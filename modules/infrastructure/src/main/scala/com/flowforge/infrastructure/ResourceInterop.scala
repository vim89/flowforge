package com.flowforge.infrastructure

import com.flowforge.core.algebra.{ EffectSystem, FlowforgeResource }

object ResourceInterop {

  // cats-effect Resource -> FlowforgeResource
  def fromCats[F[_], A](
    res: cats.effect.Resource[F, A],
  )(implicit
    M: cats.effect.kernel.MonadCancel[F, Throwable],
  ): FlowforgeResource[F, A] =
    new FlowforgeResource[F, A] {
      def use[B](f: A => F[B]): F[B] = res.use(f)
    }

  // ZIO acquire/release -> FlowforgeResource[Task, A]
  object ZioInterop {
    import zio.Task
    def fromZIO[A](
      acquire: => Task[A],
    )(
      release: A => Task[Unit],
    )(implicit
      F: EffectSystem[Task],
    ): FlowforgeResource[Task, A] =
      FlowforgeResource.make(acquire)(release)
  }

  // Java AutoCloseable -> FlowforgeResource using any EffectSystem[F]
  def fromAutoCloseable[F[_], A <: AutoCloseable](
    acquire: F[A],
  )(implicit
    F: EffectSystem[F],
  ): FlowforgeResource[F, A] =
    FlowforgeResource.make(acquire)(a => F.map(F.delay(a.close()))(_ => ()))
}
