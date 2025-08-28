package com.flowforge.core.types

/**
 * Type class instances and implicit conversions for core types.
 */
object TypeInstances {

  /**
   * Implicit conversion from refined types to their underlying values. Enables seamless use of
   * refined types where underlying types are expected.
   */
  implicit def refinedToValue[A, P](refined: Refined[A, P]): A = refined.value

  /**
   * Show instances for pretty printing.
   */
  trait Show[A] {
    def show(a: A): String
  }

  object Show {
    def apply[A](implicit show: Show[A]): Show[A] = show

    implicit val showPipelineId: Show[PipelineId] = (id: PipelineId) => s"PipelineId(${id.value})"
    implicit val showWorkflowId: Show[WorkflowId] = (id: WorkflowId) => s"WorkflowId(${id.value})"
    implicit val showDataSourceId: Show[DataSourceId] = (id: DataSourceId) =>
      s"DataSourceId(${id.value})"
    implicit val showBatchSize: Show[BatchSize] = (bs: BatchSize) => s"BatchSize(${bs.value})"
    implicit val showRecordCount: Show[RecordCount] = (rc: RecordCount) =>
      s"RecordCount(${rc.value})"

    implicit def showRefined[A: Show, P]: Show[Refined[A, P]] =
      (refined: Refined[A, P]) => s"Refined(${Show[A].show(refined.value)})"
  }

  /**
   * Ordering instances for sorting and comparison.
   */
  implicit val orderingPipelineId: Ordering[PipelineId]     = Ordering.by(_.value.toString)
  implicit val orderingWorkflowId: Ordering[WorkflowId]     = Ordering.by(_.value.toString)
  implicit val orderingDataSourceId: Ordering[DataSourceId] = Ordering.by(_.value.toString)
  implicit val orderingBatchSize: Ordering[BatchSize]       = Ordering.by(_.value)
  implicit val orderingRecordCount: Ordering[RecordCount]   = Ordering.by(_.value)
}
