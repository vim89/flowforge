package $organization$

import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._

/**
 * Examples of contract mismatches.
 *
 * Each block is commented out. Uncomment one and run `sbt compile`
 * to see the corresponding compile-time failure.
 */
object ContractFailureDemo {

  // Expected contract
  case class User(id: Long, name: String, email: String)
  implicit val userShape: Shape[User] = Shape.gen[User]

  /*
  // 1. Missing required field (email missing)
  case class MissingFieldUser(id: Long, name: String)
  implicit val missingFieldShape: Shape[MissingFieldUser] = Shape.gen[MissingFieldUser]

  def missingField[F[_]: EffectSystem]: Unit = {
    PipelineBuilder[F]("missing-field")
      .addTypedSource[MissingFieldUser, User, SchemaPolicy.Exact](
        TypedSource[MissingFieldUser](DataSource.local("users.csv", DataFormat.CSV)),
        m => EffectSystem[F].pure(m)
      )
    ()
  }
  */

  /*
  // 2. Wrong type for id field (should be Long)
  case class WrongTypeUser(id: String, name: String, email: String)
  implicit val wrongTypeShape: Shape[WrongTypeUser] = Shape.gen[WrongTypeUser]

  def wrongType[F[_]: EffectSystem]: Unit = {
    PipelineBuilder[F]("wrong-type")
      .addTypedSource[WrongTypeUser, User, SchemaPolicy.Exact](
        TypedSource[WrongTypeUser](DataSource.local("users.csv", DataFormat.CSV)),
        w => EffectSystem[F].pure(w)
      )
    ()
  }
  */

  /*
  // 3. Extra field not allowed by Exact policy
  case class ExtraFieldUser(id: Long, name: String, email: String, age: Int)
  implicit val extraFieldShape: Shape[ExtraFieldUser] = Shape.gen[ExtraFieldUser]

  def extraField[F[_]: EffectSystem]: Unit = {
    PipelineBuilder[F]("extra-field")
      .addTypedSource[ExtraFieldUser, User, SchemaPolicy.Exact](
        TypedSource[ExtraFieldUser](DataSource.local("users.csv", DataFormat.CSV)),
        e => EffectSystem[F].pure(e)
      )
    ()
  }
  */
}

