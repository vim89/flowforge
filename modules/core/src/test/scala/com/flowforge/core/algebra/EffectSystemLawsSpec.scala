// scalafix:off DisableSyntax.noUnsafeRunSync
package com.flowforge.core.algebra

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import zio.{ Runtime, Task, Unsafe }

/**
 * Runs [[EffectSystemLaws]] against every shipped `EffectSystem` instance.
 *
 * The laws are the same list for each instance, so an adapter cannot pass by being tested differently from
 * the other one. Adding an instance means adding an [[EffectSystemLawsSpec.Adapter]] here, which is the
 * whole cost of holding it to the laws.
 *
 * Running an effect is the one thing a law cannot do for itself, since it needs a runtime, so each adapter
 * supplies that and nothing else.
 */
class EffectSystemLawsSpec extends AnyFunSpec with Matchers {

  private val adapters: List[EffectSystemLawsSpec.Adapter] =
    List(EffectSystemLawsSpec.CatsEffect, EffectSystemLawsSpec.Zio)

  adapters.foreach { adapter =>
    describe(s"EffectSystem[${adapter.name}]") {
      EffectSystemLaws.all(adapter.effectSystem).foreach { law =>
        it(law.name) {
          adapter.run(law.holds) shouldBe true
        }
      }
    }
  }
}

object EffectSystemLawsSpec {

  /** An instance plus the one capability a law cannot carry: how to run it. */
  sealed abstract class Adapter(val name: String) {
    type Eff[_]
    def effectSystem: EffectSystem[Eff]
    def run(law: Eff[Boolean]): Boolean
  }

  private object CatsEffect extends Adapter("IO") {
    type Eff[A] = IO[A]
    val effectSystem: EffectSystem[IO]   = EffectInstances.catsEffectSystemInstance
    def run(law: IO[Boolean]): Boolean   = law.unsafeRunSync()
  }

  private object Zio extends Adapter("Task") {
    type Eff[A] = Task[A]
    val effectSystem: EffectSystem[Task] = EffectInstances.zioEffectSystemInstance
    def run(law: Task[Boolean]): Boolean =
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(law).getOrThrowFiberFailure()
      }
  }
}
