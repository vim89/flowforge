package com.flowforge.examples.demo

import cats.effect.{ Fiber, IO, IOApp }
import scala.concurrent.duration._

object CatsFibers extends IOApp.Simple {
  def work(name: String): IO[Int] =
    IO.println(s"[$name] start") *>
      IO.sleep(750.millis) *>
      IO.println(s"[$name] done") *> IO.pure(name.length)

  val run: IO[Unit] =
    for {
      f1 <- work("alpha").start
      f2 <- work("beta").start
      r1 <- f1.joinWithNever
      r2 <- f2.joinWithNever
      _  <- IO.println(s"results: $r1 + $r2 = ${r1 + r2}")
    } yield ()
}
