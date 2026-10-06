// scalafix:off DisableSyntax.throw DisableSyntax.noUnsafeRunSync
package com.flowforge.engines.spark

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.validated._
import com.flowforge.core.algebra.{
  CorruptedData,
  DataDecoder,
  DataEncoder,
  EffectSystem,
  EncodedData,
  EncodingHints,
  UnsupportedFormat,
}
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types.PipelineTypes.QualityCheck
import com.flowforge.core.types.RefinedTypes.BucketName
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

/**
 * One engine path, read to write, on the only Spark source and sink that are implemented: a local file.
 *
 * Every other Spark test here drives Delta merges or a dataset wrapper in isolation. None of them took a file
 * in, handed it to the algebra and checked what came out the other side, so the path the documentation calls
 * production had no test that ran it end to end.
 */
object SparkLocalBatchSpec {

  /**
   * Spark derives a product encoder from the type, so it has to be nameable from outside the test instance.
   */
  final case class Person(id: Int, name: String)

  implicit val personEncoder: DataEncoder[Person] = new DataEncoder[Person] {
    def encode(p: Person, format: DataFormat) = format match {
      case DataFormat.CSV  => Right(EncodedData(s"${p.id},${p.name}".getBytes("UTF-8"), format))
      case DataFormat.JSON => Right(EncodedData(json(p).getBytes("UTF-8"), format))
      case other           => Left(UnsupportedFormat(other, "Person"))
    }
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder.addField("id", DataType.Integer).addField("name", DataType.String).build
    def estimateSize(p: Person, format: DataFormat): Long = json(p).length.toLong
    def supportsFormat(format: DataFormat): Boolean = format == DataFormat.CSV || format == DataFormat.JSON
    def optimizationHints(p: Person, format: DataFormat): EncodingHints = EncodingHints.default

    private def json(p: Person): String = s"""{"id":${p.id},"name":"${p.name}"}"""
  }

  /**
   * Both formats are needed, and for the same reason the Spark path exists: `read` hands the frame to
   * `ProductionSparkDataset.fromDataFrame`, which samples it as JSON, while a CSV sink writes the record
   * itself.
   */
  implicit val personDecoder: DataDecoder[Person] = new DataDecoder[Person] {
    def decode(encoded: EncodedData, format: DataFormat) = {
      val text = new String(encoded.data, "UTF-8").trim
      format match {
        case DataFormat.CSV =>
          text.split(',') match {
            case Array(id, name) =>
              try Right(Person(id.toInt, name))
              catch { case _: NumberFormatException => Left(CorruptedData(s"Not a person record: $text")) }
            case _ => Left(CorruptedData(s"Expected two columns: $text"))
          }
        case DataFormat.JSON =>
          val id   = """"id"\s*:\s*(-?\d+)""".r.findFirstMatchIn(text).map(_.group(1).toInt)
          val name = """"name"\s*:\s*"([^"]*)"""".r.findFirstMatchIn(text).map(_.group(1))
          id.zip(name).map { case (i, n) => Person(i, n) }
            .toRight(CorruptedData(s"Not a person record: $text"))
        case other => Left(CorruptedData(s"Unsupported format: $other"))
      }
    }
    def validateSchema(encoded: EncodedData, expected: DataSchema) = Right(())
    def decodeWithEvolution(
      encoded: EncodedData,
      format: DataFormat,
      target: DataSchema,
    ) =
      decode(encoded, format)
    override def supportsFormat(format: DataFormat): Boolean =
      format == DataFormat.CSV || format == DataFormat.JSON
  }
}

class SparkLocalBatchSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  import SparkLocalBatchSpec._

  implicit private val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  /**
   * One session for the suite. Each test stopping and starting its own made the driver rebind a port five
   * times, which fails outright on a host whose name does not resolve to a local address.
   */
  private lazy val spark: SparkSession =
    SparkSession
      .builder()
      .appName("ff-local-batch-spec")
      .master("local[2]")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.driver.host", "127.0.0.1")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

  override def afterAll(): Unit = {
    spark.stop()
    super.afterAll()
  }

  private def csvFixture(rows: List[Person]): Path = {
    val file  = Files.createTempFile("ff-people", ".csv")
    val lines = "id,name" :: rows.map(p => s"${p.id},${p.name}")
    Files.write(file, lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
    file
  }

  private def tempDir(prefix: String): String = {
    val dir = Files.createTempDirectory(prefix)
    Files.delete(dir) // Spark writes the directory itself and refuses an existing one without overwrite
    dir.toString
  }

  test("a local csv read and parquet write keeps every record") {
    // A Spark write to a local path goes through Hadoop's RawLocalFileSystem, which sets permissions by
    // calling winutils.exe. Windows runners have no HADOOP_HOME, so the write fails there for reasons that
    // have nothing to do with this code. Reads need none of that, so only this test is skipped.
    assume(
      !sys.props.getOrElse("os.name", "").toLowerCase.contains("win"),
      "Spark local write needs winutils",
    )

    val rows   = List(Person(1, "Alice"), Person(2, "Bob"), Person(3, "Carol"))
    val source = LocalDataSource(csvFixture(rows).toString, DataFormat.CSV)
    val out    = tempDir("ff-people-out")
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val written = (for {
      dataset <- alg.read[Person](source)
      result  <- alg.write(dataset, LocalDataSink(out, DataFormat.Parquet))
    } yield result).unsafeRunSync()

    written.recordsWritten shouldBe 3L

    // Read the sink back with Spark rather than through the algebra: the point is what landed on disk.
    val readBack = spark.read
      .parquet(out).orderBy("id").collect().toList
      .map(r => Person(r.getAs[Int]("id"), r.getAs[String]("name")))
    readBack shouldBe rows
  }

  test("count reports the whole dataset, not the decoded sample") {
    // `fromDataFrame` decodes at most 100 records for compatibility with the in-memory interface. Counting
    // that sample instead of the frame reported 100 for any larger input.
    val rows   = (1 to 150).map(i => Person(i, s"p$i")).toList
    val source = LocalDataSource(csvFixture(rows).toString, DataFormat.CSV)
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val dataset = alg.read[Person](source).unsafeRunSync()

    alg.count(dataset) shouldBe 150L
    alg.isEmpty(dataset) shouldBe false
    dataset.metadata.recordCount shouldBe 150L
  }

  test("a filter before a write writes only the records that passed") {
    assume(
      !sys.props.getOrElse("os.name", "").toLowerCase.contains("win"),
      "Spark local write needs winutils",
    )

    // The predicate used to narrow the decoded sample and leave the frame alone, and the write writes the
    // frame. So a filter followed by a write wrote every record the source held, filtered or not.
    val rows   = (1 to 150).map(i => Person(i, s"p$i")).toList
    val source = LocalDataSource(csvFixture(rows).toString, DataFormat.CSV)
    val out    = tempDir("ff-filtered-out")
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val written = (for {
      dataset <- alg.read[Person](source)
      kept = alg.filter(dataset, (p: Person) => p.id <= 10)
      result <- alg.write(kept, LocalDataSink(out, DataFormat.Parquet))
    } yield result).unsafeRunSync()

    written.recordsWritten shouldBe 10L

    val readBack = spark.read
      .parquet(out).orderBy("id").collect().toList
      .map(r => Person(r.getAs[Int]("id"), r.getAs[String]("name")))
    readBack shouldBe rows.take(10)
  }

  test("map applies to every record, not just the sample") {
    val rows   = (1 to 150).map(i => Person(i, s"p$i")).toList
    val source = LocalDataSource(csvFixture(rows).toString, DataFormat.CSV)
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val doubled = alg.map(alg.read[Person](source).unsafeRunSync(), (p: Person) => Person(p.id * 2, p.name))

    alg.count(doubled) shouldBe 150L
    // 300 only exists if the record with id 150 went through the function, and that record is past the
    // sample the driver holds.
    alg.count(alg.filter(doubled, (p: Person) => p.id == 300)) shouldBe 1L
  }

  test("a quality check sees a violation past the sample") {
    val rows   = (1 to 150).map(i => Person(i, s"p$i")).toList
    val source = LocalDataSource(csvFixture(rows).toString, DataFormat.CSV)
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val dataset = alg.read[Person](source).unsafeRunSync()

    // The sample is the first records of the frame, so the last one is outside it. Checking that here keeps
    // the test from passing for the wrong reason if the sample ever grows.
    assume(!dataset.data.exists(_.id == 150), "the sample must not hold the violating record")

    val onlyRecord150Violates: QualityCheck[Person] = p =>
      if (p.id != 150) ().validNel
      else ValidationError.SchemaViolation("id", "not 150", "150", message = "id is 150").invalidNel

    val results = alg.runQualityChecks(dataset, NonEmptyList.one(onlyRecord150Violates)).unsafeRunSync()

    results.map(r => r.checkName -> r.passed) shouldBe List("check_0" -> false)
    results.head.message should include("id is 150")
  }

  test("an empty local source reads as an empty dataset") {
    val source = LocalDataSource(csvFixture(Nil).toString, DataFormat.CSV)
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

    val dataset = alg.read[Person](source).unsafeRunSync()

    alg.count(dataset) shouldBe 0L
    alg.isEmpty(dataset) shouldBe true
  }

  test("a source type the engine does not implement fails the read") {
    val alg = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
    val s3  = DataSource.S3Source(BucketName.unsafeFrom("bucket"), "prefix", DataFormat.Parquet)

    // The capability set advertises Read without saying which sources it covers, so this failure is the
    // only thing that tells a caller an S3 read is missing. Pinning it keeps the gap from being closed by
    // accident, in either direction.
    val failure = intercept[DataProcessingError.ProcessingFailure](alg.read[Person](s3).unsafeRunSync())

    failure.reason should include("S3Source")
  }

  test("a format the engine does not implement fails the read") {
    val alg    = SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra
    val source = LocalDataSource(csvFixture(Nil).toString, DataFormat.Avro)

    val failure = intercept[DataProcessingError.ProcessingFailure](alg.read[Person](source).unsafeRunSync())

    failure.reason should include("Avro")
  }
}
