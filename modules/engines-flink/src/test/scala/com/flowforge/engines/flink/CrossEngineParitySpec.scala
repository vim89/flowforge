// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.engines.flink

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.flowforge.core.algebra._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types.PipelineTypes.QualityCheck
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CrossEngineParitySpec extends AnyFunSuite with Matchers {

  case class User(id: Int, name: String)

  implicit val userEncoder: DataEncoder[User] = new DataEncoder[User] {
    def encode(u: User, format: DataFormat) = format match {
      case DataFormat.CSV =>
        val line = s"${u.id},${u.name}"
        Right(EncodedData(line.getBytes("UTF-8"), format))
      case other => Left(UnsupportedFormat(other, "User"))
    }
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder
        .addField("id", DataType.Integer)
        .addField("name", DataType.String)
        .build
    def estimateSize(u: User, format: DataFormat): Long               = 0L
    def supportsFormat(format: DataFormat): Boolean                   = format == DataFormat.CSV
    def optimizationHints(u: User, format: DataFormat): EncodingHints = EncodingHints.default
  }

  implicit val userDecoder: DataDecoder[User] = new DataDecoder[User] {
    def decode(encoded: EncodedData, format: DataFormat) = format match {
      case DataFormat.CSV =>
        val parts = new String(encoded.data, "UTF-8").split(',')
        Right(User(parts(0).toInt, parts(1)))
      case other => Left(CorruptedData(s"Unsupported format: $other"))
    }
    def validateSchema(encoded: EncodedData, expected: DataSchema) = Right(())
    def decodeWithEvolution(
      encoded: EncodedData,
      format: DataFormat,
      target: DataSchema,
    ) =
      decode(encoded, format)
    override def supportsFormat(format: DataFormat): Boolean = format == DataFormat.CSV
  }

  private val check: QualityCheck[User] = _ => ().validNel
  private val checks                    = NonEmptyList.one(check)

  private def writeSourceFile(): java.nio.file.Path = {
    val tmp     = Files.createTempFile("users", ".csv")
    val content = "id,name\n1,Alice\n2,Bob\n"
    Files.write(tmp, content.getBytes(StandardCharsets.UTF_8))
    tmp
  }

  test("Flink matches in-memory engine on basic read/write/quality operations") {
    val sourcePath = writeSourceFile()
    val source     = LocalDataSource(sourcePath.toString, DataFormat.CSV)

    val inMemoryDA = new com.flowforge.core.impl.InMemoryDataAlgebra[IO]()
    val flinkDA    = new FlinkDataAlgebra[IO]()

    val memOut   = Files.createTempFile("mem-out", ".csv").toString
    val flinkOut = Files.createTempFile("flink-out", ".csv").toString

    val memRes = (for {
      ds <- inMemoryDA.read[User](source)
      _  <- inMemoryDA.write(ds, LocalDataSink(memOut, DataFormat.CSV))
      qc <- inMemoryDA.runQualityChecks(ds, checks)
    } yield qc).unsafeRunSync()

    val flinkRes = (for {
      ds <- flinkDA.read[User](source)
      _  <- flinkDA.write(ds, LocalDataSink(flinkOut, DataFormat.CSV))
      qc <- flinkDA.runQualityChecks(ds, checks)
    } yield qc).unsafeRunSync()

    memRes.map(_.passed) shouldEqual flinkRes.map(_.passed)
  }
}
