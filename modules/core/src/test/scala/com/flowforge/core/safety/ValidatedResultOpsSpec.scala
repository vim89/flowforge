package com.flowforge.core.safety

import cats.data.NonEmptyList
import com.flowforge.core.types.FlowForgeError
import org.scalatest.funsuite.AnyFunSuite

class ValidatedResultOpsSpec extends AnyFunSuite {
  test("toEither wraps invalid into CompositeError") {
    val e1  = FlowForgeError.ValidationError("e1")
    val e2  = FlowForgeError.ValidationError("e2")
    val nel = NonEmptyList.of(e1, e2)
    val v   = cats.data.Validated.invalidNel[FlowForgeError, Int](e1).leftMap(_ => nel)
    val out = new Safety.ValidatedResultOps(v).toEither
    assert(out.isLeft)
    val left = out.left.get
    assert(left.isInstanceOf[FlowForgeError.CompositeError])
    val comp = left.asInstanceOf[FlowForgeError.CompositeError]
    assert(comp.errors.toList.contains(e1) && comp.errors.toList.contains(e2))
  }
}
