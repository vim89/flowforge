package com.flowforge.core.effects

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class EffectInstancesCanceledCoverageSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("bracketCase canceled exit case when fiber canceled") {
    @volatile var canceled = false
    val acquire = IO.pure(1)
    val useNever: Int => IO[Int] = _ => IO.never
    val release = (_: Int, ec: es.ExitCase[Throwable]) => IO {
      ec match {
        case es.ExitCase.Canceled => canceled = true
        case _                    => ()
      }
    }

    val effect = es.bracketCase(acquire)(useNever)(release)
    val fiber  = es.start(effect).unsafeRunSync()
    IO.sleep(20.millis).unsafeRunSync()
    fiber.cancel.unsafeRunSync()
    canceled shouldBe true
  }
}

