package com.flowforge.core.types

/**
 * Phantom types to track builder completeness at compile time. These types exist only during compilation and
 * have no runtime overhead.
 *
 * Following the 100% compile-time contracts specification:
 *   - Pipelines cannot be built without all required stages
 *   - Each stage must be added with proper contract evidence
 *   - Build method only available when all stages present
 */
sealed trait BuilderState

// Individual phantom state markers
sealed trait HasSource    extends BuilderState
sealed trait HasContract  extends BuilderState
sealed trait HasTransform extends BuilderState
sealed trait HasSink      extends BuilderState

// Convenience type aliases for common combinations
object BuilderState {
  type Empty         = BuilderState
  type WithSource    = HasSource
  type WithContract  = HasSource with HasContract
  type WithTransform = HasSource with HasContract with HasTransform
  type Complete      = HasSource with HasContract with HasTransform with HasSink
}
