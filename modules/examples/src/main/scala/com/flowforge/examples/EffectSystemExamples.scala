package com.flowforge.examples

import com.flowforge.core.algebra.EffectSystem

object EffectSystemExamples {
  def parallelWordCount[F[_]](texts: List[String])(implicit F: EffectSystem[F]): F[Int] =
    F.map(F.parTraverse(texts)(t => F.delay(t.split("\\s+").count(_.nonEmpty))))(_.sum)

  def resourceExample[F[_]](implicit F: EffectSystem[F]): F[Boolean] = {
    val closed = new java.util.concurrent.atomic.AtomicBoolean(false)
    F.flatMap(F.bracket(F.pure("res"))(_ => F.pure(()))(_ => F.delay { closed.set(true); () })) { _ =>
      F.delay(closed.get())
    }
  }
}

