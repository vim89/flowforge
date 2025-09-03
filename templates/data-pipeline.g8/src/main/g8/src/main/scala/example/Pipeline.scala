package example

import cats.effect.{IO, IOApp}
import java.nio.file.Files

/**
  * Runnable Spark local demo (no FlowForge dependency required).
  * Demonstrates Delta table creation and a simple SCD2-like merge pattern.
  * Run with: sbt run
  */
object Pipeline extends IOApp.Simple {
  def run: IO[Unit] = IO.blocking {
    import org.apache.spark.sql.SparkSession
    import org.apache.spark.sql.functions._

    val spark = SparkSession
      .builder()
      .appName("$name$-demo")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
      .getOrCreate()

    val src = Files.createTempDirectory("demo-src").toFile.getAbsolutePath
    val tgt = Files.createTempDirectory("demo-tgt").toFile.getAbsolutePath

    import spark.implicits._

    case class Rec(id: Long, v: String, ts: Long)

    // Batch 1: insert one key
    val t1 = System.currentTimeMillis()
    Seq(Rec(1L, "a", t1)).toDF.write.format("delta").mode("overwrite").save(src)
    spark.emptyDataFrame.write.format("delta").mode("overwrite").save(tgt)

    val res1 = runScd2Merge(spark, src, tgt, keys = Seq("id"), tsCol = Some("ts"))
    println(s"First merge: $res1")

    // Batch 2: update same key
    val t2 = t1 + 1000
    Seq(Rec(1L, "b", t2)).toDF.write.format("delta").mode("overwrite").save(src)
    val res2 = runScd2Merge(spark, src, tgt, keys = Seq("id"), tsCol = Some("ts"))
    println(s"Second merge: $res2")

    val tgtDf = spark.read.format("delta").load(tgt)
    tgtDf.show(false)
    println(s"Current rows: $${tgtDf.filter("is_current = true").count()} | Closed rows: $${tgtDf.filter("is_current = false").count()}")

    spark.stop()
  }

  private def runScd2Merge(
    spark: org.apache.spark.sql.SparkSession,
    sourcePath: String,
    targetPath: String,
    keys: Seq[String],
    tsCol: Option[String]
  ): String = {
    import io.delta.tables.DeltaTable
    import org.apache.spark.sql.functions._

    val src = spark.read.format("delta").load(sourcePath).alias("source")
    val tgt = DeltaTable.forPath(spark, targetPath)
    val tgtDf = tgt.toDF.alias("target")

    val scdFrom = "effective_from"
    val scdTo   = "effective_to"
    val scdCur  = "is_current"

    val tgtHas = tgtDf.columns.toSet
    val missing = Seq(scdFrom, scdTo, scdCur).filterNot(tgtHas.contains)
    if (missing.nonEmpty && tgtDf.limit(1).count() == 0) {
      val addCols = missing.map {
        case c if c == scdCur => s"$c BOOLEAN"
        case c                => s"$c TIMESTAMP"
      }.mkString(", ")
      spark.sql(s"ALTER TABLE delta.`$targetPath` ADD COLUMNS ($addCols)")
    }

    val cond = keys.map(k => s"target.$k = source.$k").mkString(" AND ")

    val tgtCurrent = tgtDf.filter(col(scdCur) === lit(true) || col(scdTo).isNull)
    val changed = src.join(tgtCurrent, expr(cond), "inner")
      .filter(expr("hash(source.*) <> hash(target.*)"))
      .select("source.*")
    val insertedNew = src.join(tgtDf, expr(cond), "left_anti")
    val toInsert0 = changed.unionByName(insertedNew, allowMissingColumns = true)
    val toInsert = tsCol.filter(src.columns.contains).map(c => toInsert0.withColumn(scdFrom, col(c).cast("timestamp"))).getOrElse(toInsert0.withColumn(scdFrom, current_timestamp()))
      .withColumn(scdTo, lit(null).cast("timestamp")).withColumn(scdCur, lit(true))

    // Close current
    tgt.as("target").merge(changed.as("source"), cond).whenMatched().updateExpr(Map(
      scdTo -> "current_timestamp()",
      scdCur -> "false"
    )).execute()

    toInsert.write.format("delta").mode("append").save(targetPath)
    s"ok: inserted=$${toInsert.count()}"
  }
}
