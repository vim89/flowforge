package com.flowforge.framework

import cats.data.Validated
import cats.syntax.all._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types.{ FlowForgeError, PipelineStage, PipelineBuilder2, BusinessError }
import com.flowforge.contracts.{ ContractViolation, DataContract }
import shapeless.{ HList, LabelledGeneric }

/**
 * Typed contract wrapper encodes a runtime DataContract together with a type-level schema R for A.
 * If A does not match R (via LabelledGeneric), this value cannot be constructed by the compiler.
 */
final case class TypedContract[A, R <: HList](dc: DataContract[A])(implicit
  val ev: LabelledGeneric.Aux[A, R]
)

object TypedContractsSyntax {

  /** PipelineBuilder2 syntax to attach a typed contract stage. Enforces at compile time that
    * Out’s labelled-generic representation matches the contract’s type-level schema R.
    */
  implicit final class PipelineBuilder2ContractsOps[F[_], In, Out](
    private val b: PipelineBuilder2[F, In, Out]
  ) extends AnyVal {

    def contractTyped[R <: HList](
      tc: TypedContract[Out, R]
    )(implicit F: EffectSystem[F]): PipelineBuilder2[F, In, Out] = {
      val stage = PipelineStage.Transform[F, Out, Out](
        name = s"typed-contract-${b.stages.size}",
        description = "Compile-time aligned contract validation",
        execute = cats.data.Kleisli { out: Out =>
          tc.dc.validate(out) match {
            case Validated.Valid(_) => F.pure(out)
            case Validated.Invalid(nel) =>
              val msg  = nel.toList.map(_.message).mkString("; ")
              val err0 = nel.head
              val err: FlowForgeError = BusinessError.DataContractViolation(
                contractName = tc.dc.schema.name.value,
                violatedRule = err0.getClass.getSimpleName,
                datasetId = tc.dc.schema.name.value,
                message = s"Data contract failed: $msg"
              )
              F.raiseError(err)
            }
          }
      )
      b.copy(stages = b.stages :+ stage)
    }
  }
}

