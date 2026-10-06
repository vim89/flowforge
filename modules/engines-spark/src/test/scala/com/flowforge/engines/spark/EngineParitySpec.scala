// scalafix:off DisableSyntax.throw DisableSyntax.noUnsafeRunSync
package com.flowforge.engines.spark

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.validated._
import com.flowforge.core.algebra.{ DataAlgebra, EffectSystem }
import com.flowforge.core.impl.InMemoryDataAlgebra
import com.flowforge.core.instances.EffectInstances
import com.flowforge.core.types.PipelineTypes.QualityCheck
import com.flowforge.core.types._
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Parity between the Spark algebra and the in-memory one, on the same fixture.
 *
 * `engines-flink` already had a spec named for parity, but it compared `InMemoryDataAlgebra` with
 * `FlinkDataAlgebra`, which holds an `InMemoryDataAlgebra` and forwards to it. Comparing an object with
 * itself passes whatever the two engines do, so parity was asserted and never measured. Spark is the only
 * other algebra with its own implementation, so this is where a parity claim can be tested at all.
 */
class EngineParitySpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  import SparkLocalBatchSpec.{ personDecoder, personEncoder, Person }

  implicit private val F: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  private lazy val spark: SparkSession =
    SparkSession
      .builder()
      .appName("ff-engine-parity-spec")
      .master("local[2]")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.driver.host", "127.0.0.1")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

  override def afterAll(): Unit = {
    spark.stop()
    super.afterAll()
  }

  private lazy val sparkAlgebra: DataAlgebra[IO] =
    SparkDataAlgebra.createSparkDataAlgebra[IO](spark).algebra

  private lazy val memoryAlgebra: DataAlgebra[IO] = new InMemoryDataAlgebra[IO]()

  private val rows = List(Person(1, "Alice"), Person(2, "Bob"), Person(3, "Carol"))

  /** Small enough that the Spark sample holds every record, so the two `data` lists are comparable. */
  private def fixture: LocalDataSource = {
    val file  = Files.createTempFile("ff-parity", ".csv")
    val lines = "id,name" :: rows.map(p => s"${p.id},${p.name}")
    Files.write(file, lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
    LocalDataSource(file.toString, DataFormat.CSV)
  }

  private val nameNotBlank: QualityCheck[Person] = p =>
    if (p.name.trim.nonEmpty) ().validNel
    else ValidationError.SchemaViolation("name", "non-blank", "blank", message = "name is blank").invalidNel

  private val idIsNegative: QualityCheck[Person] = p =>
    if (p.id < 0) ().validNel
    else ValidationError.SchemaViolation("id", "negative", p.id.toString, message = "id is not negative").invalidNel

  test("both engines read the same records and the same count from one csv") {
    val source = fixture

    val fromSpark  = sparkAlgebra.read[Person](source).unsafeRunSync()
    val fromMemory = memoryAlgebra.read[Person](source).unsafeRunSync()

    fromSpark.data.sortBy(_.id) shouldBe rows
    fromMemory.data.sortBy(_.id) shouldBe rows
    sparkAlgebra.count(fromSpark) shouldBe memoryAlgebra.count(fromMemory)
  }

  test("both engines report the same quality check outcome") {
    val source = fixture
    val checks = NonEmptyList.of(nameNotBlank, idIsNegative)

    val sparkResults  = sparkAlgebra.runQualityChecks(sparkAlgebra.read[Person](source).unsafeRunSync(), checks).unsafeRunSync()
    val memoryResults = memoryAlgebra.runQualityChecks(memoryAlgebra.read[Person](source).unsafeRunSync(), checks).unsafeRunSync()

    // The second check fails on every record. An engine that reports it as passing is not running the check.
    sparkResults.map(r => r.checkName -> r.passed) shouldBe List("check_0" -> true, "check_1" -> false)
    memoryResults.map(r => r.checkName -> r.passed) shouldBe sparkResults.map(r => r.checkName -> r.passed)
  }

  test("a format neither engine supports fails on both, with the error type each one raises") {
    val source = LocalDataSource(fixture.location, DataFormat.Avro)

    // A known divergence, pinned rather than hidden: Spark wraps the failure in the framework's error type
    // and the in-memory algebra raises the plain exception. A caller that handles one does not handle both.
    val sparkFailure =
      intercept[DataProcessingError.ProcessingFailure](sparkAlgebra.read[Person](source).unsafeRunSync())
    val memoryFailure =
      intercept[UnsupportedOperationException](memoryAlgebra.read[Person](source).unsafeRunSync())

    sparkFailure.reason should include("Avro")
    memoryFailure.getMessage should include("Avro")
  }
}
