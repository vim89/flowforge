package com.flowforge.examples.demo

// Import the *compile-time* contract types from your core/contracts module.
// Adjust the import if your package differs slightly.
import com.flowforge.core.contracts.{SchemaPolicy, SchemaConforms}

object CompileConformanceDemo {
  // Producer has an extra field `age` that Consumer doesn't have
  final case class Producer(id: Long, email: String, age: Int)

  final case class Consumer(id: Long, email: String)

  // --- RED (Exact): uncomment this line to see the compiler STOP you.
  // val _ = implicitly[SchemaConforms[Producer, Consumer, SchemaPolicy.Exact]]

  // --- GREEN (Backward): producer may add fields; consumer tolerates missing.
  val _ = implicitly[SchemaConforms[Producer, Consumer, SchemaPolicy.Backward]]


  case class UserFull(id: Long, name: String, email: String, age: Int)
  case class User(id: Long, name: String, email: String)

  val valid: SchemaConforms[User, UserFull, SchemaPolicy.Forward] = implicitly // ✅ Works!


  case class UserExtended(id: Long, name: String, email: String, age: Int)
  // val invalid: SchemaConforms[UserExtended, User, SchemaPolicy.Forward] = implicitly

}
