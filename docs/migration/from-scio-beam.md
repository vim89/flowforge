# Migrating from Scio/Apache Beam to FlowForge

This guide helps Scio and Apache Beam users migrate to FlowForge's contract-first, engine-agnostic data pipeline framework. FlowForge provides compile-time contract validation, multi-engine support, and functional programming patterns that complement Scio's type safety with additional guarantees.

## Table of Contents

1. [Architecture Comparison](#architecture-comparison)
2. [Pipeline Translation](#pipeline-translation)
3. [Type Safety Migration](#type-safety-migration)
4. [Streaming Patterns](#streaming-patterns)
5. [Testing Migration](#testing-migration)
6. [Performance Considerations](#performance-considerations)
7. [Gradual Migration Strategy](#gradual-migration-strategy)

## Architecture Comparison

### Execution Models

| Aspect | Scio/Beam | FlowForge |
|--------|-----------|-----------|
| **Execution** | Beam runners (Dataflow, Flink, Spark) | Engine-agnostic (Spark, Flink, future engines) |
| **Type Safety** | Scala types + Beam SDK | Compile-time contracts + Scala types |
| **Schema Evolution** | Runtime schema compatibility | Compile-time schema policies |
| **Effect Management** | Scala Futures, manual resource handling | F[_] abstraction (Cats Effect, ZIO) |
| **Data Quality** | External validation (custom DoFns) | Built-in dual-mode validation |
| **Lineage** | Manual instrumentation | Automatic OpenLineage emission |

### Key Philosophical Differences

**Scio/Beam Philosophy**: Runtime flexibility with type safety at the Scala level
```scala
// Scio: Type-safe but schema drift detected at runtime
val pipeline = sc.parallelize(users)
  .map(user => ProcessedUser(user.id, user.name.toUpperCase))
  .saveAsTextFile("gs://bucket/output")
```

**FlowForge Philosophy**: Contract-first with compile-time validation
```scala
// FlowForge: Schema drift prevents compilation
import com.flowforge.core.contracts.SchemaConforms

def processUsers[F[_]: Sync](
  users: Dataset[User]
)(implicit ev: SchemaConforms[User, ProcessedUser]): F[Dataset[ProcessedUser]] = {
  // Compilation fails if User schema doesn't conform to ProcessedUser contract
  users.map(user => ProcessedUser(user.id, user.name.toUpperCase)).pure[F]
}
```

## Pipeline Translation

### Basic Transformations

#### Scio ParDo → FlowForge Map/FlatMap

**Scio Pattern:**
```scala
import com.spotify.scio._
import com.spotify.scio.values.SCollection

// Scio: ParDo via map/flatMap
val pipeline = sc.parallelize(events)
  .map(event => event.copy(processed = true))
  .filter(_.isValid)
  .flatMap(event => event.tags.map(tag => (tag, 1)))
```

**FlowForge Equivalent:**
```scala
import com.flowforge.core.Pipeline
import com.flowforge.core.contracts.SchemaConforms
import cats.effect.Sync
import cats.implicits._

def processEvents[F[_]: Sync](
  events: Dataset[Event]
)(implicit 
  ev1: SchemaConforms[Event, ProcessedEvent],
  ev2: SchemaConforms[ProcessedEvent, TagCount]
): F[Dataset[TagCount]] = {
  for {
    processed <- events
      .map(event => event.copy(processed = true))
      .filter(_.isValid)
      .pure[F]
    tagCounts <- processed
      .flatMap(event => event.tags.map(tag => TagCount(tag, 1)))
      .pure[F]
  } yield tagCounts
}
```

#### Scio GroupByKey → FlowForge GroupBy + Aggregation

**Scio Pattern:**
```scala
// Scio: GroupByKey with windowing
val wordCounts = sc.pubsubSubscription[String](subscription)
  .withFixedWindows(Duration.standardMinutes(5))
  .flatMap(_.split("\\s+"))
  .map(word => (word, 1))
  .aggregateByKey(0)(_ + _)
```

**FlowForge Equivalent:**
```scala
import com.flowforge.streaming.WindowConfig
import com.flowforge.streaming.WindowConfig.FixedWindow
import scala.concurrent.duration._

def wordCount[F[_]: Sync](
  messages: StreamingDataset[String]
)(implicit ev: SchemaConforms[String, WordCount]): F[StreamingDataset[WordCount]] = {
  val windowConfig = FixedWindow(5.minutes)
  
  for {
    words <- messages
      .withWindow(windowConfig)
      .flatMap(_.split("\\s+"))
      .pure[F]
    counts <- words
      .map(word => WordCount(word, 1))
      .groupBy(_.word)
      .agg(sum(col("count")))
      .pure[F]
  } yield counts
}
```

### Advanced Patterns

#### Scio Side Inputs → FlowForge Broadcast Variables

**Scio Pattern:**
```scala
// Scio: Side inputs for enrichment
val lookupData = sc.parallelize(lookups).asMapSideInput
val enriched = sc.parallelize(events)
  .withSideInputs(lookupData)
  .map { (event, ctx) =>
    val lookup = ctx(lookupData)
    event.copy(category = lookup.get(event.categoryId))
  }
  .toSCollection
```

**FlowForge Equivalent:**
```scala
def enrichEvents[F[_]: Sync](
  events: Dataset[Event],
  lookups: Dataset[Lookup]
)(implicit ev: SchemaConforms[Event, EnrichedEvent]): F[Dataset[EnrichedEvent]] = {
  for {
    lookupMap <- lookups
      .collect()
      .map(_.map(l => l.id -> l.category).toMap)
    enriched <- events
      .map(event => EnrichedEvent(
        event.id,
        event.categoryId,
        lookupMap.getOrElse(event.categoryId, "unknown")
      ))
      .pure[F]
  } yield enriched
}
```

#### Scio CoGroupByKey → FlowForge Joins

**Scio Pattern:**
```scala
// Scio: CoGroupByKey for joining
val joined = (users.keyBy(_.id), orders.keyBy(_.userId))
  .cogroup
  .map { case (userId, (userIter, orderIter)) =>
    val user = userIter.headOption
    val orders = orderIter.toList
    UserWithOrders(user, orders)
  }
```

**FlowForge Equivalent:**
```scala
def joinUsersOrders[F[_]: Sync](
  users: Dataset[User],
  orders: Dataset[Order]
)(implicit ev: SchemaConforms[(User, Order), UserWithOrders]): F[Dataset[UserWithOrders]] = {
  for {
    joined <- users
      .join(orders, users("id") === orders("userId"), "left")
      .groupBy(users("id"))
      .agg(
        first(struct(users.columns.map(col): _*)).as("user"),
        collect_list(struct(orders.columns.map(col): _*)).as("orders")
      )
      .map(row => UserWithOrders(
        row.getAs[User]("user"),
        row.getAs[Seq[Order]]("orders").toList
      ))
      .pure[F]
  } yield joined
}
```

## Type Safety Migration

### Scio Type Safety vs FlowForge Contracts

**Scio Approach**: Type safety through Scala's type system
```scala
// Scio: Compile-time type safety, runtime schema issues
case class User(id: Long, name: String, email: String)
case class ProcessedUser(id: Long, name: String, email: String, processed: Boolean)

val pipeline = sc.parallelize(users: Seq[User])
  .map(user => ProcessedUser(user.id, user.name, user.email, true))
  // ✅ Compiles fine
  // ❌ Runtime failure if User schema changes (e.g., email becomes optional)
```

**FlowForge Approach**: Contract validation + type safety
```scala
// FlowForge: Compile-time contract validation + type safety
import com.flowforge.contracts._
import com.flowforge.core.contracts.SchemaConforms

case class User(id: Long, name: String, email: String)
case class ProcessedUser(id: Long, name: String, email: String, processed: Boolean)

// Define contracts
implicit val userContract: DataContract[User] = DataContract.builder[User]
  .withSchema(ContractSchema(
    name = NonEmptyString.unsafeFrom("User"),
    fields = List(
      FieldContract(NonEmptyString.unsafeFrom("id"), FieldType.LongType),
      FieldContract(NonEmptyString.unsafeFrom("name"), FieldType.StringType),
      FieldContract(NonEmptyString.unsafeFrom("email"), FieldType.StringType)
    ),
    version = SchemaVersion.unsafeFrom(1)
  ))
  .build

def processUsers[F[_]: Sync](
  users: Dataset[User]
)(implicit ev: SchemaConforms[User, ProcessedUser]): F[Dataset[ProcessedUser]] = {
  // ✅ Compiles only if User schema conforms to ProcessedUser contract
  // ✅ Compilation fails immediately if schema drifts
  users.map(user => ProcessedUser(user.id, user.name, user.email, true)).pure[F]
}
```

### Schema Evolution Policies

FlowForge provides explicit schema evolution policies that Scio handles implicitly:

```scala
// FlowForge: Explicit schema evolution control
import com.flowforge.core.contracts.SchemaPolicy._

// Backward compatibility: new fields optional, old fields required
implicit val backwardPolicy: SchemaPolicy = Backward

// Forward compatibility: old consumers can read new data
implicit val forwardPolicy: SchemaPolicy = Forward

// Full compatibility: both directions
implicit val fullPolicy: SchemaPolicy = Full

// Exact match: no schema changes allowed
implicit val exactPolicy: SchemaPolicy = Exact
```

## Streaming Patterns

### Windowing Translation

#### Scio Fixed Windows → FlowForge Fixed Windows

**Scio Pattern:**
```scala
import org.apache.beam.sdk.transforms.windowing._
import org.joda.time.Duration

// Scio: Fixed windows with triggers
val windowedData = sc.pubsubSubscription[String](subscription)
  .withFixedWindows(
    Duration.standardMinutes(5),
    options = WindowOptions(
      allowedLateness = Duration.standardMinutes(10),
      trigger = Repeatedly.forever(
        AfterWatermark.pastEndOfWindow()
          .withLateFirings(AfterPane.elementCountAtLeast(1))
      ),
      accumulationMode = ACCUMULATING_FIRED_PANES
    )
  )
  .map(parseEvent)
  .aggregateByKey(EventStats.empty)(_ + _)
```

**FlowForge Equivalent:**
```scala
import com.flowforge.streaming._
import scala.concurrent.duration._

def processStreamingEvents[F[_]: Sync](
  events: StreamingDataset[String]
)(implicit ev: SchemaConforms[String, EventStats]): F[StreamingDataset[EventStats]] = {
  val windowConfig = WindowConfig.FixedWindow(
    duration = 5.minutes,
    allowedLateness = Some(10.minutes),
    trigger = TriggerConfig.AfterWatermarkWithLate(
      earlyTrigger = Some(TriggerConfig.ProcessingTime(1.minute)),
      lateTrigger = TriggerConfig.ElementCount(1)
    ),
    accumulationMode = AccumulationMode.Accumulating
  )
  
  for {
    parsed <- events
      .withWindow(windowConfig)
      .map(parseEvent)
      .pure[F]
    aggregated <- parsed
      .groupBy(_.eventType)
      .agg(
        count("*").as("count"),
        sum("value").as("totalValue"),
        max("timestamp").as("maxTimestamp")
      )
      .map(row => EventStats(
        eventType = row.getString("eventType"),
        count = row.getLong("count"),
        totalValue = row.getDouble("totalValue"),
        maxTimestamp = row.getTimestamp("maxTimestamp")
      ))
      .pure[F]
  } yield aggregated
}
```

#### Scio Session Windows → FlowForge Session Windows

**Scio Pattern:**
```scala
// Scio: Session windows for user activity
val userSessions = events
  .keyBy(_.userId)
  .withSessionWindows(Duration.standardMinutes(30))
  .aggregateByKey(UserSession.empty)(_ + _)
```

**FlowForge Equivalent:**
```scala
def trackUserSessions[F[_]: Sync](
  events: StreamingDataset[UserEvent]
)(implicit ev: SchemaConforms[UserEvent, UserSession]): F[StreamingDataset[UserSession]] = {
  val sessionConfig = WindowConfig.SessionWindow(
    gapDuration = 30.minutes,
    allowedLateness = Some(5.minutes)
  )
  
  for {
    sessions <- events
      .withWindow(sessionConfig)
      .groupBy(col("userId"))
      .agg(
        min("timestamp").as("sessionStart"),
        max("timestamp").as("sessionEnd"),
        count("*").as("eventCount"),
        collect_list("eventType").as("eventTypes")
      )
      .map(row => UserSession(
        userId = row.getString("userId"),
        sessionStart = row.getTimestamp("sessionStart"),
        sessionEnd = row.getTimestamp("sessionEnd"),
        eventCount = row.getLong("eventCount"),
        eventTypes = row.getSeq[String]("eventTypes").toList
      ))
      .pure[F]
  } yield sessions
}
```

### State and Timers

**Scio Pattern:**
```scala
// Scio: Stateful DoFn with timers
class DeduplicationDoFn extends DoFn[Event, Event] {
  @StateId("seen") val seenState = StateSpecs.set[String]()
  @TimerId("cleanup") val cleanupTimer = TimerSpecs.timer(TimeDomain.EVENT_TIME)
  
  @ProcessElement
  def processElement(
    @Element event: Event,
    @StateId("seen") seen: SetState[String],
    @TimerId("cleanup") cleanup: Timer,
    out: OutputReceiver[Event]
  ): Unit = {
    if (!seen.contains(event.id).read()) {
      seen.add(event.id)
      cleanup.set(event.timestamp.plus(Duration.standardHours(24)))
      out.output(event)
    }
  }
  
  @OnTimer("cleanup")
  def onCleanup(@StateId("seen") seen: SetState[String]): Unit = {
    seen.clear()
  }
}
```

**FlowForge Equivalent:**
```scala
import com.flowforge.streaming.state._

def deduplicateEvents[F[_]: Sync](
  events: StreamingDataset[Event]
)(implicit ev: SchemaConforms[Event, Event]): F[StreamingDataset[Event]] = {
  val stateConfig = StateConfig.KeyedState[String, Set[String]](
    stateName = "seen_events",
    ttl = Some(24.hours),
    keyExtractor = _.id
  )
  
  events
    .withState(stateConfig)
    .mapWithState { (event, state) =>
      val seen = state.getOrElse(Set.empty)
      if (seen.contains(event.id)) {
        (None, state) // Duplicate, filter out
      } else {
        val newSeen = seen + event.id
        (Some(event), Some(newSeen))
      }
    }
    .filter(_.isDefined)
    .map(_.get)
    .pure[F]
}
```

## Testing Migration

### Scio Test Patterns → FlowForge Test Patterns

#### Scio JobTest → FlowForge Pipeline Testing

**Scio Pattern:**
```scala
import com.spotify.scio.testing._

class WordCountTest extends PipelineSpec {
  "WordCount" should "count words correctly" in {
    JobTest[WordCount.type]
      .args("--input=in.txt", "--output=out.txt")
      .input(TextIO("in.txt"), Seq("hello world", "hello"))
      .output(TextIO("out.txt")) { output =>
        output should containInAnyOrder(Seq("hello: 2", "world: 1"))
      }
      .run()
  }
}
```

**FlowForge Equivalent:**
```scala
import com.flowforge.testing._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WordCountTest extends AsyncWordSpec with AsyncIOSpec with Matchers {
  "WordCount pipeline" should {
    "count words correctly" in {
      val input = List("hello world", "hello")
      val expected = List(WordCount("hello", 2), WordCount("world", 1))
      
      PipelineTest[IO]
        .withInput(input)
        .run(wordCountPipeline[IO])
        .map { result =>
          result should contain theSameElementsAs expected
        }
    }
    
    "handle contract violations" in {
      val invalidInput = List("") // Empty string violates contract
      
      PipelineTest[IO]
        .withInput(invalidInput)
        .expectContractViolation[WordCount]
        .run(wordCountPipeline[IO])
        .map { violations =>
          violations should not be empty
          violations.head.fieldName shouldBe "word"
        }
    }
  }
}
```

#### Scio Property-Based Testing → FlowForge Property Testing

**Scio Pattern:**
```scala
import org.scalacheck._
import com.spotify.scio.testing._

class PropertyTest extends Properties("WordCount") {
  property("word count is always positive") = Prop.forAll { (words: List[String]) =>
    val pipeline = TestPipeline.create()
    val input = pipeline.apply(Create.of(words.asJava))
    val output = WordCount.countWords(input)
    
    // Test that all counts are positive
    PAssert.that(output).satisfies { results =>
      results.asScala.forall(_.getValue > 0)
    }
    
    pipeline.run().waitUntilFinish()
    true
  }
}
```

**FlowForge Equivalent:**
```scala
import org.scalacheck._
import org.scalacheck.Prop._
import com.flowforge.testing.generators._

class WordCountPropertyTest extends Properties("WordCount") {
  implicit val arbWordCount: Arbitrary[WordCount] = Arbitrary(
    for {
      word <- Gen.alphaStr.suchThat(_.nonEmpty)
      count <- Gen.posNum[Int]
    } yield WordCount(word, count)
  )
  
  property("word count preserves total word count") = forAll { (words: List[String]) =>
    val totalWords = words.length
    
    PipelineTest[IO]
      .withInput(words)
      .run(wordCountPipeline[IO])
      .map { result =>
        result.map(_.count).sum == totalWords
      }
      .unsafeRunSync()
  }
  
  property("contract validation catches invalid data") = forAll { (invalidWords: List[String]) =>
    val hasInvalid = invalidWords.exists(_.isEmpty)
    
    if (hasInvalid) {
      PipelineTest[IO]
        .withInput(invalidWords)
        .expectContractViolation[WordCount]
        .run(wordCountPipeline[IO])
        .map(_.nonEmpty)
        .unsafeRunSync()
    } else {
      true // Valid data should not produce violations
    }
  }
}
```

## Performance Considerations

### Beam Optimizations → FlowForge Optimizations

#### Scio Fusion and Combining

**Scio Pattern:**
```scala
// Scio: Relies on Beam's fusion optimization
val result = sc.parallelize(data)
  .map(transform1)
  .map(transform2)  // Fused with transform1
  .map(transform3)  // Fused with previous maps
  .aggregateByKey(0)(_ + _)  // Combining optimization
```

**FlowForge Equivalent:**
```scala
// FlowForge: Explicit optimization hints + engine delegation
def optimizedPipeline[F[_]: Sync](
  data: Dataset[Input]
)(implicit ev: SchemaConforms[Input, Output]): F[Dataset[Output]] = {
  data
    .transform(_.map(transform1).map(transform2).map(transform3))  // Explicit fusion
    .optimizeFor(OptimizationHint.Fusion)  // Hint to engine
    .groupBy(_.key)
    .agg(sum(col("value")))  // Engine-specific combining
    .optimizeFor(OptimizationHint.Combining)
    .pure[F]
}
```

#### Memory Management

**Scio Considerations:**
- Beam handles memory management automatically
- Side inputs cached in memory
- State stored in Beam's state backend

**FlowForge Approach:**
```scala
import com.flowforge.optimization._

def memoryOptimizedPipeline[F[_]: Sync](
  largeDataset: Dataset[LargeRecord]
): F[Dataset[Summary]] = {
  largeDataset
    .repartition(OptimalPartitions.forMemory(largeDataset.estimatedSize))
    .mapPartitions { partition =>
      // Process in chunks to control memory usage
      partition.grouped(1000).flatMap(processChunk)
    }
    .cache(CacheLevel.MemoryAndDisk)  // Explicit cache control
    .pure[F]
}
```

### Engine-Specific Optimizations

FlowForge allows leveraging engine-specific optimizations:

```scala
// Spark-specific optimizations
def sparkOptimized[F[_]: Sync](data: Dataset[Event]): F[Dataset[Result]] = {
  data
    .hint("broadcast")  // Spark broadcast join hint
    .coalesce(200)      // Spark-specific partitioning
    .pure[F]
}

// Flink-specific optimizations  
def flinkOptimized[F[_]: Sync](stream: StreamingDataset[Event]): F[StreamingDataset[Result]] = {
  stream
    .keyBy(_.userId)
    .withParallelism(16)  // Flink-specific parallelism
    .withBufferTimeout(100.millis)  // Flink buffering
    .pure[F]
}
```

## Gradual Migration Strategy

### Phase 1: Proof of Concept (1-2 weeks)

1. **Choose a Simple Pipeline**: Start with a basic batch ETL job
2. **Set Up FlowForge**: Generate project using g8 template
3. **Define Contracts**: Create contracts for existing data models
4. **Implement Core Logic**: Migrate business logic (keep Scio running in parallel)

```scala
// Phase 1: Simple batch migration
object Phase1Migration {
  // Existing Scio pipeline
  def scioWordCount(sc: ScioContext): Unit = {
    sc.textFile("input.txt")
      .flatMap(_.split("\\s+"))
      .map(word => (word, 1))
      .aggregateByKey(0)(_ + _)
      .saveAsTextFile("output")
  }
  
  // New FlowForge equivalent
  def flowForgeWordCount[F[_]: Sync](
    input: Dataset[String]
  )(implicit ev: SchemaConforms[String, WordCount]): F[Dataset[WordCount]] = {
    input
      .flatMap(_.split("\\s+"))
      .map(word => WordCount(word, 1))
      .groupBy(_.word)
      .agg(sum(col("count")))
      .pure[F]
  }
}
```

### Phase 2: Contract Integration (2-3 weeks)

1. **Add Contract Validation**: Implement comprehensive contracts
2. **Quality Rules**: Add data quality validation
3. **Testing**: Migrate test suites to FlowForge patterns
4. **CI Integration**: Add contract validation to build pipeline

```scala
// Phase 2: Add contracts and quality
implicit val wordCountContract: DataContract[WordCount] = 
  DataContract.builder[WordCount]
    .withSchema(ContractSchema(
      name = NonEmptyString.unsafeFrom("WordCount"),
      fields = List(
        FieldContract(
          name = NonEmptyString.unsafeFrom("word"),
          dataType = FieldType.StringType,
          constraints = List(FieldConstraint.MinLength(1))
        ),
        FieldContract(
          name = NonEmptyString.unsafeFrom("count"),
          dataType = FieldType.IntType,
          constraints = List(FieldConstraint.Range(1, Int.MaxValue))
        )
      ),
      version = SchemaVersion.unsafeFrom(1)
    ))
    .withRules(
      ValidationRules.nonNull("word")(_.word),
      ValidationRules.range("count")(1, Int.MaxValue)(_.count)
    )
    .build
```

### Phase 3: Streaming Migration (3-4 weeks)

1. **Streaming Patterns**: Migrate windowing and state management
2. **Engine Choice**: Decide between Spark Streaming and Flink
3. **Performance Tuning**: Optimize for production workloads
4. **Monitoring**: Set up lineage and quality monitoring

```scala
// Phase 3: Streaming migration
def migrateStreamingPipeline[F[_]: Sync](
  events: StreamingDataset[Event]
): F[StreamingDataset[Alert]] = {
  val windowConfig = WindowConfig.SlidingWindow(
    windowSize = 10.minutes,
    slideSize = 1.minute
  )
  
  events
    .withWindow(windowConfig)
    .filter(_.severity >= Severity.Warning)
    .groupBy(_.alertType)
    .agg(count("*").as("count"))
    .filter(col("count") > 100)  // Alert threshold
    .map(row => Alert(
      alertType = row.getString("alertType"),
      count = row.getLong("count"),
      timestamp = Instant.now()
    ))
    .pure[F]
}
```

### Phase 4: Production Deployment (2-3 weeks)

1. **Parallel Deployment**: Run both systems in parallel
2. **Data Validation**: Compare outputs between systems
3. **Performance Monitoring**: Ensure performance requirements met
4. **Gradual Cutover**: Migrate traffic incrementally
5. **Scio Decommission**: Remove old Scio pipelines

### Migration Checklist

- [ ] **Environment Setup**
  - [ ] FlowForge project generated from g8 template
  - [ ] Dependencies configured (Spark/Flink engines)
  - [ ] CI/CD pipeline updated for contract validation

- [ ] **Data Modeling**
  - [ ] Case classes defined for all data types
  - [ ] Contracts created with appropriate schema policies
  - [ ] Quality rules implemented for business constraints

- [ ] **Pipeline Logic**
  - [ ] Core transformations migrated from Scio
  - [ ] Windowing and aggregation patterns converted
  - [ ] State management migrated (if applicable)

- [ ] **Testing**
  - [ ] Unit tests for pure business logic
  - [ ] Integration tests for full pipelines
  - [ ] Property-based tests for contract validation
  - [ ] Performance benchmarks vs Scio baseline

- [ ] **Production Readiness**
  - [ ] Monitoring and alerting configured
  - [ ] Lineage emission to Marquez/DataHub
  - [ ] Error handling and retry logic
  - [ ] Resource management and cleanup

- [ ] **Documentation**
  - [ ] Pipeline documentation updated
  - [ ] Runbooks for operations team
  - [ ] Contract evolution procedures
  - [ ] Troubleshooting guides

## Common Migration Patterns

### Pattern 1: Scio SCollection → FlowForge Dataset

```scala
// Before (Scio)
val processed: SCollection[Result] = input
  .map(transform)
  .filter(predicate)

// After (FlowForge)
def process[F[_]: Sync](
  input: Dataset[Input]
)(implicit ev: SchemaConforms[Input, Result]): F[Dataset[Result]] = {
  input
    .map(transform)
    .filter(predicate)
    .pure[F]
}
```

### Pattern 2: Scio Side Inputs → FlowForge Broadcast

```scala
// Before (Scio)
val lookup = sc.parallelize(lookupData).asMapSideInput
val enriched = input.withSideInputs(lookup).map { (item, ctx) =>
  val lookupMap = ctx(lookup)
  enrich(item, lookupMap)
}

// After (FlowForge)
def enrich[F[_]: Sync](
  input: Dataset[Item],
  lookupData: Dataset[Lookup]
): F[Dataset[EnrichedItem]] = {
  for {
    lookupMap <- lookupData.collect().map(_.map(l => l.key -> l.value).toMap)
    enriched <- input.map(item => enrich(item, lookupMap)).pure[F]
  } yield enriched
}
```

### Pattern 3: Scio Windowing → FlowForge Streaming Windows

```scala
// Before (Scio)
val windowed = stream
  .withFixedWindows(Duration.standardMinutes(5))
  .aggregateByKey(Aggregator.sum)

// After (FlowForge)
def windowedAggregation[F[_]: Sync](
  stream: StreamingDataset[Event]
): F[StreamingDataset[Aggregated]] = {
  stream
    .withWindow(WindowConfig.FixedWindow(5.minutes))
    .groupBy(_.key)
    .agg(sum(col("value")))
    .pure[F]
}
```

## Troubleshooting Common Issues

### Issue 1: Contract Compilation Errors

**Problem**: `SchemaConforms` evidence not found
```scala
// Error: could not find implicit value for parameter ev: SchemaConforms[User, ProcessedUser]
def process(users: Dataset[User]): Dataset[ProcessedUser] = ???
```

**Solution**: Define contracts and ensure schema compatibility
```scala
// Define contracts for both types
implicit val userContract: DataContract[User] = ???
implicit val processedUserContract: DataContract[ProcessedUser] = ???

// Ensure schema policy allows the transformation
implicit val schemaPolicy: SchemaPolicy = SchemaPolicy.Backward
```

### Issue 2: Performance Regression

**Problem**: FlowForge pipeline slower than Scio equivalent

**Investigation Steps**:
1. Check partitioning strategy
2. Verify caching decisions
3. Compare execution plans
4. Profile memory usage

**Solutions**:
```scala
// Add explicit optimizations
def optimizedPipeline[F[_]: Sync](data: Dataset[Input]): F[Dataset[Output]] = {
  data
    .repartition(OptimalPartitions.forSize(data.estimatedSize))
    .cache(CacheLevel.MemoryAndDisk)
    .optimizeFor(OptimizationHint.Fusion)
    .transform(businessLogic)
    .pure[F]
}
```

### Issue 3: Streaming Watermark Issues

**Problem**: Late data not handled correctly

**Solution**: Configure appropriate watermark and lateness policies
```scala
val windowConfig = WindowConfig.FixedWindow(
  duration = 5.minutes,
  allowedLateness = Some(10.minutes),
  trigger = TriggerConfig.AfterWatermarkWithLate(
    lateTrigger = TriggerConfig.ElementCount(1)
  )
)
```

## Next Steps

After completing the migration:

1. **Explore Advanced Features**:
   - Multi-engine deployment (same logic on Spark and Flink)
   - Advanced schema evolution patterns
   - Custom quality rules and constraints

2. **Optimize for Production**:
   - Performance tuning for specific workloads
   - Advanced monitoring and alerting
   - Cost optimization strategies

3. **Team Training**:
   - FlowForge best practices workshops
   - Contract design patterns
   - Troubleshooting and debugging techniques

4. **Community Engagement**:
   - Contribute migration experiences back to FlowForge
   - Share patterns and solutions with the community
   - Participate in FlowForge development and roadmap discussions

For additional support during migration, refer to:
- [FlowForge Documentation](../README.md)
- [Interactive Tutorials](../tutorials/README.md)
- [Community Discord/Slack](#) 
- [GitHub Issues](https://github.com/flowforge/flowforge/issues)