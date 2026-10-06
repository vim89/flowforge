package com.flowforge.engines.spark

import com.flowforge.core.algebra.{ DataDecoder, DataEncoder, EncodedData }
import com.flowforge.core.types.DataFormat
import org.apache.spark.sql.{ DataFrame, Dataset, SparkSession }

import java.nio.charset.StandardCharsets

/**
 * Record-level operations run inside the frame rather than on the driver.
 *
 * A FlowForge operation is written against `A`, and a Spark frame holds rows. JSON is the one representation
 * both sides already speak: `DataDecoder` and `DataEncoder` are defined over it, and a frame renders to it
 * and reads back from it. So each operation here is the same three steps - render rows to JSON, apply the
 * caller's function to the decoded record, read the result back as a frame - and the only thing that differs
 * between them is that function.
 *
 * Every function in this object is a calculation: a frame in, a frame out, no session state changed. The
 * caller's function and the codecs travel to the workers, which is why `DataDecoder` and `DataEncoder` are
 * `Serializable`.
 *
 * A record the decoder rejects is dropped. That matches what the sample path did before, and a pure operation
 * that returns a `Dataset` has nowhere to report the failure.
 */
private[spark] object SparkFrameOps {

  /** Decode one JSON row. `None` when the decoder rejects it. */
  def decodeRow[A](json: String, decoder: DataDecoder[A]): Option[A] =
    decoder
      .decode(EncodedData(json.getBytes(StandardCharsets.UTF_8), DataFormat.JSON), DataFormat.JSON)
      .toOption

  /** Encode one record as a JSON row. `None` when the encoder rejects it. */
  def encodeRow[A](value: A, encoder: DataEncoder[A]): Option[String] =
    encoder.encode(value, DataFormat.JSON).toOption.map(ed => new String(ed.data, StandardCharsets.UTF_8))

  /**
   * Keep the rows whose decoded record satisfies the predicate.
   *
   * The result is read back under the input frame's own schema, so the columns, their types and their
   * nullability are the ones the caller started with. Inferring a schema from the kept rows instead would let
   * a filter change the shape of what a later write produces.
   */
  def filter[A](
    spark: SparkSession,
    frame: DataFrame,
    predicate: A => Boolean,
  )(implicit decoder: DataDecoder[A],
  ): DataFrame = {
    val rowDecoder = decoder
    val kept       = frame.toJSON.filter((json: String) => decodeRow(json, rowDecoder).exists(predicate))
    spark.read.schema(frame.schema).json(kept)
  }

  /**
   * Apply `f` to every decoded record and read the results back as a frame.
   *
   * The result schema is inferred from the encoded results, because `B` need not have the shape of the input
   * frame. An empty result therefore has an empty schema.
   */
  def mapRows[A, B](
    spark: SparkSession,
    frame: DataFrame,
    f: A => List[B],
  )(implicit
    decoder: DataDecoder[A],
    encoder: DataEncoder[B],
  ): DataFrame = {
    import spark.implicits._
    val rowDecoder = decoder
    val rowEncoder = encoder
    val encoded: Dataset[String] = frame.toJSON.flatMap { json =>
      decodeRow(json, rowDecoder).toList.flatMap(a => f(a).flatMap(b => encodeRow(b, rowEncoder)))
    }
    spark.read.json(encoded)
  }

  /**
   * How many distinct violation messages [[collectViolations]] brings back to the driver. A message built
   * from a record's own values is otherwise as large as the dataset.
   */
  val violationMessageLimit: Int = 1000

  /**
   * Apply every check to every decoded record.
   *
   * Each element of `checks` maps a record to the messages for the violations it has, and the index of a
   * check in that list identifies it in the result.
   *
   * The two halves of the result are collected separately on purpose. The index set is exact, so a check can
   * never look like it passed because its message was dropped. The messages are bounded, so a dataset of
   * violations cannot fill the driver.
   */
  def collectViolations[A](
    spark: SparkSession,
    frame: DataFrame,
    checks: List[A => List[String]],
    messageLimit: Int = violationMessageLimit,
  )(implicit decoder: DataDecoder[A],
  ): (Set[Int], List[(Int, String)]) = {
    import spark.implicits._
    val rowDecoder = decoder
    val indexed    = checks.zipWithIndex
    val perRecord = frame.toJSON.flatMap { json =>
      decodeRow(json, rowDecoder).toList.flatMap { a =>
        indexed.flatMap { case (check, idx) => check(a).map(message => (idx, message)) }
      }
    }
    val violations = perRecord.distinct()
    val failed     = violations.map(_._1).distinct().collect().toSet
    val messages   = violations.limit(messageLimit).collect().toList
    (failed, messages)
  }
}
