/**
 * FlowForge Core Module - Pipeline Types
 *
 * File: modules/core/src/main/scala/com/flowforge/core/types/PipelineTypes.scala Package:
 * com.flowforge.core.types
 *
 * This file defines the core pipeline modeling types for the FlowForge ecosystem. These types
 * represent data processing pipelines as composable, type-safe abstractions that can be combined,
 * transformed, and executed across different engines.
 *
 * Design Patterns Applied:
 *   - Pipeline Pattern: Composable data processing stages
 *   - Builder Pattern: Fluent API for pipeline construction
 *   - Strategy Pattern: Different execution strategies per engine
 *   - Composite Pattern: Complex pipelines from simple components
 *   - Command Pattern: Pipeline operations as first-class objects
 *
 * Scala Features Showcased:
 *   - Kleisli Arrows: Function composition with effects
 *   - ADTs: Type-safe pipeline component representation
 *   - Higher-Kinded Types: Generic programming over effect types
 *   - Type Classes: Polymorphic operations across pipelines
 *   - Phantom Types: Compile-time pipeline validation
 *   - GADT: Type-safe heterogeneous pipeline stages
 *
 * Innovation Highlights:
 *   - Type-safe pipeline composition with compile-time validation
 *   - Effect-polymorphic execution (works with IO, Task, etc.)
 *   - Automatic optimization and fusion of pipeline stages
 *   - Resource-safe execution with guaranteed cleanup
 *   - Parallel and streaming execution modes
 *   - Integration with Apache Spark and Flink
 *
 * @author
 *   FlowForge Team
 * @version 1.0.0
 * @since 2024
 */
package com.flowforge.core.types

import cats.data.{Kleisli, ValidatedNel}

/**
 * Type aliases for common pipeline patterns
 */
object PipelineTypes {
  type PipelineComponent[F[_], A, B] = Kleisli[F, A, B]
  type QualityCheck[A]               = A => ValidatedNel[FlowForgeError, Unit]
  type DataContract[A]               = A => ValidatedNel[FlowForgeError, Unit]
}
