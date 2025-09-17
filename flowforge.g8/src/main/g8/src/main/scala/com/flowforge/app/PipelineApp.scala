package com.flowforge.app

import cats.effect.{ IO, IOApp, Resource }
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.algebra.{ DataEncoder => FFEncoder }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._
import com.flowforge.core.algebra.{ DataDecoder => FFDecoder, EncodedData => FFEncodedData, CorruptedData => FFCorruptedData }
import io.circe.parser.parse
import com.flowforge.engines.spark.SparkDataAlgebra
import com.flowforge.framework.PipelineExecution
import com.flowforge.quality.deequ.DeequAdapter
import org.apache.spark.sql.SparkSession

/**
 * Real runnable Spark pipeline using FlowForge.
 * - Reads CSV users
 * - Validates data quality (native or Deequ if present)
 * - Writes Parquet
 * - Logs audit events to JDBC (H2)
 */
object PipelineApp extends IOApp.Simple {

  final case class User(id: Long, email: String, age: Int)

  implicit val userShape: Shape[User] = Shape.gen[User]
  implicit val conformsExact: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  // Minimal JSON decoder for User so dao.read[User] works with SparkDataAlgebra (which emits JSON for sampling)
  implicit val userDecoder: FFDecoder[User] = new FFDecoder[User] {
    def decode(ed: FFEncodedData, format: DataFormat) = format match {
      case DataFormat.JSON | DataFormat.JSONL =>
        parse(new String(ed.data, "UTF-8")).left.map(e => FFCorruptedData(e.getMessage)).flatMap {
          json =>
            val c = json.hcursor
            for {
              id    <- c.get[Long]("id").left.map(e => FFCorruptedData(e.getMessage))
              email <- c.get[String]("email").left.map(e => FFCorruptedData(e.getMessage))
              age   <- c.get[Int]("age").left.map(e => FFCorruptedData(e.getMessage))
            } yield User(id, email, age)
        }
      case other => Left(FFCorruptedData("Unsupported format: " + other.toString))
    }
    def validateSchema(ed: FFEncodedData, expected: DataSchema) = Right(())
    def decodeWithEvolution(ed: FFEncodedData, format: DataFormat, target: DataSchema) = decode(ed, format)
    def supportsFormat(format: DataFormat) = format == DataFormat.JSON || format == DataFormat.JSONL
  }

  // Minimal encoder to satisfy typeclass bounds during write; Spark path will bypass it
  implicit val userEncoder: FFEncoder[User] = com.flowforge.core.algebra.DataEncoder.instance[User](
    (u, format) => Right(FFEncodedData("{}".getBytes("UTF-8"), format)),
    _ => DataSchema.builder.addField("id", DataType.Long).addField("email", DataType.String).addField("age", DataType.Integer).build,
  )

  private def sparkR: Resource[IO, SparkSession] =
    Resource.make(IO {
      SparkSession
        .builder()
        .appName("FlowForgeTemplatePipeline")
        .master("local[*]")
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    })(s => IO(s.stop()).void)

  private def daoR(spark: SparkSession): Resource[IO, DataAlgebra[IO]] =
    Resource.pure(SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra)

  def run: IO[Unit] = sparkR.use { spark =>
    daoR(spark).use { dao =>
      val log = org.slf4j.LoggerFactory.getLogger("pipeline")
      val F   = EffectSystem[IO]

      val src  = DataSource.local("data/users.csv", DataFormat.CSV)
      val sink = DataSink.local("target/out/users", DataFormat.Parquet)

      val typedSrc  = TypedSource[User](src)
      val typedSink = TypedSink[User](sink)

      for {
        _ <- AuditDb.init()
        _ <- AuditDb.log("pipeline_started")
        ds <- dao.read[User](src)
        // Contract-aware pipeline (compile-time): source → transform → sink
        builder: com.flowforge.framework.Pipeline[IO, Unit, User] = PipelineBuilder[IO]("users-pipeline")
          .addTypedSource[User, User, SchemaPolicy.Exact](
            typedSrc,
            _ => IO.pure(User(0L, "sample@example.com", 0)),
          )
          .addTransform[User](u => IO.pure(u.copy(age = Math.max(0, u.age))))
          .addTypedSink[User, SchemaPolicy.Exact](
            typedSink,
            (_, d) => dao.write(ds, d).void, // write the real dataset read above
          )
          .build()
        // Data Quality demo: uses native Spark by default; switches to Deequ when -Dff.quality.mode=deequ
        dq = List[
          QualityConstraint
        ](
          QualityConstraint.NotNull(RefinedTypes.FieldName.unsafeFrom("id")),
          QualityConstraint.Unique(RefinedTypes.FieldName.unsafeFrom("id")),
          QualityConstraint.NullRateBelow(RefinedTypes.FieldName.unsafeFrom("email"), 0.0),
          QualityConstraint.Pattern(
            RefinedTypes.FieldName.unsafeFrom("email"),
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
          ),
          QualityConstraint.Min(RefinedTypes.FieldName.unsafeFrom("age"), 0.0),
          QualityConstraint.Max(RefinedTypes.FieldName.unsafeFrom("age"), 120.0),
          QualityConstraint.Distinctness(RefinedTypes.FieldName.unsafeFrom("id"), 1.0),
        )
        dqRes: DataAlgebra.QualityResult[DataAlgebra.Dataset[User]] = DeequAdapter.runChecks(spark, ds, dq)
        _ <- if (dqRes.passed)
               IO(log.info("DQ passed with score " + dqRes.score)) *> F.parSequence(
                 List(AuditDb.log("dq_passed"), AuditDb.log("dq_passed_duplicate")),
               ).void
             else IO(log.warn("DQ failed, violations: " + dqRes.violations.size.toString)) *> F.parSequence(
               List(AuditDb.log("dq_failed")),
             ).void
        _ <- PipelineExecution.execute(builder)(())
        // EffectSystem demo: parallel sum + bracket safety
        eff <- EffectsDemo.demo[IO]
        _   <- AuditDb.log("effects_demo sum=" + eff._1.toString + " closed=" + eff._2.toString)
        _ <- AuditDb.log("pipeline_completed")
      } yield ()
    }
  }
}
