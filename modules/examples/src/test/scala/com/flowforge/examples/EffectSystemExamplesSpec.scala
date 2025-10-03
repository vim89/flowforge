// scalafix:off DisableSyntax.var DisableSyntax.throw DisableSyntax.null DisableSyntax.noUnsafeRunSync
package com.flowforge.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EffectSystemExamplesSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

  test("parallel word count works") {
    val texts = List("hello world", "flowforge effect system", "is quite neat")
    val count = EffectSystemExamples.parallelWordCount[IO](texts).unsafeRunSync()
    count shouldBe 6
  }

  test("resource example closes resource") {
    val closed = EffectSystemExamples.resourceExample[IO].unsafeRunSync()
    closed shouldBe true
  }
}
