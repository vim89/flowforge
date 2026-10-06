# Getting started with FlowForge

FlowForge is a Scala data pipeline framework with two ideas at its centre: a data contract is checked by the
compiler, and effects live at the edges of a pipeline rather than inside transforms.

Read this page as a description of what works today on `main`. v1.0.0 is not released, nothing is published to
Maven Central, and the gaps are listed in [v1.0 readiness](plan/v1.0-readiness.md). The versions CI runs are in
[the compatibility matrix](reference/compatibility.md).

## Run a pipeline in two minutes

```bash
git clone https://github.com/vim89/flowforge.git && cd flowforge
sbt "examples/runMain com.flowforge.examples.SimpleGoldenPath"
```

That example builds a typed source, one transform and a typed sink, proves the contract at compile time, and
executes the pipeline with lineage emission in noop mode. It needs no cluster and no cloud account.

The Spark path is exercised by tests rather than by a demo app:

```bash
sbt engines-spark/test
```

Delta and SCD tests in that module are opt-in integration tests, so a plain `test` run skips them.

## Using FlowForge in your own project

There are no `com.flowforge` artifacts on Maven Central yet, so publish to your local ivy cache first:

```bash
sbt publishLocal
```

sbt prints the version it published, which comes from the latest git tag through sbt-dynver. Use that version in
your own `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.flowforge" %% "flowforge-core"          % "<version printed by publishLocal>",
  "com.flowforge" %% "flowforge-contracts"     % "<version printed by publishLocal>",
  "com.flowforge" %% "flowforge-engines-spark" % "<version printed by publishLocal>",
)
```

To scaffold a new project, use the giter8 template in this repository. It is not a separate GitHub project, so
point `sbt new` at the directory:

```bash
sbt new file://$PWD/flowforge.g8
```

## Your first pipeline

### Step 1: describe the contract

A contract can be a plain case class. The compiler derives its shape, and `SchemaConforms` proves that what a
stage produces fits what the next endpoint declared.

```scala
final case class User(id: Long, email: String)
```

For a contract with field-level constraints and ownership metadata, the contracts module has a DSL:

```scala
import com.flowforge.contracts.syntax.ContractDSL._

val userContract = Contract("user")
  .field("id").required.long.positive
  .field("email").required.string.matches(Patterns.EMAIL)
  .withSLA("hourly")
  .withOwner("DataPlatformTeam")
  .build
```

### Step 2: build the pipeline

The builder carries a phantom state, so `build()` only exists once a source, a transform and a sink are all
present. An incomplete pipeline is a compile error, not a runtime failure.

```scala
import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts._
import com.flowforge.core.instances.EffectInstances._ // brings EffectSystem[IO]
import com.flowforge.core.types._

final case class User(id: Long, email: String)

val src  = TypedSource[User](LocalDataSource("/tmp/in", DataFormat.Parquet))
val sink = TypedSink[User](LocalDataSink("/tmp/out", DataFormat.Parquet))

val pipeline = PipelineBuilder[IO]("user-pipeline")
  .addTypedSource[User, User, SchemaPolicy.Exact](src, _ => IO.pure(User(1L, "a@b.com")))
  .addTransform[User](u => IO.pure(u.copy(email = u.email.toLowerCase)))
  .addTypedSink[User, SchemaPolicy.Exact](sink, (_, _) => IO.unit)
  .build()
```

The reader and writer functions are where IO happens. On a real pipeline they call a `DataAlgebra`, which is what
`modules/examples/src/main/scala/com/flowforge/examples/HelloPipeline.scala` does with the Spark algebra.

### Step 3: run it

```scala
import com.flowforge.framework.PipelineExecution

object UserPipelineApp extends cats.effect.IOApp.Simple {
  def run: IO[Unit] = PipelineExecution.execute(pipeline)(()).void
}
```

## Seeing contract drift at compile time

Start from a producer that is missing a field the contract requires:

```scala
import com.flowforge.core.contracts._

final case class Out(id: Long)
final case class Contract(id: Long, email: String)

implicitly[SchemaConforms[Out, Contract, SchemaPolicy.Exact]] // compile error: missing email
```

The error names the policy, both types, and the missing, extra and mismatched fields.

Relaxing the policy to `Backward` does not make that example compile. `Backward` lets a producer carry fields the
contract does not declare, and lets it omit a field the contract declares optional. A required field that is
absent is still an error under every policy:

```scala
final case class OutWithExtra(id: Long, email: String, tag: String)
implicitly[SchemaConforms[OutWithExtra, Contract, SchemaPolicy.Backward]] // ok: tag is extra

final case class OptionalEmail(id: Long, email: Option[String])
implicitly[SchemaConforms[Out, OptionalEmail, SchemaPolicy.Backward]] // ok: email is optional
```

The full policy lattice, including the ordered, case-insensitive and by-position variants, is in
[how it fails](how-it-fails.md). Proof that the failures are real is in `modules/compile-fail-tests`.

## Fast feedback

| Goal | Command |
|------|---------|
| Compile everything and run the quick tests | `sbt ffDev` |
| Core only, on file save | `sbt dev` |
| Formatting and lint, as CI runs them | `sbt fmtCheck` and `sbt fixCheck` |

## What exists today

- Contracts and the typestate builder: the part of the framework that is finished.
- Spark engine: `read` and `write` handle a local path and a JDBC endpoint. Other sources and sinks raise
  `UnsupportedOperationException`.
- Flink engine: pinned to Scala 2.12, does not resolve its dependencies, built by no CI job.
- Data quality: native checks by default, Deequ when it is on the classpath (`-Dff.quality.mode=deequ`).
- Lineage: OpenLineage emission, noop unless configured.
- There is no streaming API. Documents that show windowing, watermarks or a streaming CDC operator describe a
  design, not shipped code.

[v1.0 readiness](plan/v1.0-readiness.md) is the single place that states how close any of this is to a release.

## Troubleshooting

**"could not find implicit value for evidence parameter of type EffectSystem[IO]"**
Add `import com.flowforge.core.instances.EffectInstances._`.

**"Compile-time contract drift"**
The producer shape does not satisfy the contract under the policy you chose. Read the missing, extra and
mismatched field lists in the message, then either fix the shape or pick a policy that genuinely allows the
difference.

**"value build is not a member of ..."**
The phantom state says the pipeline is incomplete. A source, at least one transform and a sink are all required
before `build()` appears.

**Spark fails to start with `IllegalAccessError: class sun.nio.ch.DirectBuffer`**
Spark needs `--add-exports=java.base/sun.nio.ch=ALL-UNNAMED` on JDK 17. The build sets it for forked runs and
tests, so run Spark code through `sbt test` or a forked `run` rather than an unforked sbt session.

**Spark fails on macOS with `BindException: Can't assign requested address`**
Export `SPARK_LOCAL_IP=127.0.0.1`, or set `spark.driver.bindAddress` and `spark.driver.host` to `127.0.0.1` in the
session builder.

## Where to go next

- [How it fails](how-it-fails.md): the anatomy of a contract error.
- [Public API](public-api.md): what is supported surface and what is internal.
- [Contracts overview](contracts/OVERVIEW.md): contracts in depth.
- [Bring your own effect](effects/bring-your-own-effect.md): Cats Effect or ZIO.
- [Core design](design/core-design.md) and [framework behaviors](design/framework-behaviors.md).
- [ADR index](adr/INDEX.md): the decisions and why they were made.
- Working code: [modules/examples](../modules/examples).

Questions and bug reports go to [GitHub issues](https://github.com/vim89/flowforge/issues).
