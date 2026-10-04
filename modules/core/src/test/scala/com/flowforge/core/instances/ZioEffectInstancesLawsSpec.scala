// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.core.instances

import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.zioEffectSystemInstance
import zio.test.Assertion._
import zio.test.{ Live, _ }
import zio.{ durationInt => _, _ }

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
        // This used to sleep 50 ms on each side and require the pair to finish inside 150 ms. A CI runner
        // under load took 196 ms, so the test reported a sequential parProduct when all it had measured was
        // a busy machine.
        //
        // Each side now announces that it started and then waits for the other to announce the same. That
        // can only finish if both are running at once, which is the property being tested, and it says
        // nothing about how fast the machine is. A sequential parProduct would block instead of returning a
        // wrong answer, so the timeout is what turns that into a failure.
        Live.live {
          Promise
            .make[Nothing, Unit]
            .zip(Promise.make[Nothing, Unit])
            .flatMap {
              case (started1, started2) =>
                val op1: Task[Int] = started1.succeed(()) *> started2.await.as(1)
                val op2: Task[Int] = started2.succeed(()) *> started1.await.as(2)
                effectSystem.parProduct(op1, op2).timeout(Duration.fromSeconds(10))
            }
            .map(result => assertTrue(result.contains((1, 2))))
        }
      },
    )
}
