package com.flowforge.core.errors

import com.flowforge.core.types._
import com.flowforge.core.types.ValidationError._
import com.flowforge.core.types.SystemError._
import com.flowforge.core.types.BusinessError._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ErrorTypesCoverageSpec extends AnyFunSuite with Matchers {
  test("ValidationError variants support helpers") {
    val sv  = SchemaViolation("f", "string", "int").withContext(Map("k" -> 1)).withCause(new RuntimeException("x"))
    val qv  = QualityViolation("rule", "v", Some("th")).withContext(Map("k" -> 2)).withCause(new RuntimeException("y"))
    val mrf = MissingRequiredField("id").withContext(Map("k" -> 3)).withCause(new RuntimeException("z"))
    val tm  = TypeMismatch("f", "int", "string", "v").withContext(Map("k" -> 4)).withCause(new RuntimeException("w"))
    sv.message should include ("Schema")
    qv.recoveryHints.nonEmpty shouldBe true
    mrf.fieldName shouldBe "id"
    tm.expectedType shouldBe "int"
  }

  test("SystemError variants support helpers") {
    val re = ResourceExhausted("mem", "1g", "2g").withContext(Map("h" -> true)).withCause(new RuntimeException("x"))
    val su = ServiceUnavailable("svc", Some("http://"))
      .withContext(Map("h" -> false)).withCause(new RuntimeException("y"))
    val ot = OperationTimeout("op", 1.second, 2.seconds).withContext(Map("t" -> 2)).withCause(new RuntimeException("z"))
    re.isRetryable shouldBe true
    su.serviceName shouldBe "svc"
    ot.timeout shouldBe 1.second
  }

  test("BusinessError variants support helpers") {
    val dc = DataContractViolation("c", "r", "ds").withContext(Map("a" -> 1)).withCause(new RuntimeException("x"))
    val sla = SlaViolation("latency", "10ms", "50ms", java.time.Duration.ofSeconds(5)).withContext(Map("b" -> 2)).withCause(new RuntimeException("y"))
    dc.message should include ("contract")
    sla.violationDuration shouldBe java.time.Duration.ofSeconds(5)
  }
}
