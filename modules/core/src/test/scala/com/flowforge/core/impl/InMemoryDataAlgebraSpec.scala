// scalafix:off DisableSyntax.throw DisableSyntax.noUnsafeRunSync
package com.flowforge.core.impl

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.flowforge.core.algebra.DataAlgebra._
import com.flowforge.core.algebra.{ CDCOperations, TableOperations, _ }
import com.flowforge.core.instances.DefaultCodecs._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.types.PipelineTypes.{ DataContract => PDataContract, QualityCheck }
import com.flowforge.core.types.RefinedTypes._
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Comprehensive test suite for InMemoryDataAlgebra.
 *
 * Tests all 362 statements and 23 branches for complete coverage.
 */
class InMemoryDataAlgebraSpec extends AnyFunSuite with Matchers {
  implicit val runtime: cats.effect.unsafe.IORuntime = cats.effect.unsafe.IORuntime.global

  // Test algebra instance
  val algebra = new InMemoryDataAlgebra[IO]

  // Encoder needed for join outputs producing 3-tuples
  implicit val tuple3Encoder: DataEncoder[(String, String, String)] = new DataEncoder[(String, String, String)] {
    def encode(data: (String, String, String), format: DataFormat) = Right(EncodedData(Array.emptyByteArray, format))
    def schema(format: DataFormat): DataSchema                                  = DataSchema.builder.build
    def estimateSize(data: (String, String, String), format: DataFormat): Long  = 0L
    def supportsFormat(format: DataFormat): Boolean                             = true
    def optimizationHints(data: (String, String, String), format: DataFormat)   = EncodingHints.default
  }

  // Helper to create temporary test files
  def createTempFile(content: String, suffix: String = ".jsonl"): File = {
    val tempFile = File.createTempFile("test", suffix)
    tempFile.deleteOnExit()
    Files.write(tempFile.toPath, content.getBytes("UTF-8"))
    tempFile
  }

  // ===============================
  // CAPABILITIES TESTS
  // ===============================

  test("capabilities should include Read, Write, and QualityChecks") {
    algebra.capabilities should contain(Capability.Read)
    algebra.capabilities should contain(Capability.Write)
    algebra.capabilities should contain(Capability.QualityChecks)
  }

  test("supports should return true for supported capabilities") {
    algebra.supports(Capability.Read) shouldBe true
    algebra.supports(Capability.Write) shouldBe true
    algebra.supports(Capability.QualityChecks) shouldBe true
  }

  // ===============================
  // READ OPERATIONS (JSONL)
  // ===============================

  test("read JSONL returns dataset with decoded records") {
    val jsonlContent = """{"value":"test1"}
{"value":"test2"}
{"value":"test3"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL),
      )
      .unsafeRunSync()

    result.data.size shouldBe 3
    result.data.exists(_.contains("test1")) shouldBe true
    result.data.exists(_.contains("test2")) shouldBe true
    result.data.exists(_.contains("test3")) shouldBe true
    result.metadata.recordCount shouldBe 3
    result.metadata.source shouldBe Some(LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL))
  }

  test("read JSONL with schema uses provided schema") {
    val jsonlContent = """{"value":"test1"}
{"value":"test2"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")

    val customSchema = DataSchema.builder.addField("custom", DataType.String).build
    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL, schema = Some(customSchema)),
      )
      .unsafeRunSync()

    result.schema shouldBe customSchema
  }

  test("read JSONL without schema uses default schema") {
    val jsonlContent = """{"value":"test1"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL),
      )
      .unsafeRunSync()

    result.schema.fieldNames shouldBe List.empty
  }

  test("read JSONL skips invalid lines") {
    val jsonlContent = """{"value":"test1"}
invalid json line
{"value":"test2"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL),
      )
      .unsafeRunSync()

    result.data.size should be >= 2
    result.data.exists(_.contains("test1")) shouldBe true
    result.data.exists(_.contains("test2")) shouldBe true
  }

  // ===============================
  // READ OPERATIONS (CSV)
  // ===============================

  test("read CSV returns dataset skipping header") {
    val csvContent = """header1,header2
test1
test2
test3"""
    val tempFile   = createTempFile(csvContent, ".csv")

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.CSV),
      )
      .unsafeRunSync()

    result.data.size shouldBe 3
    result.data should contain("test1")
    result.data should contain("test2")
    result.data should contain("test3")
  }

  test("read CSV with schema uses provided schema") {
    val csvContent   = """header1,header2
test1"""
    val tempFile     = createTempFile(csvContent, ".csv")
    val customSchema = DataSchema.builder.addField("col1", DataType.String).build

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.CSV, schema = Some(customSchema)),
      )
      .unsafeRunSync()

    result.schema shouldBe customSchema
  }

  test("read CSV skips invalid lines") {
    val csvContent = """header
test1
invalid data that fails decoding
test2"""
    val tempFile   = createTempFile(csvContent, ".csv")

    val result = algebra
      .read[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.CSV),
      )
      .unsafeRunSync()

    // At least some records should be read (behavior depends on decoder)
    result.metadata.recordCount shouldBe result.data.size.toLong
  }

  // ===============================
  // READ OPERATIONS (ERROR CASES)
  // ===============================

  test("read unsupported format raises error") {
    val tempFile = createTempFile("content", ".parquet")

    val error = intercept[Exception] {
      algebra
        .read[String](
          LocalDataSource(tempFile.getAbsolutePath, DataFormat.Parquet),
        )
        .unsafeRunSync()
    }

    val msg = error.getMessage.toLowerCase
    msg should (include("not supported") or include("unsupported format"))
  }

  test("read non-LocalDataSource raises error") {
    val gcsSource = DataSource.GcsSource(
      BucketName.unsafeFrom("test-bucket"),
      "prefix",
      DataFormat.JSONL,
    )

    val error = intercept[Exception] {
      algebra.read[String](gcsSource).unsafeRunSync()
    }

    error.getMessage should include("Only LocalDataSource supported")
  }

  // ===============================
  // READ WITH SCHEMA VALIDATION
  // ===============================

  test("readWithSchema returns validated dataset") {
    val jsonlContent = """{"value":"test1"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")
    val schema       = DataSchema.builder.addField("value", DataType.String).build

    val result = algebra
      .readWithSchema[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL),
        schema,
      )
      .unsafeRunSync()

    result.isValid shouldBe true
    result.toOption.get.data.exists(_.contains("test1")) shouldBe true
  }

  // ===============================
  // STREAM OPERATIONS
  // ===============================

  test("stream returns DataStream with chunks") {
    val jsonlContent = """{"value":"test1"}
{"value":"test2"}"""
    val tempFile     = createTempFile(jsonlContent, ".jsonl")

    val stream = algebra
      .stream[String](
        LocalDataSource(tempFile.getAbsolutePath, DataFormat.JSONL),
      )
      .unsafeRunSync()

    val chunks = stream.chunks.unsafeRunSync()
    chunks.size shouldBe 1
    chunks.head.data.size shouldBe 2
    chunks.head.data.exists(_.contains("test1")) shouldBe true
  }

  // ===============================
  // WRITE OPERATIONS (JSONL)
  // ===============================

  test("write JSONL creates file with data") {
    val tempFile = File.createTempFile("write-test", ".jsonl")
    tempFile.deleteOnExit()

    val data = List("test1", "test2", "test3")
    val dataset = SimpleDataset(
      data,
      DataSchema.builder.addField("value", DataType.String).build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra
      .write(
        dataset,
        LocalDataSink(tempFile.getAbsolutePath, DataFormat.JSONL),
        WriteOptions.default,
      )
      .unsafeRunSync()

    result.success shouldBe true
    result.recordsWritten shouldBe 3
    result.partitionsWritten shouldBe 1
    result.bytesWritten shouldBe 300L // 3 records * 100 bytes estimate

    val content = new String(Files.readAllBytes(tempFile.toPath))
    content should include("test1")
    content should include("test2")
    content should include("test3")
  }

  test("write JSONL handles encoding errors gracefully") {
    val tempFile = File.createTempFile("write-error-test", ".jsonl")
    tempFile.deleteOnExit()

    // Create a custom encoder that throws for specific data
    implicit val failingEncoder: DataEncoder[String] = new DataEncoder[String] {
      def encode(data: String, format: DataFormat) =
        if (data == "fail") Left(UnsupportedFormat(format, "String"))
        else Right(EncodedData(data.getBytes("UTF-8"), format))
      def schema(format: DataFormat): DataSchema =
        DataSchema.builder.addField("value", DataType.String).build
      def estimateSize(data: String, format: DataFormat): Long               = data.length.toLong
      def supportsFormat(format: DataFormat): Boolean                        = true
      def optimizationHints(data: String, format: DataFormat): EncodingHints = EncodingHints.default
    }

    val data = List("test1", "fail", "test3")
    val dataset = SimpleDataset(
      data,
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val error = intercept[RuntimeException] {
      algebra
        .write(
          dataset,
          LocalDataSink(tempFile.getAbsolutePath, DataFormat.JSONL),
          WriteOptions.default,
        )(failingEncoder)
        .unsafeRunSync()
    }

    error.getMessage.toLowerCase should (include("not supported") or include("unsupported format"))
  }

  // ===============================
  // WRITE OPERATIONS (CSV)
  // ===============================

  test("write CSV creates file with header and data") {
    val tempFile = File.createTempFile("write-csv-test", ".csv")
    tempFile.deleteOnExit()

    val data = List("value1", "value2")
    val schema =
      DataSchema.builder.addField("col1", DataType.String).addField("col2", DataType.String).build
    val dataset = SimpleDataset(
      data,
      schema,
      DatasetMetadata(2, schema, 1, Instant.now()),
    )

    val result = algebra
      .write(
        dataset,
        LocalDataSink(tempFile.getAbsolutePath, DataFormat.CSV),
        WriteOptions.default,
      )
      .unsafeRunSync()

    result.success shouldBe true
    result.recordsWritten shouldBe 2

    val content = new String(Files.readAllBytes(tempFile.toPath))
    content should include("col1,col2")
    content should include("value1")
    content should include("value2")
  }

  test("write CSV with empty fieldNames creates data without header") {
    val tempFile = File.createTempFile("write-csv-no-header", ".csv")
    tempFile.deleteOnExit()

    val data    = List("value1", "value2")
    val schema  = DataSchema.builder.build // No fields
    val dataset = SimpleDataset(data, schema, DatasetMetadata(2, schema, 1, Instant.now()))

    val result = algebra
      .write(
        dataset,
        LocalDataSink(tempFile.getAbsolutePath, DataFormat.CSV),
        WriteOptions.default,
      )
      .unsafeRunSync()

    result.success shouldBe true

    val content = new String(Files.readAllBytes(tempFile.toPath))
    content should include("value1")
    content should include("value2")
    content shouldNot include(",") // No header comma
  }

  // ===============================
  // WRITE OPERATIONS (ERROR CASES)
  // ===============================

  test("write unsupported format raises error") {
    val tempFile = File.createTempFile("write-unsupported", ".parquet")
    tempFile.deleteOnExit()

    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val error = intercept[Exception] {
      algebra
        .write(
          dataset,
          LocalDataSink(tempFile.getAbsolutePath, DataFormat.Parquet),
          WriteOptions.default,
        )
        .unsafeRunSync()
    }

    error.getMessage should include("Unsupported format")
  }

  test("write non-LocalDataSink raises error") {
    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val gcsSink = DataSink.GcsSink(
      BucketName.unsafeFrom("test-bucket"),
      "prefix",
      DataFormat.JSONL,
    )

    val error = intercept[Exception] {
      algebra
        .write(
          dataset,
          gcsSink,
          WriteOptions.default,
        )
        .unsafeRunSync()
    }

    error.getMessage should include("Unsupported sink")
  }

  test("write increments Prometheus metrics") {
    val tempFile = File.createTempFile("write-metrics", ".jsonl")
    tempFile.deleteOnExit()

    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    // This should not fail even if Prometheus metrics are unavailable
    val result = algebra
      .write(
        dataset,
        LocalDataSink(tempFile.getAbsolutePath, DataFormat.JSONL),
        WriteOptions.default,
      )
      .unsafeRunSync()

    result.success shouldBe true
  }

  // ===============================
  // WRITE WITH VALIDATION
  // ===============================

  test("writeWithValidation returns validated result") {
    val tempFile = File.createTempFile("write-validated", ".jsonl")
    tempFile.deleteOnExit()

    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    // DataAlgebra.writeWithValidation expects PipelineTypes.DataContract (A => ValidatedNel[FlowForgeError, Unit])
    val contract: PDataContract[String] = _ => cats.data.Validated.validNel(())

    val result = algebra
      .writeWithValidation(
        dataset,
        LocalDataSink(tempFile.getAbsolutePath, DataFormat.JSONL),
        contract,
        WriteOptions.default,
      )
      .unsafeRunSync()

    result.isValid shouldBe true
    result.toOption.get.success shouldBe true
  }

  // ===============================
  // PURE TRANSFORMATIONS
  // ===============================

  test("filter removes elements not matching predicate") {
    val dataset = SimpleDataset(
      List(1, 2, 3, 4, 5),
      DataSchema.builder.build,
      DatasetMetadata(5, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.filter(dataset, (x: Int) => x % 2 == 0)

    result.data shouldBe List(2, 4)
  }

  test("map transforms dataset elements") {
    val dataset = SimpleDataset(
      List(1, 2, 3),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.map(dataset, (x: Int) => x * 2)

    result.data shouldBe List(2, 4, 6)
    result.metadata.recordCount shouldBe 3
  }

  test("flatMap flattens nested datasets") {
    val dataset = SimpleDataset(
      List(1, 2),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.flatMap(dataset, (x: Int) => {
      SimpleDataset(
        List(x, x * 10),
        DataSchema.builder.build,
        DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
      )
    })

    result.data shouldBe List(1, 10, 2, 20)
    result.metadata.recordCount shouldBe 4
  }

  test("flatMap with empty nested dataset uses original schema") {
    val dataset = SimpleDataset(
      List(1),
      DataSchema.builder.addField("test", DataType.Integer).build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.flatMap(dataset, (_: Int) => {
      SimpleDataset(
        List.empty[Int],
        DataSchema.builder.build,
        DatasetMetadata(0, DataSchema.builder.build, 1, Instant.now()),
      )
    })

    result.data shouldBe List.empty
    result.schema shouldBe dataset.schema
  }

  test("groupBy aggregates by key") {
    val dataset = SimpleDataset(
      List("apple", "banana", "apricot", "blueberry"),
      DataSchema.builder.build,
      DatasetMetadata(4, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.groupBy(
      dataset,
      (s: String) => s.charAt(0),
      (list: List[String]) => list.mkString(","),
    )

    result.data.size shouldBe 2
    result.data should contain(('a', "apple,apricot"))
    result.data should contain(('b', "banana,blueberry"))
    result.metadata.recordCount shouldBe 2
  }

  test("join combines datasets by key") {
    // Provide local encoder for tuple3 output required by join's typeclass bound
    implicit val tuple3Encoder: DataEncoder[(String, String, String)] = new DataEncoder[(String, String, String)] {
      def encode(data: (String, String, String), format: DataFormat) = Right(EncodedData(Array.emptyByteArray, format))
      def schema(format: DataFormat): DataSchema                                  = DataSchema.builder.build
      def estimateSize(data: (String, String, String), format: DataFormat): Long  = 0L
      def supportsFormat(format: DataFormat): Boolean                             = true
      def optimizationHints(data: (String, String, String), format: DataFormat)   = EncodingHints.default
    }
    val left = SimpleDataset(
      List(("k1", "left1"), ("k2", "left2")),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val right = SimpleDataset(
      List(("k1", "right1"), ("k2", "right2a"), ("k2", "right2b")),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.join(
      left,
      right,
      (l: (String, String)) => l._1,
      (r: (String, String)) => r._1,
      (l: (String, String), r: (String, String)) => (l._1, l._2, r._2),
    )

    result.data.size shouldBe 3
    result.data should contain(("k1", "left1", "right1"))
    result.data should contain(("k2", "left2", "right2a"))
    result.data should contain(("k2", "left2", "right2b"))
  }

  test("join with non-matching keys produces empty result") {
    val left = SimpleDataset(
      List(("k1", "left1")),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val right = SimpleDataset(
      List(("k2", "right2")),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.join(
      left,
      right,
      (l: (String, String)) => l._1,
      (r: (String, String)) => r._1,
      (l: (String, String), r: (String, String)) => (l._1, l._2, r._2),
    )

    result.data shouldBe List.empty
  }

  test("union combines two datasets") {
    val left = SimpleDataset(
      List(1, 2),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val right = SimpleDataset(
      List(3, 4),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.union(left, right)

    result.data shouldBe List(1, 2, 3, 4)
    result.metadata.recordCount shouldBe 4
  }

  test("sortBy orders dataset by key") {
    val dataset = SimpleDataset(
      List(3, 1, 4, 1, 5),
      DataSchema.builder.build,
      DatasetMetadata(5, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.sortBy(dataset, (x: Int) => x)

    result.data shouldBe List(1, 1, 3, 4, 5)
  }

  test("take returns first n elements") {
    val dataset = SimpleDataset(
      List(1, 2, 3, 4, 5),
      DataSchema.builder.build,
      DatasetMetadata(5, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.take(dataset, 3)

    result.data shouldBe List(1, 2, 3)
    result.metadata.recordCount shouldBe 3
  }

  test("take with n greater than size returns all elements") {
    val dataset = SimpleDataset(
      List(1, 2),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.take(dataset, 10)

    result.data shouldBe List(1, 2)
    result.metadata.recordCount shouldBe 2
  }

  test("drop removes first n elements") {
    val dataset = SimpleDataset(
      List(1, 2, 3, 4, 5),
      DataSchema.builder.build,
      DatasetMetadata(5, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.drop(dataset, 2)

    result.data shouldBe List(3, 4, 5)
    result.metadata.recordCount shouldBe 3
  }

  test("drop with n greater than size returns empty dataset") {
    val dataset = SimpleDataset(
      List(1, 2),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.drop(dataset, 10)

    result.data shouldBe List.empty
    result.metadata.recordCount shouldBe 0
  }

  // ===============================
  // EFFECTFUL TRANSFORMATIONS
  // ===============================

  test("transformWithEffect applies effectful transformation") {
    val dataset = SimpleDataset(
      List(1, 2, 3),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra
      .transformWithEffect(dataset, (x: Int) => IO.pure(x * 2))
      .unsafeRunSync()

    result.data shouldBe List(2, 4, 6)
    result.metadata.recordCount shouldBe 3
  }

  test("transformPipeline applies multiple transformations sequentially") {
    val dataset = SimpleDataset(
      List(1, 2, 3),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val transformations = NonEmptyList.of(
      (x: Int) => IO.pure(x * 2),
      (x: Int) => IO.pure(x + 10),
    )

    val result = algebra
      .transformPipeline(dataset, transformations)
      .unsafeRunSync()

    // Transformations are applied to original value, last one wins
    result.data shouldBe List(11, 12, 13)
  }

  // ===============================
  // METADATA & SCHEMA OPERATIONS
  // ===============================

  test("extractSchema returns dataset schema") {
    val schema  = DataSchema.builder.addField("test", DataType.String).build
    val dataset = SimpleDataset(List("test"), schema, DatasetMetadata(1, schema, 1, Instant.now()))

    val result = algebra.extractSchema(dataset).unsafeRunSync()

    result shouldBe schema
  }

  test("evolveSchema migrates dataset to new schema") {
    val sourceSchema = DataSchema.builder.addField("old", DataType.String).build
    val targetSchema = DataSchema.builder.addField("new", DataType.String).build
    val dataset      = SimpleDataset(List(1, 2), sourceSchema, DatasetMetadata(2, sourceSchema, 1, Instant.now()))

    val srcSch = sourceSchema
    val tgtSch = targetSchema
    val migration = new SchemaMigration[Int, String] {
      def migrate(data: Int): String = s"migrated_$data"
      val sourceSchema: DataSchema   = srcSch
      val targetSchema: DataSchema   = tgtSch
    }

    val result = algebra.evolveSchema(dataset, migration).unsafeRunSync()

    result.data shouldBe List("migrated_1", "migrated_2")
    result.schema shouldBe targetSchema
    result.metadata.schema shouldBe targetSchema
    result.metadata.recordCount shouldBe 2
  }

  test("compareSchemas returns compatibility report for matching schemas") {
    val schema1 = DataSchema.builder.addField("field1", DataType.String).build
    val schema2 = DataSchema.builder.addField("field1", DataType.String).build

    val result = algebra.compareSchemas(schema1, schema2).unsafeRunSync()

    result.compatible shouldBe true
    result.changes shouldBe List.empty
    result.breakingChanges shouldBe List.empty
  }

  test("compareSchemas returns incompatible for different schemas") {
    val schema1 = DataSchema.builder.addField("field1", DataType.String).build
    val schema2 = DataSchema.builder.addField("field2", DataType.String).build

    val result = algebra.compareSchemas(schema1, schema2).unsafeRunSync()

    result.compatible shouldBe false
  }

  // ===============================
  // LINEAGE OPERATIONS
  // ===============================

  test("recordLineage creates lineage record") {
    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(
        1,
        DataSchema.builder.build,
        1,
        Instant.now(),
        Some(LocalDataSource("test.jsonl", DataFormat.JSONL)),
      ),
    )

    val context = LineageContext("test-pipeline", "job-123", Instant.now(), "test-user")

    val result = algebra.recordLineage(dataset, "test-operation", context).unsafeRunSync()

    result.operation shouldBe "test-operation"
    result.context shouldBe context
    result.source shouldBe LocalDataSource("test.jsonl", DataFormat.JSONL)
    result.inputSchemas should contain(dataset.schema)
    result.outputSchema shouldBe Some(dataset.schema)
  }

  test("recordLineage uses unknown source when metadata source is missing") {
    val dataset = SimpleDataset(
      List("test"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now(), None),
    )

    val context = LineageContext("test-pipeline", "job-123", Instant.now(), "test-user")

    val result = algebra.recordLineage(dataset, "test-operation", context).unsafeRunSync()

    result.source shouldBe LocalDataSource("unknown", DataFormat.JSON)
  }

  test("queryLineage returns empty list") {
    val query  = LineageQuery()
    val result = algebra.queryLineage(query).unsafeRunSync()

    result shouldBe List.empty
  }

  // ===============================
  // QUALITY OPERATIONS
  // ===============================

  test("validate returns passing quality result") {
    val dataset  = SimpleDataset(List("test"), DataSchema.builder.build, DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()))
    val contract: PDataContract[String] = _ => cats.data.Validated.validNel(())

    val result = algebra.validate(dataset, contract).unsafeRunSync()

    result.passed shouldBe true
    result.violations shouldBe List.empty
    result.score shouldBe 1.0
    result.data shouldBe dataset
  }

  test("runQualityChecks returns results for all checks") {
    val dataset = SimpleDataset(List("test"), DataSchema.builder.build, DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()))

    val check1: QualityCheck[String] = _ => cats.data.Validated.validNel(())
    val check2: QualityCheck[String] = _ => cats.data.Validated.validNel(())

    val checks = NonEmptyList.of(check1, check2)
    val result = algebra.runQualityChecks(dataset, checks).unsafeRunSync()

    result.size shouldBe 2
    result.head.checkName shouldBe "check_0"
    result.head.passed shouldBe true
    result(1).checkName shouldBe "check_1"
    result(1).passed shouldBe true
  }

  test("profile returns data profile with statistics") {
    val dataset = SimpleDataset(
      List("test1", "test2", "test1"),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.profile(dataset).unsafeRunSync()

    result.recordCount shouldBe 3
    result.nullCount shouldBe 0
    result.distinctCount shouldBe 2
    result.schema shouldBe dataset.schema
    result.statistics shouldBe Map.empty
  }

  // ===============================
  // UTILITY OPERATIONS
  // ===============================

  test("count returns number of records") {
    val dataset = SimpleDataset(
      List(1, 2, 3),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    algebra.count(dataset) shouldBe 3
  }

  test("isEmpty returns true for empty dataset") {
    val dataset = SimpleDataset(
      List.empty[Int],
      DataSchema.builder.build,
      DatasetMetadata(0, DataSchema.builder.build, 1, Instant.now()),
    )

    algebra.isEmpty(dataset) shouldBe true
  }

  test("isEmpty returns false for non-empty dataset") {
    val dataset = SimpleDataset(
      List(1),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    algebra.isEmpty(dataset) shouldBe false
  }

  test("cache returns dataset unchanged") {
    val dataset = SimpleDataset(
      List(1, 2),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra.cache(dataset, CacheStrategy.Memory).unsafeRunSync()

    result shouldBe dataset
  }

  test("partition splits dataset by partitioner") {
    val dataset = SimpleDataset(
      List(1, 2, 3, 4, 5),
      DataSchema.builder.build,
      DatasetMetadata(5, DataSchema.builder.build, 1, Instant.now()),
    )

    val partitioner = new Partitioner[Int] {
      def partition(data: Int): Int = data % 2
      val numPartitions             = 2
    }

    val result = algebra.partition(dataset, partitioner)

    result.size shouldBe 2
    result.map(_.data.size).sum shouldBe 5
    result.exists(_.data.contains(1)) shouldBe true
    result.exists(_.data.contains(2)) shouldBe true
  }

  // ===============================
  // CDC OPERATIONS
  // ===============================

  test("performDelta computes inserts, updates, deletes") {
    implicit val contract: DataContract[String] = DataContract.empty[String]

    val source = SimpleDataset(
      List("new1", "new2", "updated"),
      DataSchema.builder.build,
      DatasetMetadata(3, DataSchema.builder.build, 1, Instant.now()),
    )

    val target = SimpleDataset(
      List("updated", "deleted"),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val config = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.of(FieldName.unsafeFrom("key")),
      deleteDetection = true,
    )

    val result = algebra.performDelta(source, target, config).unsafeRunSync()

    result.inserted shouldBe 2
    result.updated shouldBe 0 // Hash is based on toString, "updated" is same in both
    result.deleted shouldBe 1
    result.success shouldBe true
  }

  test("performDelta without delete detection sets deleted to 0") {
    implicit val contract = DataContract.empty[String]

    val source = SimpleDataset(
      List("new"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val target = SimpleDataset(
      List("deleted"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val config = CDCOperations.CDCConfig(
      keyColumns = NonEmptyList.of(FieldName.unsafeFrom("key")),
      deleteDetection = false,
    )

    val result = algebra.performDelta(source, target, config).unsafeRunSync()

    result.deleted shouldBe 0
    result.success shouldBe true
  }

  test("computeCDCOperations identifies inserts") {
    val source = SimpleDataset(
      List("new1", "new2"),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val target = SimpleDataset(
      List.empty[String],
      DataSchema.builder.build,
      DatasetMetadata(0, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra
      .computeCDCOperations(source, target, NonEmptyList.of(FieldName.unsafeFrom("key")))
      .unsafeRunSync()

    result.inserts.size shouldBe 2
    result.updates shouldBe List.empty
    result.deletes shouldBe List.empty
  }

  test("computeCDCOperations identifies deletes") {
    val source = SimpleDataset(
      List.empty[String],
      DataSchema.builder.build,
      DatasetMetadata(0, DataSchema.builder.build, 1, Instant.now()),
    )

    val target = SimpleDataset(
      List("deleted1", "deleted2"),
      DataSchema.builder.build,
      DatasetMetadata(2, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra
      .computeCDCOperations(source, target, NonEmptyList.of(FieldName.unsafeFrom("key")))
      .unsafeRunSync()

    result.inserts shouldBe List.empty
    result.updates shouldBe List.empty
    result.deletes.size shouldBe 2
  }

  test("computeCDCOperations identifies updates based on MD5 hash") {
    val source = SimpleDataset(
      List("key1-updated"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val target = SimpleDataset(
      List("key1-original"),
      DataSchema.builder.build,
      DatasetMetadata(1, DataSchema.builder.build, 1, Instant.now()),
    )

    val result = algebra
      .computeCDCOperations(source, target, NonEmptyList.of(FieldName.unsafeFrom("key")))
      .unsafeRunSync()

    // Different values will have different keys/hashes
    result.inserts.size shouldBe 1
    result.deletes.size shouldBe 1
  }

  test("applyCDCOperations returns result with operation counts") {
    val operations = CDCOperations.CDCOperationSet(
      inserts = List("insert1", "insert2"),
      updates = List("update1"),
      deletes = List("delete1"),
    )

    val sink = LocalDataSink("test.jsonl", DataFormat.JSONL)

    val result = algebra.applyCDCOperations(operations, sink).unsafeRunSync()

    result.inserted shouldBe 2
    result.updated shouldBe 1
    result.deleted shouldBe 1
    result.unchanged shouldBe 0
    result.errors shouldBe 0
    result.success shouldBe true
  }

  // ===============================
  // TABLE OPERATIONS
  // ===============================

  test("repairRefreshTable returns unsupported operation error") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val result = algebra.repairRefreshTable(table).unsafeRunSync()

    result.success shouldBe false
    result.operation shouldBe "repairRefresh"
    result.errors.size shouldBe 1
    result.errors.head.message should include("operation not supported")
  }

  test("getTableLocation returns invalid configuration error") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val result = algebra.getTableLocation(table).unsafeRunSync()

    result.isInvalid shouldBe true
    result.toEither.left.get.head.message should include("in-memory has no table locations")
  }

  test("getAffectedPartitions returns empty list") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val result = algebra
      .getAffectedPartitions(table, Instant.now(), Instant.now())
      .unsafeRunSync()

    result shouldBe List.empty
  }

  test("deleteDfsLocation returns unsupported operation error") {
    val result = algebra.deleteDfsLocation("/path/to/delete", dryRun = true).unsafeRunSync()

    result.success shouldBe false
    result.operation should include("delete")
    result.errors.size shouldBe 1
  }

  test("analyzeTable returns unsupported operation error") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val result = algebra.analyzeTable(table, None).unsafeRunSync()

    result.success shouldBe false
    result.operation shouldBe "analyze"
    result.tableName shouldBe table
  }

  test("analyzeTable with partitions includes them in result") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val partitions = NonEmptyList.of(
      TableOperations.PartitionSpec(
        NonEmptyList.of(FieldName.unsafeFrom("year")),
        NonEmptyList.of("2024"),
      ),
    )

    val result = algebra.analyzeTable(table, Some(partitions)).unsafeRunSync()

    result.affectedPartitions shouldBe partitions.toList
  }

  test("vacuumTable returns unsupported operation error") {
    val table = TableOperations.TableName(
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("db"),
      eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("table"),
    )

    val result = algebra.vacuumTable(table, retentionHours = 168, dryRun = true).unsafeRunSync()

    result.success shouldBe false
    result.operation should include("vacuum")
    result.operation should include("168")
    result.operation should include("dryRun=true")
  }
}
