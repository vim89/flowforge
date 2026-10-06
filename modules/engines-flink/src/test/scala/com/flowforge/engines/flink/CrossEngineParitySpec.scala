// scalafix:off DisableSyntax.throw DisableSyntax.noUnsafeRunSync
package com.flowforge.engines.flink

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.validated._
import com.flowforge.core.algebra._
import com.flowforge.core.impl.InMemoryDataAlgebra
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract, QualityCheck }
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * The codecs, the checks and the contracts live here rather than in the suite.
 *
 * Every one of them travels to a Flink task, so every one of them is serialized. A value declared in the
 * suite's body captures the suite, which is not serializable, so the job would fail on the way out rather
 * than on anything the test is about.
 */
object CrossEngineParitySpec {

  case class Person(id: Int, name: String)

  implicit val personEncoder: DataEncoder[Person] = new DataEncoder[Person] {
    def encode(p: Person, format: DataFormat) = format match {
      case DataFormat.CSV =>
        Right(EncodedData(s"${p.id},${p.name}".getBytes(StandardCharsets.UTF_8), format))
      case DataFormat.JSON | DataFormat.JSONL =>
        val json = s"""{"id":${p.id},"name":"${p.name}"}"""
        Right(EncodedData(json.getBytes(StandardCharsets.UTF_8), format))
      case other => Left(UnsupportedFormat(other, "Person"))
    }
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder.addField("id", DataType.Integer).addField("name", DataType.String).build
    def estimateSize(p: Person, format: DataFormat): Long = 0L
    def supportsFormat(format: DataFormat): Boolean =
      format == DataFormat.CSV || format == DataFormat.JSON || format == DataFormat.JSONL
    def optimizationHints(p: Person, format: DataFormat): EncodingHints = EncodingHints.default
  }

  implicit val personDecoder: DataDecoder[Person] = new DataDecoder[Person] {
    def decode(encoded: EncodedData, format: DataFormat) = {
      val text = new String(encoded.data, StandardCharsets.UTF_8).trim
      format match {
        case DataFormat.CSV =>
          text.split(',') match {
            case Array(id, name) =>
              id.toIntOption.map(i => Person(i, name)).toRight(CorruptedData(s"Not a person record: $text"))
            case _ => Left(CorruptedData(s"Expected two columns: $text"))
          }
        case DataFormat.JSON | DataFormat.JSONL =>
          val id   = """"id"\s*:\s*(-?\d+)""".r.findFirstMatchIn(text).flatMap(_.group(1).toIntOption)
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
      format == DataFormat.CSV || format == DataFormat.JSON || format == DataFormat.JSONL
  }

  val nameNotBlank: QualityCheck[Person] = p =>
    if (p.name.trim.nonEmpty) ().validNel
    else ValidationError.SchemaViolation("name", "non-blank", "blank", message = "name is blank").invalidNel

  val idIsNegative: QualityCheck[Person] = p =>
    if (p.id < 0) ().validNel
    else
      ValidationError
        .SchemaViolation("id", "negative", p.id.toString, message = "id is not negative").invalidNel

  /** Rejects Bob and nobody else, so a passing result means the contract was not applied. */
  val nameIsNotBob: PDataContract[Person] = p =>
    if (p.name != "Bob") ().validNel
    else ValidationError.SchemaViolation("name", "not Bob", p.name, message = "name is Bob").invalidNel

  val isEven: Person => Boolean = _.id % 2 == 0

  val shout: Person => Person = p => p.copy(name = p.name.toUpperCase)
}

/**
 * Parity between the Flink algebra and the in-memory one, on the same fixture.
 *
 * This suite used to compare `FlinkDataAlgebra` with the `InMemoryDataAlgebra` it forwarded every call to,
 * which compared an object with itself and passed whatever the two did. The Flink algebra now runs `read`,
 * `write`, `filter`, `map`, `validate` and `runQualityChecks` as Flink jobs, so there are two implementations
 * to compare and a disagreement between them can fail a test.
 *
 * Each test runs its own Flink job on a local MiniCluster, so these are slow by the standards of the rest of
 * the build. That is the price of checking that the engine runs rather than that it delegates.
 */
class CrossEngineParitySpec extends AnyFunSuite with Matchers {

  import CrossEngineParitySpec._

  implicit private val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  private val flink: DataAlgebra[IO]  = new FlinkDataAlgebra[IO]()
  private val memory: DataAlgebra[IO] = new InMemoryDataAlgebra[IO]()

  private val rows = List(Person(1, "Alice"), Person(2, "Bob"), Person(3, "Carol"))

  /** Small enough that the Flink sample holds every record, so the two `data` lists are comparable. */
  private def csvFixture(people: List[Person] = rows): LocalDataSource = {
    val file  = Files.createTempFile("ff-flink-parity", ".csv")
    val lines = "id,name" :: people.map(p => s"${p.id},${p.name}")
    Files.write(file, lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
    LocalDataSource(file.toString, DataFormat.CSV)
  }

  test("both engines read the same records and the same count from one csv") {
    val source = csvFixture()

    val fromFlink  = flink.read[Person](source).unsafeRunSync()
    val fromMemory = memory.read[Person](source).unsafeRunSync()

    fromFlink.data.sortBy(_.id) shouldBe rows
    fromMemory.data.sortBy(_.id) shouldBe rows
    flink.count(fromFlink) shouldBe memory.count(fromMemory)
  }

  test("the record count comes from the job, not from the sample") {
    val people = (1 to 250).toList.map(i => Person(i, s"person-$i"))
    val source = csvFixture(people)

    val dataset = flink.read[Person](source).unsafeRunSync()

    // `data` is capped at the sample size and says so. `count` answers for the whole file.
    dataset.data.size shouldBe FlinkDataset.sampleSize
    flink.count(dataset) shouldBe 250L
    flink.count(flink.filter[Person](dataset, isEven)) shouldBe 125L
  }

  test("both engines filter and map to the same records") {
    val source = csvFixture()

    val flinkOut = flink.map[Person, Person](
      flink.filter[Person](flink.read[Person](source).unsafeRunSync(), isEven),
      shout,
    )
    val memoryOut = memory.map[Person, Person](
      memory.filter[Person](memory.read[Person](source).unsafeRunSync(), isEven),
      shout,
    )

    flinkOut.data.sortBy(_.id) shouldBe List(Person(2, "BOB"))
    memoryOut.data.sortBy(_.id) shouldBe flinkOut.data.sortBy(_.id)
  }

  test("both engines report the same quality check outcome") {
    val source = csvFixture()
    val checks = NonEmptyList.of(nameNotBlank, idIsNegative)

    val flinkResults =
      flink.runQualityChecks(flink.read[Person](source).unsafeRunSync(), checks).unsafeRunSync()
    val memoryResults =
      memory.runQualityChecks(memory.read[Person](source).unsafeRunSync(), checks).unsafeRunSync()

    // The second check fails on every record. An engine that reports it as passing is not running the check.
    flinkResults.map(r => r.checkName -> r.passed) shouldBe List("check_0" -> true, "check_1" -> false)
    memoryResults.map(r => r.checkName -> r.passed) shouldBe flinkResults.map(r => r.checkName -> r.passed)
  }

  test("both engines report the same contract result") {
    val source = csvFixture()

    val fromFlink = flink.validate(flink.read[Person](source).unsafeRunSync(), nameIsNotBob).unsafeRunSync()
    val fromMemory =
      memory.validate(memory.read[Person](source).unsafeRunSync(), nameIsNotBob).unsafeRunSync()

    fromFlink.passed shouldBe false
    fromFlink.score shouldBe (2.0 / 3.0)
    fromFlink.violations.map(v => (v.message, v.recordsAffected)) shouldBe List(("name is Bob", 1L))

    // `data` differs - each engine returns its own dataset - so compare what the contract said about it.
    fromMemory.passed shouldBe fromFlink.passed
    fromMemory.score shouldBe fromFlink.score
    fromMemory.violations shouldBe fromFlink.violations
  }

  test("the flink sink writes every record, and the records read back") {
    val source = csvFixture()
    val out    = Files.createTempFile("ff-flink-out", ".jsonl")
    Files.delete(out) // the sink writes the file itself

    val dataset = flink.read[Person](source).unsafeRunSync()
    val result  = flink.write(dataset, LocalDataSink(out.toString, DataFormat.JSONL)).unsafeRunSync()

    result.success shouldBe true
    result.recordsWritten shouldBe 3L

    val readBack = flink.read[Person](LocalDataSource(out.toString, DataFormat.JSONL)).unsafeRunSync()
    readBack.data.sortBy(_.id) shouldBe rows
  }

  test("a sink format the flink engine does not write fails rather than writing a sample") {
    val source = csvFixture()
    val out    = Files.createTempFile("ff-flink-csv-out", ".csv").toString

    val dataset = flink.read[Person](source).unsafeRunSync()
    val failure = intercept[UnsupportedOperationException](
      flink.write(dataset, LocalDataSink(out, DataFormat.CSV)).unsafeRunSync(),
    )

    failure.getMessage should include("CSV")
  }

  test("a source format neither engine reads fails on both, with the error type each one raises") {
    val source = LocalDataSource(csvFixture().location, DataFormat.Avro)

    // A known divergence, pinned rather than hidden: the Flink engine wraps the failure in the framework's
    // error type, like Spark does, and the in-memory algebra raises the plain exception.
    val flinkFailure =
      intercept[DataProcessingError.ProcessingFailure](flink.read[Person](source).unsafeRunSync())
    val memoryFailure =
      intercept[UnsupportedOperationException](memory.read[Person](source).unsafeRunSync())

    flinkFailure.reason should include("Avro")
    memoryFailure.getMessage should include("Avro")
  }
}
