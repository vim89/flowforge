package com.flowforge.safety

import cats.effect.Resource

/**
 * Resource safety framework for automatic resource management.
 */
trait ResourceSafety[F[_]] {
  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]
  def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A]
  def combineResources[A, B](
    resourceA: Resource[F, A],
    resourceB: Resource[F, B],
  ): Resource[F, (A, B)]
  def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A]
}

/**
 * Cloud-specific resource safety for cloud operations.
 */
trait CloudResourceSafety[F[_]] extends ResourceSafety[F] {
  def safeConnection[Provider, A](provider: Provider)(use: Connection[Provider] => F[A]): F[A]
  def safeFileHandle[A](path: CloudPath)(use: FileHandle => F[A]): F[A]
  def safeStreamProcessing[A, B](
    inputStream: F[Stream[A]],
  )(
    process: Stream[A] => F[Stream[B]],
  ): F[Stream[B]]
}

// Supporting types for cloud resource safety
case class Connection[Provider](provider: Provider, handle: AnyRef)
case class CloudPath(path: String)
case class FileHandle(path: CloudPath, handle: AnyRef)
case class Stream[A](data: List[A])

object ResourceSafety {
  def bracket[F[_], A, B](
    acquire: F[A],
  )(
    use: A => F[B],
  )(
    release: A => F[Unit],
  )(implicit
    F: cats.MonadError[F, Throwable],
  ): F[B] =
    F.flatMap(acquire) { a =>
      F.handleErrorWith(
        F.flatMap(use(a))(b => F.map(release(a))(_ => b)),
      )(e => F.flatMap(release(a))(_ => F.raiseError(e)))
    }

  def resource[F[_], A](
    acquire: F[A],
  )(
    release: A => F[Unit],
  )(implicit
    F: cats.MonadError[F, Throwable],
  ): Resource[F, A] =
    Resource.make(acquire)(release)
}
