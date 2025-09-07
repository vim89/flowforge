package com.flowforge.contracts

import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Compile-time test suite for SchemaConforms functionality.
 * This demonstrates that FlowForge contracts work correctly with Magnolia-based schema validation.
 */
class SchemaConformsCompileSpec extends AnyWordSpec with Matchers {

  // Shared test types defined at class level
  case class BaseRecord(id: String, value: Int, active: Boolean)
  case class ExtendedRecord(id: String, value: Int, active: Boolean, extraField: String)
  case class SubsetRecord(id: String, value: Int)

  "SchemaConforms with Magnolia" should {

    "provide evidence for exact schema matches with same types" in {
      // Same type should always match under exact policy
      val evidence: SchemaConforms[BaseRecord, BaseRecord, SchemaPolicy.Exact] =
        implicitly[SchemaConforms[BaseRecord, BaseRecord, SchemaPolicy.Exact]]

      evidence shouldBe a[SchemaConforms[_, _, _]]
    }

    "provide evidence for backward compatibility with extra fields" in {
      // Extended type should conform to base type under backward policy  
      val evidence: SchemaConforms[ExtendedRecord, BaseRecord, SchemaPolicy.Backward] =
        implicitly[SchemaConforms[ExtendedRecord, BaseRecord, SchemaPolicy.Backward]]

      evidence shouldBe a[SchemaConforms[_, _, _]]
    }

    "provide evidence for forward compatibility with fewer fields" in {
      // Subset type should conform to base type under forward policy
      val evidence: SchemaConforms[SubsetRecord, BaseRecord, SchemaPolicy.Forward] =
        implicitly[SchemaConforms[SubsetRecord, BaseRecord, SchemaPolicy.Forward]]

      evidence shouldBe a[SchemaConforms[_, _, _]]
    }

    "work with complex types" in {
      case class ComplexRecord(
        id: String,
        metadata: Map[String, String],
        tags: List[String],
        score: Option[Double]
      )

      val evidence: SchemaConforms[ComplexRecord, ComplexRecord, SchemaPolicy.Exact] =
        implicitly[SchemaConforms[ComplexRecord, ComplexRecord, SchemaPolicy.Exact]]

      evidence shouldBe a[SchemaConforms[_, _, _]]
    }

    // Document expected compile failures without actually triggering them
    "document expected compile failures" in {
      info("Field name mismatches would prevent SchemaConforms resolution")
      info("Type mismatches would prevent SchemaConforms resolution")
      info("Missing required fields would prevent SchemaConforms resolution under strict policies")
      
      // These tests pass because they're just documentation
      succeed
    }
  }
}