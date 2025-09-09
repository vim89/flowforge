package com.flowforge.core.instances

import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.zioEffectSystemInstance
import zio.test.Assertion._
import zio.test.{ Live, _ }
import zio.{ durationInt => _, _ }

import scala.concurrent.duration._

object ZioEffectInstancesLawsSpec extends ZIOSpecDefault {
  private val effectSystem: EffectSystem[Task] = zioEffectSystemInstance

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIO EffectSystem instance")(
      test("satisfies Monad left identity") {
        val a                   = 42
        val f: Int => Task[Int] = x => ZIO.succeed(x + 1)
        val left                = effectSystem.flatMap(effectSystem.pure(a))(f)
        val right               = f(a)
        assertZIO(left.zip(right).map { case (l, r) => l == r })(isTrue)
      },
      test("satisfies Monad right identity") {
        val a    = 42
        val fa   = effectSystem.pure(a)
        val left = effectSystem.flatMap(fa)(effectSystem.pure)
        assertZIO(left.zip(fa).map { case (l, r) => l == r })(isTrue)
      },
      test("satisfies Monad associativity") {
        val a                   = 42
        val f: Int => Task[Int] = x => ZIO.succeed(x + 2)
        val g: Int => Task[Int] = x => ZIO.succeed(x * 3)
        val left                = effectSystem.flatMap(effectSystem.flatMap(effectSystem.pure(a))(f))(g)
        val right = effectSystem.flatMap(effectSystem.pure(a))(x => effectSystem.flatMap(f(x))(g))
        assertZIO(left.zip(right).map { case (l, r) => l == r })(isTrue)
      },
      test("provides stack-safe tailRecM") {
        val largeN = 10000
        val result = effectSystem.tailRecM(0) { i =>
          if (i < largeN) ZIO.succeed(Left(i + 1)) else ZIO.succeed(Right(i))
        }
        assertZIO(result)(equalTo(largeN))
      },
      test("handles errors in bracket operations") {
        var released = false
        val err      = new RuntimeException("boom")
        val prog = effectSystem
          .bracket(effectSystem.pure("res"))(_ => effectSystem.raiseError[String](err))(_ =>
            effectSystem.delay { released = true },
          )
          .either
        assertZIO(prog.map(_.left.map(_ => released)))(isLeft(equalTo(true)))
      },
      test("runs parallel operations concurrently") {
        Live.live {
          val start = java.lang.System.currentTimeMillis()
          val op1   = effectSystem.sleep(DurationInt(50).millis) *> effectSystem.pure(1)
          val op2   = effectSystem.sleep(DurationInt(50).millis) *> effectSystem.pure(2)
          effectSystem.parProduct(op1, op2).map {
            case (r1, r2) =>
              val elapsed = java.lang.System.currentTimeMillis() - start
              assertTrue(r1 == 1 && r2 == 2 && elapsed < 150)
          }
        }
      },
    )
}
