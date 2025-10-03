package com.flowforge.compilefail

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }

/**
 * Compile-fail tests for nested optionality inside collections/maps. Ensures List[Option[A]] is NOT
 * considered equal to List[A] under Exact policies, and Map[K, Option[V]] differs from Map[K, V].
 */
class NestedOptionCollectionsSpec extends AnyWordSpec with Matchers {

  "Nested optionality in collections" should {
    "fail when List[Option[Int]] is compared to List[Int] under Exact" in
      assertTypeError(
        """
          import com.flowforge.core.contracts._
          type Out      = List[Option[Int]]
          type Contract = List[Int]
          implicitly[SchemaConforms[Out, Contract, SchemaPolicy.Exact]]
        """.stripMargin,
      )

    "fail when Map[String, Option[Int]] is compared to Map[String, Int] under Exact" in
      assertTypeError(
        """
          import com.flowforge.core.contracts._
          type Out      = Map[String, Option[Int]]
          type Contract = Map[String, Int]
          implicitly[SchemaConforms[Out, Contract, SchemaPolicy.Exact]]
        """.stripMargin,
      )

  }
}
