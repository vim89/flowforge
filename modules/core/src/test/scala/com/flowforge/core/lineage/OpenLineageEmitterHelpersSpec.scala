package com.flowforge.core.lineage

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OpenLineageEmitterHelpersSpec extends AnyFunSuite with Matchers {
  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  test("generateRunId produces a stable, non-empty id when no env set") {
    val id = OpenLineageEmitter.generateRunId("testpipe")
    id.trim.length should be > 0
  }

  test("pipeline-level emit helpers succeed with noop emitter") {
    val em  = OpenLineageEmitter.noop[IO]
    val rid = OpenLineageEmitter.generateRunId("p")
    val r1  = OpenLineageEmitter.emitPipelineStart(em, "p", rid).unsafeRunSync()
    val r2  = OpenLineageEmitter.emitPipelineComplete(em, "p", rid).unsafeRunSync()
    val r3  = OpenLineageEmitter.emitPipelineFail(em, "p", rid, "x").unsafeRunSync()
    r1 shouldBe Right(())
    r2 shouldBe Right(())
    r3 shouldBe Right(())
  }

  test(
    "generateRunId uses system property when set and Http emitter maps failures",
    com.flowforge.core.testing.How,
  ) {
    val propKey = "openlineage.run.id"
    try {
      System.setProperty(propKey, "ff-fixed-id")
      val id = OpenLineageEmitter.generateRunId("p2")
      id shouldBe "ff-fixed-id"

      // Http emitter attempts local endpoint and should map any failure to Left(LineageError)
      val http = new HttpOpenLineageEmitter[IO]()
      val res  = http.emitJobStart("ns", "job", id).unsafeRunSync()
      res.isLeft shouldBe true
    } finally
      System.clearProperty(propKey)
  }
}
