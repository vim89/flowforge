package com.flowforge.core.types

// NOTE: This file uses shapeless HList/LabelledGeneric. It remains temporarily for backward-compat
//       while the Magnolia-based SchemaConforms is rolled out. New code MUST NOT depend on this API.
//       Track removal in ADR: docs/adr/ADR-compile-time-contracts.md

import shapeless.ops.hlist.Intersection
import shapeless.{ HList, LabelledGeneric }

import scala.annotation.implicitNotFound

/**
 * SchemaWitness enforces compile-time contract compliance using phantom types. This is the core mechanism
 * that makes pipelines unbuildable when schema drift occurs.
 *
 * Following FlowForge principles:
 *   - Pure functional approach with immutable witnesses
 *   - Type-level programming for compile-time safety
 *   - Phantom types to encode schema information without runtime overhead
 *   - First-class functions for witness composition
 *
 * The witness requires explicit evidence that pipeline output matches contract schema under the chosen
 * evolution policy. Without this evidence, compilation fails.
 */
@implicitNotFound("""
╔══════════════════════════════════════════════════════════════════════════════╗
║                     🚨 FlowForge Contract Drift Detected! 🚨                 ║
║                                                                              ║
║  Pipeline output type '${PipelineOut}' does not match contract '${Contract}' ║
║  under evolution policy '${Policy}'.                                         ║
║                                                                              ║
║  ❌ This pipeline CANNOT be built due to schema incompatibility.             ║
║                                                                              ║
║  🔧 Common fixes:                                                            ║
║    1. Update case class fields to match contract schema                      ║
║    2. Use BackwardCompatible policy if adding fields is intentional          ║
║    3. Update the contract if schema changes are correct                      ║
║                                                                              ║
║  📖 See: docs/contracts/SCHEMA_EVOLUTION.md                                  ║
╚══════════════════════════════════════════════════════════════════════════════╝
""")
@deprecated(
  "Legacy shapeless/HList-based witness. Migrate to com.flowforge.core.contracts.SchemaConforms for compile-time contracts. Will be removed in 1.0.0.",
  since = "0.9.0",
)
sealed trait SchemaWitness[PipelineOut, Contract, Policy <: SchemaEvolutionPolicy]

object SchemaWitness {

  // Type alias for migration - use SchemaConforms instead
  @deprecated("Use SchemaConforms[Out, Contract, P] instead", "0.9.0")
  type Witness[Out, Contract, P <: SchemaEvolutionPolicy] = SchemaWitness[Out, Contract, P]

  /**
   * Exact match witness using pure functional composition. Requires identical field names, types, and order
   * between pipeline output and contract.
   */
  implicit def exactWitness[PipelineOut, Contract, SchemaRepr <: HList](
    implicit
    pipelineGen: LabelledGeneric.Aux[PipelineOut, SchemaRepr],
    contractGen: LabelledGeneric.Aux[Contract, SchemaRepr],
  ): SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.Exact] =
    new SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.Exact] {}

  /**
   * Backward compatible witness using higher-order type constraints. Pipeline output can have additional
   * fields beyond contract requirements.
   */
  implicit def backwardCompatibleWitness[PipelineOut, Contract, PipelineRepr <: HList, ContractRepr <: HList](
    implicit
    pipelineGen: LabelledGeneric.Aux[PipelineOut, PipelineRepr],
    contractGen: LabelledGeneric.Aux[Contract, ContractRepr],
    subsetEvidence: SubsetSchema[ContractRepr, PipelineRepr],
  ): SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.BackwardCompatible] =
    new SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.BackwardCompatible] {}

  /**
   * Forward compatible witness using type-level programming. Pipeline output can have fewer fields than
   * contract specifies.
   */
  implicit def forwardCompatibleWitness[PipelineOut, Contract, PipelineRepr <: HList, ContractRepr <: HList](
    implicit
    pipelineGen: LabelledGeneric.Aux[PipelineOut, PipelineRepr],
    contractGen: LabelledGeneric.Aux[Contract, ContractRepr],
    subsetEvidence: SubsetSchema[PipelineRepr, ContractRepr],
  ): SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.ForwardCompatible] =
    new SchemaWitness[PipelineOut, Contract, SchemaEvolutionPolicy.ForwardCompatible] {}
}

/**
 * Schema evolution policies using ADT pattern for type safety. Each policy encodes different compatibility
 * rules at the type level.
 */
sealed trait SchemaEvolutionPolicy extends Product with Serializable
object SchemaEvolutionPolicy {
  case object Exact              extends SchemaEvolutionPolicy
  case object BackwardCompatible extends SchemaEvolutionPolicy
  case object ForwardCompatible  extends SchemaEvolutionPolicy
  case object FullCompatible     extends SchemaEvolutionPolicy

  // Type aliases for phantom type constraints
  type Exact              = Exact.type
  type BackwardCompatible = BackwardCompatible.type
  type ForwardCompatible  = ForwardCompatible.type
  type FullCompatible     = FullCompatible.type
}

/**
 * Type class for subset schema validation using shapeless operations. Provides evidence that one schema is a
 * subset of another.
 */
@implicitNotFound("""
╔════════════════════════════════════════════════════════════════════════════════╗
║                    🔍 Schema Subset Validation Failed 🔍                      ║
║                                                                                ║
║  Schema '${Subset}' is not a valid subset of '${Superset}'.                   ║
║                                                                                ║
║  💡 This means some fields in the subset schema cannot be found               ║
║     in the superset schema with matching types.                               ║
║                                                                                ║
║  🔧 Fix: Ensure all fields in subset exist in superset with same types.      ║
╚════════════════════════════════════════════════════════════════════════════════╝
""")
trait SubsetSchema[Subset <: HList, Superset <: HList]

object SubsetSchema {

  /**
   * Pure functional evidence for subset relationship using shapeless intersection. If R1 intersected with R2
   * equals R1, then R1 is a subset of R2.
   */
  implicit def subsetEvidence[Subset <: HList, Superset <: HList](
    implicit intersectionEvidence: Intersection.Aux[Subset, Superset, Subset],
  ): SubsetSchema[Subset, Superset] = new SubsetSchema[Subset, Superset] {}
}

