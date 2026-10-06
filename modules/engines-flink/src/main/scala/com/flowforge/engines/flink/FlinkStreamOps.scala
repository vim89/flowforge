package com.flowforge.engines.flink

import com.flowforge.core.algebra.{ DataDecoder, DataEncoder }
import com.flowforge.core.types.DataFormat
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.{ FilterFunction, FlatMapFunction }
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.api.java.typeutils.TupleTypeInfo
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

/**
 * Record-level operations run inside a Flink job rather than on the driver, and the plans that describe them.
 *
 * The plan combinators are calculations: a [[FlinkPlan]] in, a plan out, no job submitted. The four methods
 * below them are the only actions in this module, and each one builds its own environment, applies the plan
 * and runs exactly one job.
 *
 * The caller's function and the codecs travel to the task that runs them, which is why `DataDecoder` and
 * `DataEncoder` are `Serializable` and why the function classes here are named rather than anonymous: a named
 * top-level class holds only the fields it declares, so there is no enclosing instance to serialize.
 *
 * A record the decoder rejects is dropped, for the same reason it is in `SparkFrameOps`: a pure operation that
 * returns a `Dataset` has nowhere to report the failure.
 */
private[flink] object FlinkStreamOps {

  /** How many distinct keys [[countKeys]] brings back. See [[violationMessageLimit]]. */
  val violationMessageLimit: Int = 1000

  /** The type of the `(key, count)` pair the counting job carries. Flink needs it stated, not inferred. */
  private val pairType: TupleTypeInfo[Tuple2[String, java.lang.Long]] =
    new TupleTypeInfo[Tuple2[String, java.lang.Long]](Types.STRING, Types.LONG)

  // ---------- Plans: calculations ----------

  /** Read a file of JSON rows, one record per line. */
  @nowarn("cat=deprecation")
  def readJsonLines(path: String): FlinkPlan = env => env.readTextFile(path)

  /**
   * Read a CSV file as JSON rows, under the given column names.
   *
   * The header is a driver-side fact: it has to be known before the job is built, because it names the fields
   * every row will carry. The job itself still reads the whole file.
   */
  @nowarn("cat=deprecation")
  def readCsv(path: String, header: List[String]): FlinkPlan =
    env => env.readTextFile(path).flatMap(new CsvLineToJson(header), Types.STRING)

  /** Keep the rows whose decoded record satisfies the predicate. */
  def filter[A](plan: FlinkPlan, predicate: A => Boolean)(implicit decoder: DataDecoder[A]): FlinkPlan = {
    val function = new DecodedFilter[A](decoder, predicate)
    env => plan(env).filter(function)
  }

  /** Apply `f` to every decoded record and carry the results on as JSON rows. */
  def mapRows[A, B](plan: FlinkPlan, f: A => List[B])(implicit
    decoder: DataDecoder[A],
    encoder: DataEncoder[B],
  ): FlinkPlan = {
    val function = new DecodedFlatMap[A, B](decoder, encoder, f)
    env => plan(env).flatMap(function, Types.STRING)
  }

  /** Both plans build in the one environment, so their streams can be joined into a single stream. */
  def union(left: FlinkPlan, right: FlinkPlan): FlinkPlan = env => left(env).union(right(env))

  /** Re-encode every record in `format`, for a sink that wants something other than the plan's JSON. */
  def encodeRows[A](plan: FlinkPlan, format: DataFormat)(implicit
    decoder: DataDecoder[A],
    encoder: DataEncoder[A],
  ): FlinkPlan = {
    val function = new ReEncode[A](decoder, encoder, format)
    env => plan(env).flatMap(function, Types.STRING)
  }

  // ---------- Jobs: actions ----------

  /**
   * A fresh environment in batch mode.
   *
   * Every source this engine reads is a bounded file, and batch mode is what makes the aggregation in
   * [[countKeys]] report one final count per key instead of a running count per element. The trade is that
   * nothing here can consume an unbounded source; this engine runs Flink batch jobs, not Flink streaming.
   */
  private def batchEnv(): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    env
  }

  /** Run the plan and bring back at most `limit` rows. */
  def collect(plan: FlinkPlan, limit: Int): List[String] =
    plan(batchEnv()).executeAndCollect(limit).asScala.toList

  /**
   * Run the plan and count its rows.
   *
   * The rows are counted as they arrive and never held, so this is bounded whatever the dataset's size.
   */
  def count(plan: FlinkPlan): Long = {
    val rows = plan(batchEnv()).executeAndCollect()
    try rows.asScala.length.toLong
    finally rows.close()
  }

  /**
   * Count the records behind each distinct key the caller's function reports.
   *
   * A key is counted once per record: a record that reports the same key twice has still broken that rule
   * once, which is what the count claims to say.
   *
   * At most `limit` keys come back, because a key built from a record's own values is otherwise as numerous as
   * the dataset. Which keys survive the cap is not specified. The Spark engine orders by key before capping,
   * so it truncates deterministically; a `DataStream` has no cheap global sort, so this does not. A caller
   * that needs an exact answer over an unbounded key set needs a count, not a key list, and [[count]] gives
   * one.
   */
  def countKeys[A](
    plan: FlinkPlan,
    keysOf: A => List[String],
    limit: Int = violationMessageLimit,
  )(implicit decoder: DataDecoder[A],
  ): List[(String, Long)] = {
    val perRecord = new KeysOfRecord[A](decoder, keysOf)
    val counted = plan(batchEnv())
      .flatMap(perRecord, pairType)
      .keyBy(new FirstField, Types.STRING)
      .sum(1)
    counted
      .executeAndCollect(limit)
      .asScala
      .toList
      .map(pair => (pair.f0, pair.f1.longValue()))
      .sortBy(_._1)
  }

  /**
   * Run the plan and write every row to `path` as one line of text.
   *
   * The sink runs at parallelism one, so `path` is the file the caller named rather than a directory of part
   * files. That serializes the write; a sink that fans out cannot also promise a single named file.
   */
  @nowarn("cat=deprecation")
  def writeLines(plan: FlinkPlan, path: String): Unit = {
    val env = batchEnv()
    plan(env).writeAsText(path, FileSystem.WriteMode.OVERWRITE).setParallelism(1)
    env.execute(s"flowforge-write-$path")
    ()
  }
}

/** Keeps the rows whose decoded record satisfies `predicate`. */
private[flink] final class DecodedFilter[A](decoder: DataDecoder[A], predicate: A => Boolean)
    extends FilterFunction[String] {
  override def filter(json: String): Boolean = FlinkRows.decode(json, decoder).exists(predicate)
}

/** Applies `f` to each decoded record and emits the results as JSON rows. */
private[flink] final class DecodedFlatMap[A, B](
  decoder: DataDecoder[A],
  encoder: DataEncoder[B],
  f: A => List[B])
    extends FlatMapFunction[String, String] {

  override def flatMap(json: String, out: Collector[String]): Unit =
    FlinkRows.decode(json, decoder).foreach { a =>
      f(a).foreach(b => FlinkRows.encode(b, encoder, DataFormat.JSON).foreach(out.collect))
    }
}

/** Re-encodes each row in `format`, leaving the record itself alone. */
private[flink] final class ReEncode[A](decoder: DataDecoder[A], encoder: DataEncoder[A], format: DataFormat)
    extends FlatMapFunction[String, String] {

  override def flatMap(json: String, out: Collector[String]): Unit =
    FlinkRows.decode(json, decoder).foreach(a => FlinkRows.encode(a, encoder, format).foreach(out.collect))
}

/** Turns one CSV line into one JSON row. Emits nothing for the header line and for a blank line. */
private[flink] final class CsvLineToJson(header: List[String]) extends FlatMapFunction[String, String] {
  override def flatMap(line: String, out: Collector[String]): Unit =
    FlinkRows.csvLineToJson(header, line).foreach(out.collect)
}

/** Emits each distinct key one record reports, paired with a count of one for the sum downstream. */
private[flink] final class KeysOfRecord[A](decoder: DataDecoder[A], keysOf: A => List[String])
    extends FlatMapFunction[String, Tuple2[String, java.lang.Long]] {

  override def flatMap(json: String, out: Collector[Tuple2[String, java.lang.Long]]): Unit =
    FlinkRows.decode(json, decoder).foreach { a =>
      keysOf(a).distinct.foreach(key => out.collect(Tuple2.of(key, java.lang.Long.valueOf(1L))))
    }
}

/** The key of a `(key, count)` pair. */
private[flink] final class FirstField extends KeySelector[Tuple2[String, java.lang.Long], String] {
  override def getKey(pair: Tuple2[String, java.lang.Long]): String = pair.f0
}
