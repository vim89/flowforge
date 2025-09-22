package com.flowforge.examples.demo

import zio._

object ZioFibers extends ZIOAppDefault {
  def work(name: String): UIO[Int] =
    Console.printLine(s"[$name] start").orDie *>
      ZIO.sleep(750.millis) *>
      Console.printLine(s"[$name] done").orDie *>
      ZIO.succeed(name.length)

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    for {
      f1 <- work("gamma").fork
      f2 <- work("delta").fork
      r1 <- f1.join
      r2 <- f2.join
      _  <- Console.printLine(s"results: $r1 + $r2 = ${r1 + r2}").orDie
    } yield ()
}
