package $organization$.$name;format="word"$

import com.flowforge.core.contracts.derive.Shape

object ContractShapes {
  final case class User(id: Long, name: String, email: String)
  implicit val userShape: Shape[User] = Shape.gen[User] // Magnolia derivation
}