/**
 * FlowForge Core Module - Effect System Test
 *
 * Simple test to verify both Cats-Effect IO and ZIO Task work correctly with the FlowForge
 * EffectSystem abstraction.
 */
package com.flowforge.core.examples

import cats.effect.{ IO, IOApp }
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances._
import zio.{ Runtime, Task, ZIO }

/**
 * Tests both effect systems with simple operations
 */
object EffectSystemTest extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      _ <- IO.println("🧪 Testing FlowForge Effect System Integration")
      _ <- IO.println()

      _ <- testCatsEffectIO
      _ <- IO.println()

      _ <- testZIOTask
      _ <- IO.println()

      _ <- IO.println("✅ Both effect systems working correctly!")

    } yield ()

  /**
   * Test Cats-Effect IO integration
   */
  def testCatsEffectIO: IO[Unit] = {
    implicit val es: EffectSystem[IO] = catsEffectSystemInstance

    for {
      _ <- IO.println("🐱 Testing Cats-Effect IO:")

      // Test basic operations
      result <- es.pure(42)
      _      <- IO.println(s"   Pure: $result")

      // Test flatMap
      doubled <- es.flatMap(es.pure(21))(x => es.pure(x * 2))
      _       <- IO.println(s"   FlatMap: $doubled")

      // Test parallel operations
      results <- es.parTraverse(List(1, 2, 3))(x => es.pure(x * 10))
      _       <- IO.println(s"   Parallel: $results")

      _ <- IO.println("   ✅ Cats-Effect IO working!")

    } yield ()
  }

  /**
   * Test ZIO Task integration
   */
  def testZIOTask: IO[Unit] = {
    implicit val es: EffectSystem[Task] = zioEffectSystemInstance
    val runtime                         = Runtime.default

    for {
      _ <- IO.println("⚡ Testing ZIO Task:")

      // Test basic operations with ZIO
      basicTest <- IO.fromFuture(IO.delay {
        val task = for {
          result  <- es.pure(42)
          doubled <- es.flatMap(es.pure(21))(x => es.pure(x * 2))
          results <- es.parTraverse(List(1, 2, 3))(x => es.pure(x * 10))
        } yield (result, doubled, results)

        zio.Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe.runToFuture(task)
        }
      })

      _ <- IO.println(s"   Pure: ${basicTest._1}")
      _ <- IO.println(s"   FlatMap: ${basicTest._2}")
      _ <- IO.println(s"   Parallel: ${basicTest._3}")
      _ <- IO.println("   ✅ ZIO Task working!")

    } yield ()
  }
}
