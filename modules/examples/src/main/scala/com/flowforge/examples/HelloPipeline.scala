package com.flowforge.examples

import cats.effect.{ IO, IOApp, Resource }
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.SchemaConforms
import com.flowforge.core.types._
import com.flowforge.framework.PipelineExecution
import com.flowforge.engines.spark.{ ProductionSparkDataset, SparkDataAlgebra }
import org.apache.spark.sql.SparkSession

/**
 * Hello Pipeline — minimal end‑to‑end example
 *
 *   - Reads a small CSV of users from resources
 *   - Applies a simple typed transform
 *   - Enforces a compile‑time contract (Exact policy)
 *   - Writes a curated Parquet output locally
 *   - Runs with lineage disabled by default
 */
object HelloPipeline extends IOApp.Simple {

  // Domain model (output type)
  final case class User(
    id: Long,
    email: String,
    age: Int)

  // Compile-time policy: output type conforms to itself exactly (demo). Replace RHS with generated contracts-sdk type in real projects.
  implicit val conforms: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly

  // Effect system instance (Cats-Effect IO)
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  // Minimal JSON encoder for demo; in real projects rely on provided codecs
  implicit val userEncoder: com.flowforge.core.algebra.DataEncoder[User] = {
    import com.flowforge.core.algebra._
    DataEncoder.instance[User](
      (data, format) =>
        format match {
          case DataFormat.JSON | DataFormat.JSONL =>
            val js = s"""{"id":%d,"email":"%s","age":%d}""".format(data.id, data.email, data.age)
            Right(EncodedData(js.getBytes("UTF-8"), format))
          case other => Left(UnsupportedFormat(other, "User"))
        },
      _ =>
        DataSchema(
          fields = List(
            StructField(RefinedTypes.FieldName.unsafeFrom("id"), DataType.Long, nullable = false),
            StructField(RefinedTypes.FieldName.unsafeFrom("email"), DataType.String, nullable = false),
            StructField(RefinedTypes.FieldName.unsafeFrom("age"), DataType.Integer, nullable = false),
          ),
          version = RefinedTypes.SchemaVersion.unsafeFrom(1),
          metadata = Map.empty,
          createdAt = java.time.Instant.now(),
        ),
    )
  }

  // Spark session as a managed resource
  private def sparkResource(appName: String): Resource[IO, SparkSession] =
    Resource.make(IO {
      SparkSession
        .builder()
        .appName(appName)
        .master("local[*]")
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    })(s => IO(s.stop()).void)

  def run: IO[Unit] =
    sparkResource("FlowForge-Hello").use { spark =>
      val dao: DataAlgebra[IO] = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

      val src = DataSource.local("modules/examples/src/main/resources/fixtures/raw-users.csv", DataFormat.CSV)
      val sink = DataSink.local("target/hello/curated", DataFormat.Parquet)

      val pipeline = PipelineBuilder[BuilderState.Empty, IO, Unit, Unit](name = "hello-pipeline")
        .addTypedSource[User, User, SchemaPolicy.Exact](
          TypedSource(src),
          (_: DataSource) => IO.pure(User(1L, "demo@flowforge.dev", 42)),
        )
        .addTransform[User](u => IO.pure(u))
        .noTransform // identity to demo phantom types
        .addTypedSink[User, SchemaPolicy.Exact](
          TypedSink(sink),
          (u: User, d: DataSink) => dao.write(ProductionSparkDataset.fromData(List(u), spark), d).void,
        )
        .build()

      // Execute with a trivial input (Unit) since our source ignores it
      PipelineExecution.execute(pipeline)(()).void
    }
}

// End of demo
