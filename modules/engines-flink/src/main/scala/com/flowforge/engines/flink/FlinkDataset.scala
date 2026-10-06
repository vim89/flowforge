package com.flowforge.engines.flink

import com.flowforge.core.algebra.{ DataAlgebra, DataDecoder }
import com.flowforge.core.types.{ DataSchema, DataSource }

import java.time.Instant

/**
 * A dataset this engine produced: a plan for the records, plus a sample of them.
 *
 * Nothing here holds the dataset. `DataAlgebra.Dataset` requires `data: List[A]`, so a Flink-backed dataset
 * has to answer that question with something, and the honest answer is a bounded sample. `size`, `isEmpty`
 * and the algebra's `count` answer from the plan instead, so a caller that wants a fact about the whole
 * dataset gets one.
 *
 * The decoder the records were read with is carried along, because the plan holds JSON rows and every
 * operation that has to see a record has to decode one. `DataAlgebra.write` is handed an encoder and no
 * decoder, so without this field the engine could not re-encode a row for a sink.
 *
 * @param sampleData
 *   at most [[FlinkDataset.sampleSize]] records, for the `data` the algebra's interface asks for
 * @param plan
 *   how to rebuild the stream of every record, in a fresh environment
 */
final case class FlinkDataset[A](
  sampleData: List[A],
  plan: FlinkPlan,
  decoder: DataDecoder[A],
  schema: DataSchema,
  metadata: DataAlgebra.DatasetMetadata)
    extends DataAlgebra.Dataset[A] {

  /** A sample, not the dataset. See the class comment. */
  override def data: List[A] = sampleData

  /**
   * The whole dataset's record count, not the sample's.
   *
   * It is established once, when the dataset is built, so asking again starts no job. `Int` is the
   * interface's type, so a dataset of more than `Int.MaxValue` records overflows it; the algebra's `count`
   * returns the same number as a `Long`.
   */
  override def size: Int = metadata.recordCount.toInt

  override def isEmpty: Boolean = metadata.recordCount == 0L
}

object FlinkDataset {

  /** How many records the driver keeps, matching what the Spark engine samples. */
  val sampleSize: Int = 100

  /**
   * Build a dataset around a plan.
   *
   * One job runs, for the sample. The record count comes from the sample when the sample turned out to be the
   * whole dataset: collecting fewer rows than the limit means there were no more to collect, so a second job
   * would only confirm a number already in hand. A dataset larger than the sample pays for that second job.
   *
   * @param source
   *   where the records came from, which a plan does not carry
   */
  def fromPlan[A](plan: FlinkPlan, source: Option[DataSource] = None)(implicit
    decoder: DataDecoder[A],
  ): FlinkDataset[A] = {
    val sampleRows = FlinkStreamOps.collect(plan, sampleSize)
    val sample     = sampleRows.flatMap(row => FlinkRows.decode(row, decoder))
    val recordCount =
      if (sampleRows.sizeIs < sampleSize) sampleRows.size.toLong else FlinkStreamOps.count(plan)
    val schema = FlinkRows.schemaOf(sampleRows)

    FlinkDataset(
      sampleData = sample,
      plan = plan,
      decoder = decoder,
      schema = schema,
      metadata = DataAlgebra.DatasetMetadata(
        recordCount = recordCount,
        schema = schema,
        partitions = 1,
        createdAt = Instant.now(),
        source = source,
      ),
    )
  }

  /**
   * Rebuild a dataset around the plan an operation produced.
   *
   * The sample, the schema and the record count all describe what the dataset now holds rather than what it
   * held before the operation. `metadata.source` is carried over, because it names where the records came
   * from and an operation does not change that.
   */
  def withPlan[A: DataDecoder](base: FlinkDataset[A], plan: FlinkPlan): FlinkDataset[A] =
    fromPlan[A](plan, base.metadata.source)
}
