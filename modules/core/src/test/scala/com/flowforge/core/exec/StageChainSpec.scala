package com.flowforge.core.exec

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Covers what the chain exists to provide: the type between two stages is checked when the second one is
 * appended, so the composer has nothing left to cast.
 */
class StageChainSpec extends AnyFunSuite with Matchers {

  implicit val es: EffectSystem[IO] = EffectInstances.catsEffectSystemInstance

  /** The decorator the composer would supply, with its wrapping left out. */
  private val plain: StageDecorator[IO] = new StageDecorator[IO] {
    def apply[A, B](stageName: String, stage: Kleisli[IO, A, B]): Kleisli[IO, A, B] = stage
  }

  /**
   * Runs `body` on a thread with a 512k stack and hands back whatever it produced.
   *
   * A deep-chain test on the default stack measures the runner, not the code: the JVM's main thread gets 8MB
   * on macOS and about 1MB on the Linux and Windows runners. Naming the stack size makes the bound the test
   * asserts the same everywhere. `StackOverflowError` is not `NonFatal`, so `Try` would not catch it.
   */
  private def onSmallStack[A](body: => A): A = {
    var outcome: Either[Throwable, A] = Left(new IllegalStateException("thread never ran"))
    val thread = new Thread(
      null,
      () =>
        outcome =
          try Right(body)
          catch { case e: Throwable => Left(e) },
      "small-stack",
      512L * 1024L,
    )
    thread.start()
    thread.join()
    outcome match {
      case Right(value) => value
      case Left(e)      => throw e
    }
  }

  private def chain: StageChain[IO, Int, String] =
    StageChain
      .empty[IO, Int]
      .andThen("double", StageKind.Transform, Kleisli((n: Int) => IO.pure(n * 2)))
      .andThen("render", StageKind.Transform, Kleisli((n: Int) => IO.pure(s"n=$n")))

  test("an empty chain runs as the identity") {
    StageChain.empty[IO, Int].arrow(plain).run(7).unsafeRunSync() shouldBe 7
  }

  test("the arrow runs the stages in the order they were appended") {
    chain.arrow(plain).run(4).unsafeRunSync() shouldBe "n=8"
  }

  test("names come back in the order the stages run") {
    chain.names shouldBe List("double", "render")
  }

  test("counts come from the kind the builder gave each stage") {
    val withSink = chain.andThen("write", StageKind.Sink, Kleisli((_: String) => IO.unit))
    withSink.count(StageKind.Transform) shouldBe 2
    withSink.count(StageKind.Sink) shouldBe 1
    withSink.count(StageKind.Quality) shouldBe 0
    withSink.size shouldBe 3
  }

  test("a chain deeper than the thread's stack still builds and runs") {
    // Runs on a small stack on purpose. An arrow that walked its links eagerly overflowed here between 2k
    // and 4k, and the depth it survived depended on the thread rather than the chain: the same 10k chain
    // passed on macOS and overflowed on Linux and Windows CI. 50k on 512k of stack leaves no room for that
    // to pass by accident.
    val deep = (1 to 50000).foldLeft(StageChain.empty[IO, Int]) { (chain, i) =>
      chain.andThen(s"add-$i", StageKind.Transform, Kleisli((n: Int) => IO.pure(n + 1)))
    }
    deep.size shouldBe 50000
    onSmallStack(deep.arrow(plain).run(0).unsafeRunSync()) shouldBe 50000
  }

  test("a stage that does not read the previous stage's output is rejected") {
    assertDoesNotCompile(
      """
      StageChain
        .empty[IO, Int]
        .andThen("double", StageKind.Transform, Kleisli((n: Int) => IO.pure(n * 2)))
        .andThen("shout", StageKind.Transform, Kleisli((s: String) => IO.pure(s.toUpperCase)))
      """,
    )
  }
}
