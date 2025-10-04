package com.flowforge.core.algebra
import com.flowforge.core.types._
import com.flowforge.core.types.ErrorSeverity
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TypeClassesContractCoverageSpec extends AnyFunSuite with Matchers {

  test("ValidationRule and DataContract validate/combine/relaxed") {
    val ruleBlock: ValidationRule[Int] = ValidationRule("gt0", "greater than 0", ErrorSeverity.Error) { x =>
      if (x > 0) cats.data.Validated.valid(()) else cats.data.Validated.invalidNel(RuleViolation("gt0", ">0"))
    }
    val ruleWarn: ValidationRule[Int] = ValidationRule("lt10", "less than 10", ErrorSeverity.Warning) { x =>
      if (x < 10) cats.data.Validated.valid(()) else cats.data.Validated.invalidNel(RuleViolation("lt10", "<10"))
    }

    val dc  = DataContract.fromRules(List(ruleBlock, ruleWarn), DataSchema.builder.addField("x", DataType.Integer).build)
    val dc2 = DataContract.fromRules(List(ruleWarn), DataSchema.builder.build)

    // success
    dc.validate(5).isValid shouldBe true
    // failure from blocking rule
    dc.validate(-1).isInvalid shouldBe true

    // combine accumulates rules
    val combined = dc.combine(dc2)
    combined.rules.size should be >= 2

    // relaxed drops blocking rules; only warnings remain effectively
    val relaxed = dc.relaxed
    relaxed.satisfiesMinimum(100) shouldBe true // only warning should not block
  }

  test("ProcessingMetrics combine and helpers") {
    val a = ProcessingMetrics(recordCount = 10, byteSize = 100, processingTimeMs = 5, errorCount = 1).withTiming("op", 10).withCustom("c", 1.5)
    val b = ProcessingMetrics(recordCount = 5, byteSize = 50, processingTimeMs = 7, errorCount = 0)
    val c = a.combine(b)
    c.recordCount shouldBe 15
    c.byteSize shouldBe 150
    c.processingTimeMs shouldBe 12
    c.errorCount shouldBe 1
  }
}
