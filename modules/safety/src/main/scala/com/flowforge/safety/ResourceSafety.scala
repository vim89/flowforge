package com.flowforge.safety

import cats.effect.kernel.MonadCancel
import cats.effect.{ Resource, Sync }

/**
 * Resource safety framework providing automatic resource management with bracket patterns. This is
 * a core Infrastructure Layer component that ensures all external resources (connections, files,
 * streams) are properly cleaned up.
 */
trait ResourceSafety[F[_]] {

  /**
   * Basic bracket pattern for resource acquisition and cleanup. Ensures release is called even if
   * use throws an exception.
   */
  def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B]

  /**
   * Advanced bracket with exit case handling. Provides information about how the use operation
   * completed.
   */
  def bracketCase[A, B](
    acquire: F[A]
  )(
    use: A => F[B]
  )(
    release: (A, ExitCase[Throwable]) => F[Unit]
  ): F[B]

  /**
   * Convert acquire/release pair into a Resource for composition.
   */
  def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A]

  /**
   * Safe resource composition - combine multiple resources.
   */
  def combineResources[A, B](
    resourceA: Resource[F, A],
    resourceB: Resource[F, B]
  ): Resource[F, (A, B)]

  /**
   * Ensure an operation with automatic cleanup, even on failure.
   */
  def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A]
}

object ResourceSafety {

  /**
   * Create ResourceSafety instance for any effect type with MonadCancel capability.
   */
  def apply[F[_]: ResourceSafety]: ResourceSafety[F] = implicitly[ResourceSafety[F]]

  /**
   * Default implementation using Cats Effect's resource management.
   */
  implicit def forCatsEffect[F[_]: Sync: MonadCancel[*[_], Throwable]]: ResourceSafety[F] =
    new CatsEffectResourceSafety[F]

  /**
   * Concrete implementation using Cats Effect primitives.
   */
  private class CatsEffectResourceSafety[F[_]: Sync: MonadCancel[*[_], Throwable]]
      extends ResourceSafety[F] {

    override def bracket[A, B](
      acquire: F[A]
    )(
      use: A => F[B]
    )(
      release: A => F[Unit]
    ): F[B] = {
      val F = MonadCancel[F, Throwable]
      F.bracket(acquire)(use)(release)
    }

    override def bracketCase[A, B](
      acquire: F[A]
    )(
      use: A => F[B]
    )(
      release: (A, ExitCase[Throwable]) => F[Unit]
    ): F[B] = {
      val F = MonadCancel[F, Throwable]
      F.bracketCase(acquire)(use)(release)
    }

    override def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A] =
      Resource.make(acquire)(release)

    override def combineResources[A, B](
      resourceA: Resource[F, A],
      resourceB: Resource[F, B]
    ): Resource[F, (A, B)] =
      for {
        a <- resourceA
        b <- resourceB
      } yield (a, b)

    override def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A] = {
      val F = MonadCancel[F, Throwable]
      F.bracket(Sync[F].unit)(_ => operation)(_ => cleanup)
    }
  }
}

/**
 * Cloud-specific resource safety for managing cloud connections and handles. Extends basic
 * ResourceSafety with cloud provider abstractions.
 */
trait CloudResourceSafety[F[_]] extends ResourceSafety[F] {

  /**
   * Safe cloud connection management. Automatically handles authentication, connection pooling, and
   * cleanup.
   */
  def safeConnection[Provider, A](
    provider: Provider
  )(
    use: Connection[Provider] => F[A]
  ): F[A]

  /**
   * Safe file handle management for cloud storage operations.
   */
  def safeFileHandle[A](path: CloudPath)(use: FileHandle => F[A]): F[A]

  /**
   * Safe stream processing with automatic resource cleanup.
   */
  def safeStreamProcessing[A, B](
    inputStream: F[Stream[A]]
  )(
    process: Stream[A] => F[Stream[B]]
  ): F[Stream[B]]
}

// Cloud provider types (to be implemented in Service Layer)
sealed trait CloudProvider extends Product with Serializable

case class Connection[Provider](provider: Provider, handle: AnyRef)
case class CloudPath(provider: CloudProvider, path: String)
case class FileHandle(path: CloudPath, handle: AnyRef)
case class Stream[A](data: List[A])

object CloudResourceSafety {

  /**
   * Create CloudResourceSafety instance that extends basic ResourceSafety.
   */
  implicit def forCloudProvider[F[_]: ResourceSafety: Sync]: CloudResourceSafety[F] =
    new DefaultCloudResourceSafety[F]

  private class DefaultCloudResourceSafety[F[_]: ResourceSafety: Sync]
      extends CloudResourceSafety[F] {

    private val safety = ResourceSafety[F]

    override def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
      safety.bracket(acquire)(use)(release)

    override def bracketCase[A, B](
      acquire: F[A]
    )(
      use: A => F[B]
    )(
      release: (A, ExitCase[Throwable]) => F[Unit]
    ): F[B] =
      safety.bracketCase(acquire)(use)(release)

    override def resource[A](acquire: F[A])(release: A => F[Unit]): Resource[F, A] =
      safety.resource(acquire)(release)

    override def combineResources[A, B](
      resourceA: Resource[F, A],
      resourceB: Resource[F, B]
    ): Resource[F, (A, B)] =
      safety.combineResources(resourceA, resourceB)

    override def ensuring[A](operation: F[A])(cleanup: F[Unit]): F[A] =
      safety.ensuring(operation)(cleanup)

    override def safeConnection[Provider, A](
      provider: Provider
    )(
      use: Connection[Provider] => F[A]
    ): F[A] =
      bracket(
        acquire = Sync[F].delay {
          // TODO: Implement actual cloud provider connection logic
          Connection(provider, new Object)
        }
      )(
        use = use
      )(
        release = conn =>
          Sync[F].delay {
            // TODO: Implement actual connection cleanup
            ()
          }
      )

    override def safeFileHandle[A](path: CloudPath)(use: FileHandle => F[A]): F[A] =
      bracket(
        acquire = Sync[F].delay {
          FileHandle(path, new Object)
        }
      )(
        use = use
      )(
        release = handle =>
          Sync[F].delay {
            // TODO: Implement actual file handle cleanup
            ()
          }
      )

    override def safeStreamProcessing[A, B](
      inputStream: F[Stream[A]]
    )(
      process: Stream[A] => F[Stream[B]]
    ): F[Stream[B]] =
      bracket(
        acquire = inputStream
      )(
        use = process
      )(
        release = _ => Sync[F].unit
      )
  }
}
