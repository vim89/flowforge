# Contributors handbook (flowforge)

## 1. Philosophy, pitch, mission
- Contracts‑first data pipelines with compile‑time confidence and effect‑safe orchestration.
- Replace runtime roulette with typed interfaces, gates, and observability.

## 2. Architecture overview
- Layered: Core → Domain → Framework → Service → Infrastructure → Apps (ADR‑013, ADR‑004).
- Guardrails: Pure Spark transforms; external IO in `F[_]` (ADR‑002/012). Contracts typed gates + build checks (ADR‑011).
 - Key Architectural Principles: Dependency Inversion; Effect polymorphism; Type safety; Composability; Resource safety; Plugin architecture. See ADR‑001/002/012/013.

## 2.1 SOLID principles implementation
- SRP/OCP/LSP/ISP/DIP applied via small algebras, interpreters, and type classes. Higher layers depend on abstractions only.

## 3. Coding patterns (Tagless final)
- Algebras: `DataPipelineAlg[F]`, `SparkDataAlgebra` (pure ops) + effectful boundaries.
- Composition: Kleisli arrows for `A => F[B]`; transformations remain pure.
- Errors: `EitherT[F, E, A]`/ZIO error channels; aggregate validation with `ValidatedNel`.

### 3.1 Kleisli composition (example)
```scala
import cats.data.Kleisli

type F[A] = cats.effect.IO[A]

val read:    Kleisli[F, Unit, Dataset[In ]] = Kleisli(_ => P.read[In ](src))
val validate: Kleisli[F, Dataset[In], Dataset[In]] = Kleisli(ds => DQ.validate(ds))
val xform:    Kleisli[F, Dataset[In], Dataset[Out]] = Kleisli(ds => P.transform(ds)(f))
val write:    Kleisli[F, Dataset[Out], Unit]        = Kleisli(ds => P.write(ds, sink).void)

val pipeline: Kleisli[F, Unit, Unit] = read andThen validate andThen xform andThen write
```

### 3.2 Spark helpers (engine-specific)
- Use SparkDatasetOps to keep transforms Spark-native while preserving the pure algebra elsewhere.
```scala
import com.flowforge.engines.spark.SparkDatasetOps
import org.apache.spark.sql.functions.col

def enrichAndFilter[F[_]: EffectSystem, A: DataDecoder](
  ds: DataAlgebra.Dataset[A],
): DataAlgebra.Dataset[A] = {
  // Spark-native filter by column (no change for non-Spark datasets)
  val filtered = SparkDatasetOps.filterByColumn(ds)(col("amount") > 0)
  // Order by timestamp descending
  val ordered  = SparkDatasetOps.sortByColumn(filtered)(col("timestamp"), ascending = false)
  // Keep top 100
  SparkDatasetOps.limitRows(ordered)(100)
}
```
Notes: `filterByColumn/sortByColumn/limitRows/dropRows` operate only when the dataset is backed by Spark; otherwise they return the input.

## 4. ETL & pipeline patterns
- Canon: read → validate → profile → transform → CDC → audit → write.
- CDC: partition‑aware joins, late events, SCD1/2 patterns; transactional/outbox sinks.
- Profiling: `DataProfile(count, schema, stats)` pre/post; lineage recorded at each stage.

### 4.1 Effect boundaries (Pure vs IO)
```scala
// Pure Spark transformation (no F[_])
def filterHighValue(ds: Dataset[Txn]): Dataset[Txn] = ds.filter(_.amount > 0)

// IO boundary (F[_]) for reads/writes/audit
def read[F[_]: EffectSystem](src: DataSource): F[Dataset[Txn]] = engine.read(src)
def write[F[_]: EffectSystem](ds: Dataset[Txn], sink: DataSink): F[WriteResult] = engine.write(ds, sink)
```

## 5. Production pipeline concerns (35+)
- Correctness/Safety: contracts as types, compatibility rules, idempotency, DLQ, delivery semantics.
- Scalability/Performance: affected partitions, shuffle minimization, encoding, ordering.
- Reliability/Resilience: audit/lineage, freshness SLAs, fault isolation, retries/timeouts.
- Governance/Maintainability: versioned transforms, reprocessability, observability hooks, catalog.

## 6. Technical implementation strategy
- Effect System: `EffectSystem[F]` with Cats‑Effect/ZIO instances; resource safety via `Resource[F,_]`.
- Schema Evolution: SchemaEq witnesses, registry compatibility, safe decode, migrations.
- Multi‑Engine: Spark/Flink/local interpreters with shared business logic.

## 6.1 Multi‑engine strategy
- Engines own runtime specifics; business logic stays in algebras. Provide engine‑specific optimizations behind the same interfaces.

### 6.2 Resource safety (example)
```scala
import cats.effect.{IO, Resource}

def jdbcResource(cfg: JdbcCfg): Resource[IO, Connection] =
  Resource.make(IO(blocking(open(cfg))))(c => IO(blocking(c.close())).handleError(_ => ()))

def fetch[F[_]: EffectSystem, A](q: Query[A]): F[List[A]] =
  EffectSystem[F].fromResource(jdbcResource(q.cfg)).use { conn =>
    EffectSystem[F].delay(blocking(runQuery(conn, q)))
  }
```

## 7. Advanced type‑level patterns
- Refined types; LabelledGeneric‑backed `SchemaEq`; phantom‑typed builders; higher‑kinded/ type class designs.

## 8. Resource management patterns
- Bracket/`Resource[F,_]` for clients (JDBC/HTTP/FS); retries/timeouts (cats‑retry/ZIO Schedule); circuit breakers.

## 9. Error modeling strategy
- Domain `PipelineError` ADT; clear separation of system vs business errors; DLQ + record‑level retries.

### 9.1 Aggregating validation (ValidatedNel)
```scala
import cats.data.{ValidatedNel, NonEmptyList}

sealed trait DQError; case object MissingId extends DQError; case object BadAmount extends DQError

def ruleId(r: Txn): ValidatedNel[DQError, Txn] = Option(r.id).toRight(MissingId).toValidatedNel.map(_ => r)
def ruleAmt(r: Txn): ValidatedNel[DQError, Txn] = Either.cond(r.amount >= 0, r, BadAmount).toValidatedNel

def validate(r: Txn): ValidatedNel[DQError, Txn] = (ruleId(r), ruleAmt(r)).mapN((_, _) => r)
```

## 10. Template generation philosophy
- Giter8 scaffolds typed pipelines; user chooses effect system; defaults to typed APIs; CI wiring for gates.

## 10.1 Archetype & Compile‑time contracts
- Contracts as code; typed endpoints and witnesses generated or referenced by SDKs.
- CI‑first: Non‑technical stakeholders submit contracts via GitHub Actions Forms; CI materializes typed artifacts and runs physical schema validation (validation‑cli). sbt plugin remains optional for local smoke checks and should delegate to the same CLI.

## 10.2 Type‑safe archetypes (Scala Ecosystem)
- Use Refined for config; Cats ValidatedNel for DQ; type classes for pluggable ingestion/validation; effect‑safe orchestration (Cats‑Effect/ZIO).

### 10.3 Typed contracts & builder (example)
```scala
import com.flowforge.contracts._

val src  = TypedSource[SalesV1Repr](DataSource.gcs("gs://raw/sales/*.parquet"))
val sink = TypedSink[SalesCuratedV1Repr](DataSink.parquet("/tmp/curated/sales"))

val b2 = PipelineBuilder2
  .addTypedSource[SalesV1, SalesV1Repr](src)
  .transform(mapToCurated)
  .addTypedSink[SalesCuratedV1, SalesCuratedV1Repr](sink)
```

## 11. Prototype integration & incremental adoption
- Start typed→gate one path; wrap legacy untyped with adapters; add gates (compile/build) gradually.
- Prototypes: see `modules/examples-spark/` for end-to-end pipeline examples.

### 11.1 Prototype index (Repo paths)
- Simple pipeline: `modules/core/src/main/scala/com/flowforge/core/examples/SimpleWorkingPipeline.scala`
- Effect system demo: `modules/core/src/main/scala/com/flowforge/core/examples/EffectSystemTest.scala`
- Spark streaming CDC: `modules/engines-spark/src/main/scala/com/flowforge/engines/spark/StreamingCDC.scala`
- CDC test: `modules/engines-spark/src/test/scala/com/flowforge/engines/spark/StreamingCDCSpec.scala`
- FileSystem example: `modules/connectors/src/main/scala/com/flowforge/connectors/filesystem/examples/FileSystemExample.scala`
- Schema validation CLI: `modules/validation-cli/src/main/scala/com/flowforge/validation/SchemaValidateCli.scala`
- Contracts extractor CLI: `modules/contracts-extractor-cli/src/main/scala/com/flowforge/contracts/extractor/ContractsExtractorCli.scala`
- g8 template sample: `templates/data-pipeline.g8/src/main/g8/src/main/scala/example/Pipeline.scala`

## 12. Refactoring strategy
- Make pure: remove `F[_]` from Spark transforms; extract IO boundaries; add tests, then optimize.

## 13. Security, config, and observability
- No secrets in code; secret managers/KMS; structured logging; Prometheus metrics; optional OTEL tracing.

## 14. Testing & QA
- Laws + property tests first; opt‑in engine IT; scoverage ≥ 80%; heavy jobs on nightly.

## 15. Anti‑patterns to reject
- Ad‑hoc scripts, try/catch as policy, untyped contracts, inline credentials, uncontrolled parallelism, blanket repartition.

## 16. 30‑Point checklist (pointer)
- Use ADR‑020 as rubric in reviews; keep transforms pure, IO effectful, enforce idempotency and partition awareness.

## 17. Session workflow
- Initialize context -> set goals -> implement incrementally with safe checks -> update ADRs/docs -> open focused PR.

## 18. Functional programming foundation
- Immutability, higher‑order functions, ADTs, type classes, monads/applicatives, referential transparency. Prefer `ValidatedNel` for accumulating validations.

## 19. Low‑level design & design patterns
- Creational/Structural/Behavioral patterns applied where useful, expressed as type classes, interpreters, and combinators.

Links: ADR‑002/011/012/013/014/018/019/020; ADR Index `docs/adr/INDEX.md`.
