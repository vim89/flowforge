package com.flowforge.core.types

import cats.data.Kleisli
import com.flowforge.core.algebra.EffectSystem
import eu.timepit.refined.api.Refined
import shapeless.{ HList, LabelledGeneric }

/**
 * A small, non-invasive typed builder prototype that enforces stage chaining at compile-time. It
 * lives alongside the legacy PipelineBuilder so you can migrate consumers incrementally.
 *
 * Usage example (compile-time safety): val builder = PipelineBuilder2[F].apply("typed")
 * .addSource[Int](mySource, reader) .addTransform[String](i => F.pure(i.toString)) .addSink(mySink,
 * (s, sink) => F.pure(())) val pipeline: Pipeline[F, Unit, Unit] = builder.build()
 */
case class PipelineBuilder2[F[_]: EffectSystem, In, Out] private (
  name: String,
  description: String = "",
  stages: List[PipelineStage[F, _, _]] = List.empty,
  config: Option[PipelineConfig] = None
) {

  // Note: addFilterSkip changes the pipeline's Out to Option[Out]. Downstream transforms must handle Option values, e.g. .addTransform[U]{
  //   case Some(v) => F.pure(doWork(v))
  //   case None => F.pure(default)
  // }
  def addFilterSkip(predicate: Out => Boolean): PipelineBuilder2[F, In, Option[Out]] = {
    val F = implicitly[EffectSystem[F]]
    val stage = PipelineStage.Transform[F, Out, Option[Out]](
      name = s"typed-filter-skip-${stages.size}",
      description = "Typed filter (skip semantics)",
      execute = Kleisli((b: Out) => if (predicate(b)) F.pure(Some(b)) else F.pure(None))
    )
    PipelineBuilder2[F, In, Option[Out]](name, description, stages :+ stage, config)
  }

  def withDescription(desc: String): PipelineBuilder2[F, In, Out] = copy(description = desc)
  def withConfig(c: PipelineConfig): PipelineBuilder2[F, In, Out] = copy(config = Some(c))

  // Untyped source removed: typed-only builder

  /**
   * Add source with compile-time schema evidence for the produced type `C`.
   */
  def addTypedSource[C, R <: HList](
    source: DataSource,
    reader: DataSource => F[C]
  )(implicit L: LabelledGeneric.Aux[C, R], eq: SchemaEq[C, R]): PipelineBuilder2[F, Unit, C] = {
    val stage = PipelineStage.Source[F, C](
      name = s"typed-source-${stages.size}",
      description = s"Read from ${source.format} (typed)",
      dataSource = source,
      execute = Kleisli(_ => reader(source))
    )
    PipelineBuilder2[F, Unit, C](name, description, stages :+ stage, config)
  }

  /** Overload that accepts a TypedSource wrapper for ergonomics. */
  def addTypedSource[C, R <: HList](
    source: TypedSource[R],
    reader: DataSource => F[C]
  )(implicit L: LabelledGeneric.Aux[C, R], eq: SchemaEq[C, R]): PipelineBuilder2[F, Unit, C] =
    addTypedSource[C, R](source.underlying, reader)

  def addTransform[C](transform: Out => F[C]): PipelineBuilder2[F, In, C] = {
    val stage = PipelineStage.Transform[F, Out, C](
      name = s"typed-transform-${stages.size}",
      description = "Typed transformation",
      execute = Kleisli(transform)
    )
    PipelineBuilder2[F, In, C](name, description, stages :+ stage, config)
  }

  def addFilter(predicate: Out => Boolean): PipelineBuilder2[F, In, Out] = {
    val F = implicitly[EffectSystem[F]]
    val stage = PipelineStage.Filter[F, Out](
      name = s"typed-filter-${stages.size}",
      description = "Typed filter",
      predicate = predicate,
      execute = Kleisli[F, Out, Out](a =>
        if (predicate(a)) F.pure(a) else F.raiseError(new RuntimeException("Filtered"))
      )
    )
    PipelineBuilder2[F, In, Out](name, description, stages :+ stage, config)
  }

  /**
   * Unwraps Option[Out] into Out, failing if None.
   */
  def compact[C](implicit ev: Out <:< Option[C]): PipelineBuilder2[F, In, C] = {
    val F = implicitly[EffectSystem[F]]
    val stage = PipelineStage.Transform[F, Out, C](
      name = s"typed-compact-${stages.size}",
      description = "Unwrap Option, fail if None",
      execute = Kleisli[F, Out, C](opt =>
        ev(opt) match {
          case Some(value) => F.pure(value)
          case None        => F.raiseError(new RuntimeException("Option was None in compact"))
        }
      )
    )
    PipelineBuilder2[F, In, C](name, description, stages :+ stage, config)
  }

  /**
   * Applies a PartialFunction[Out, C], failing if not defined.
   */
  def collect[C](pf: PartialFunction[Out, C]): PipelineBuilder2[F, In, C] = {
    val F = implicitly[EffectSystem[F]]
    val stage = PipelineStage.Transform[F, Out, C](
      name = s"typed-collect-${stages.size}",
      description = "Collect with PartialFunction, fail if not matched",
      execute = Kleisli[F, Out, C](out =>
        if (pf.isDefinedAt(out)) F.pure(pf(out))
        else F.raiseError(new RuntimeException("PartialFunction not defined for input in collect"))
      )
    )
    PipelineBuilder2[F, In, C](name, description, stages :+ stage, config)
  }

  // Untyped sink removed: typed-only builder

  /**
   * Add a sink with compile-time schema enforcement.
   *
   * Requires evidence that the pipeline's current output type `Out` has a labelled-generic
   * representation `R` that matches the typed sink's expected schema. If they do not match, this
   * method cannot be called (compilation fails).
   */
  def addTypedSink[R <: HList](
    sink: TypedSink[R],
    writer: (Out, DataSink) => F[Unit]
  )(implicit
    eq: SchemaEq[Out, R],
    L: LabelledGeneric.Aux[Out, R]
  ): PipelineBuilder2[F, In, Unit] = {
    val stage = PipelineStage.Sink[F, Out](
      name = s"typed-sink-${stages.size}",
      description = s"Write to ${sink.underlying.format} (typed)",
      dataSink = sink.underlying,
      execute = Kleisli((b: Out) => writer(b, sink.underlying))
    )
    PipelineBuilder2[F, In, Unit](name, description, stages :+ stage, config)
  }

  /** Policy-driven typed sink: Exact (default), Superset, or Subset. */
  def addTypedSinkWithPolicy[R <: HList, P <: SchemaPolicy](
    sink: TypedSink[R],
    writer: (Out, DataSink) => F[Unit]
  )(implicit
    conf: SchemaConforms[Out, R, P]
  ): PipelineBuilder2[F, In, Unit] = {
    val stage = PipelineStage.Sink[F, Out](
      name = s"typed-sink-${stages.size}",
      description = s"Write to ${sink.underlying.format} (typed, policy)",
      dataSink = sink.underlying,
      execute = Kleisli((b: Out) => writer(b, sink.underlying))
    )
    PipelineBuilder2[F, In, Unit](name, description, stages :+ stage, config)
  }

  /** Convenience for order-insensitive exact matching of fields and types. */
  def addTypedSinkExactUnordered[R <: HList](
    sink: TypedSink[R],
    writer: (Out, DataSink) => F[Unit]
  )(implicit
    conf: SchemaConforms[Out, R, SchemaPolicy.ExactUnordered]
  ): PipelineBuilder2[F, In, Unit] =
    addTypedSinkWithPolicy[R, SchemaPolicy.ExactUnordered](sink, writer)

  // build returns a typed Pipeline[F, In, Out]
  def build(): Pipeline[F, In, Out] = {

    val defaultConfig = config.getOrElse(
      PipelineConfig(
        name = Refined.unsafeApply(if (name.nonEmpty) name else "default"),
        version = "1.0.0",
        environment = Environment.Development,
        source = stages.collectFirst { case s: PipelineStage.Source[F, _] => s.dataSource }
          .getOrElse(DataSource.gcs("default", "default", DataFormat.Parquet)),
        sink = stages.collectFirst { case s: PipelineStage.Sink[F, _] => s.dataSink }
          .getOrElse(DataSink.gcs("default", "default", DataFormat.Parquet))
      )
    )
    Pipeline[F, In, Out](
      name = name,
      description = description,
      stages = stages,
      config = defaultConfig
    )
  }
}

object PipelineBuilder2 {
  def apply[F[_]: EffectSystem](name: String): PipelineBuilder2[F, Unit, Unit] =
    PipelineBuilder2[F, Unit, Unit](name)
}
