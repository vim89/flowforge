package $organization$

import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.core.contracts.derive.Shape
import com.flowforge.core.types._

/**
 * Comprehensive Contract Drift Demonstrations
 *
 * This file contains working examples and commented failure cases that demonstrate
 * FlowForge's compile-time contract validation.
 *
 * TO TEST: Uncomment the commented sections to see compile failures!
 */

// Base data models
case class User(id: Long, name: String, email: String, age: Int)
case class UserWithOptionalAge(id: Long, name: String, email: String, age: Option[Int])
case class UserWithCountry(id: Long, name: String, email: String, age: Int, country: String)

object ContractDemos {

  // Shape derivations
  implicit val userShape: Shape[User] = Shape.gen[User]
  implicit val userWithOptionalAgeShape: Shape[UserWithOptionalAge] = Shape.gen[UserWithOptionalAge]
  implicit val userWithCountryShape: Shape[UserWithCountry] = Shape.gen[UserWithCountry]

  /**
   * ✅ SUCCESS CASES - These will compile
   */
  def successfulExamples[F[_]: EffectSystem](): Unit = {
    val F = EffectSystem[F]

    // 1. Exact match - compiles successfully
    val exactMatchPipeline = PipelineBuilder[F]("exact-match")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        TypedSource[User](DataSource.local("users.csv", DataFormat.CSV)),
        _ => F.pure(User(1, "Alice", "alice@example.com", 25))
      )

    // 2. Backward compatibility - reader produces more fields than contract expects
    val backwardCompatPipeline = PipelineBuilder[F]("backward-compat")
      .addTypedSource[UserWithCountry, User, SchemaPolicy.Backward](
        TypedSource[User](DataSource.local("basic-users.csv", DataFormat.CSV)),
        _ => F.pure(UserWithCountry(1, "Bob", "bob@example.com", 30, "USA"))
      )

    // 3. Forward compatibility - contract has more fields than reader produces
    val forwardCompatPipeline = PipelineBuilder[F]("forward-compat")
      .addTypedSource[User, UserWithCountry, SchemaPolicy.Forward](
        TypedSource[UserWithCountry](DataSource.local("extended-users.csv", DataFormat.CSV)),
        _ => F.pure(User(1, "Charlie", "charlie@example.com", 35))
      )

    // Note: Full policy requires compatible types - simplified example
    val fullPolicyPipeline = PipelineBuilder[F]("full-policy")
      .addTypedSource[User, User, SchemaPolicy.Full](
        TypedSource[User](DataSource.local("any-users.csv", DataFormat.CSV)),
        _ => F.pure(User(1, "Diana", "diana@example.com", 28))
      )

    println("✅ All successful examples compiled!")
  }

  /**
   * ❌ FAILURE CASES - Uncomment these to see compile-time errors!
   */

  // UNCOMMENT TO TEST FAILURES:

  /*
  // Missing field example
  case class IncompleteUser(id: Long, name: String) // Missing email and age!
  implicit val incompleteUserShape: Shape[IncompleteUser] = Shape.gen[IncompleteUser]

  def missingFieldFailure[F[_]: EffectSystem](): F[Unit] = {
    val F = EffectSystem[F]

    // This will FAIL to compile - missing required fields
    val failingPipeline = PipelineBuilder[F]("failing-missing-field")
      .addTypedSource[IncompleteUser, User, SchemaPolicy.Exact](
        TypedSource[IncompleteUser](DataSource.local("incomplete.csv", DataFormat.CSV)),
        _ => F.pure(IncompleteUser(1, "Failing User"))
      )

    F.unit
  }
  */

  /*
  // Wrong type example
  case class WrongTypeUser(id: String, name: String, email: String, age: Int) // id is String instead of Long!
  implicit val wrongTypeUserShape: Shape[WrongTypeUser] = Shape.gen[WrongTypeUser]

  def wrongTypeFailure[F[_]: EffectSystem](): F[Unit] = {
    val F = EffectSystem[F]

    // This will FAIL to compile - wrong type for id field
    val failingPipeline = PipelineBuilder[F]("failing-wrong-type")
      .addTypedSource[WrongTypeUser, User, SchemaPolicy.Exact](
        TypedSource[WrongTypeUser](DataSource.local("wrong-type.csv", DataFormat.CSV)),
        _ => F.pure(WrongTypeUser("string-id", "Failing User", "fail@example.com", 25))
      )

    F.unit
  }
  */

  /*
  // Extra field with Exact policy (will fail)
  case class ExtraFieldUser(id: Long, name: String, email: String, age: Int, extraField: String)
  implicit val extraFieldUserShape: Shape[ExtraFieldUser] = Shape.gen[ExtraFieldUser]

  def extraFieldWithExactFailure[F[_]: EffectSystem](): F[Unit] = {
    val F = EffectSystem[F]

    // This will FAIL to compile - Exact policy doesn't allow extra fields
    val failingPipeline = PipelineBuilder[F]("failing-extra-field")
      .addTypedSource[ExtraFieldUser, User, SchemaPolicy.Exact](
        TypedSource[ExtraFieldUser](DataSource.local("extra-field.csv", DataFormat.CSV)),
        _ => F.pure(ExtraFieldUser(1, "Failing User", "fail@example.com", 25, "extra"))
      )

    F.unit
  }
  */

  /**
   * Schema Evolution Scenarios
   *
   * Real-world examples of how schema policies handle evolution
   */
  def evolutionScenarios[F[_]: EffectSystem](): F[Unit] = {
    val F = EffectSystem[F]

    // Scenario 1: Adding optional field (Backward compatible)
    // Old consumer can still read new data by ignoring extra fields
    case class UserV1(id: Long, name: String, email: String, age: Int)
    case class UserV2(id: Long, name: String, email: String, age: Int, country: Option[String] = None)

    implicit val userV1Shape: Shape[UserV1] = Shape.gen[UserV1]
    implicit val userV2Shape: Shape[UserV2] = Shape.gen[UserV2]

    val backwardEvolution = PipelineBuilder[F]("backward-evolution")
      .addTypedSource[UserV2, UserV1, SchemaPolicy.Backward](
        TypedSource[UserV1](DataSource.local("v1-users.csv", DataFormat.CSV)),
        _ => F.pure(UserV2(1, "Evolved User", "evolved@example.com", 30, Some("USA")))
      )

    // Scenario 2: Making field optional (Forward compatible)
    // New consumer can handle old data by providing defaults
    val forwardEvolution = PipelineBuilder[F]("forward-evolution")
      .addTypedSource[UserV1, UserV2, SchemaPolicy.Forward](
        TypedSource[UserV2](DataSource.local("v2-users.csv", DataFormat.CSV)),
        _ => F.pure(UserV1(1, "Legacy User", "legacy@example.com", 28))
      )

    F.unit
  }

  /**
   * Quality Gate Integration
   *
   * Shows how contract validation works with data quality
   */
  def qualityIntegration[F[_]: EffectSystem](): F[Unit] = {
    val F = EffectSystem[F]

    // Contract ensures compile-time structure
    // Quality checks ensure runtime data validity
    val qualityPipeline = PipelineBuilder[F]("quality-integration")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        TypedSource[User](DataSource.local("users.csv", DataFormat.CSV)),
        _ => F.pure(User(1, "Quality User", "quality@example.com", 25))
      )
      .addTransform[User] { user =>
        // Runtime quality validation
        if (user.email.contains("@") && user.age > 0) {
          F.pure(user)
        } else {
          F.raiseError(new IllegalArgumentException(s"Invalid user data: " + user))
        }
      }

    F.unit
  }
}
