package com.flowforge.safety

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.atomic.AtomicBoolean

class ResourceSafetySpec extends AnyFunSuite {

  test("bracket releases on success and failure") {
    val released = new AtomicBoolean(false)
    val acquire  = IO.pure(42)
    val useOk    = (_: Int) => IO.pure("ok")
    val release  = (_: Int) => IO.delay(released.set(true))

    val ok = ResourceSafety.bracket[IO, Int, String](acquire)(useOk)(release).unsafeRunSync()
    assert(ok == "ok" && released.get())

    released.set(false)
    val useFail = (_: Int) => IO.raiseError[String](new RuntimeException("boom"))
    val ex = intercept[RuntimeException] {
      ResourceSafety.bracket[IO, Int, String](acquire)(useFail)(release).unsafeRunSync()
    }
    assert(ex.getMessage == "boom" && released.get())
  }

  test("resource acquires and releases") {
    val released = new AtomicBoolean(false)
    val r        = ResourceSafety.resource[IO, String](IO.pure("res"))(_ => IO.delay(released.set(true)))
    val out      = r.use(s => IO.pure(s"hello $s")).unsafeRunSync()
    assert(out == "hello res" && released.get())
  }
}

