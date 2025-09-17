# Bring Your Own Effect System (BYO-F)

FlowForge is effect-agnostic. Core APIs depend only on a small type class: `EffectSystem[F]` and a resource helper: `FlowforgeResource[F, R]`. This lets you use Cats-Effect IO, ZIO Task, or your own F.

## What you implement

Implement `EffectSystem[F]` once. The surface is minimal and maps to standard concepts:

```scala
import com.flowforge.core.algebra.EffectSystem
import scala.concurrent.duration.FiniteDuration

final case class MyF[A](run: () => A) // placeholder

object MyFInstances {
  implicit val myFEffectSystem: EffectSystem[MyF] = new EffectSystem[MyF] {
    def map[A,B](fa: MyF[A])(f: A => B): MyF[B] = MyF(() => f(fa.run()))
    def pure[A](a: A): MyF[A] = MyF(() => a)
    def ap[A,B](ff: MyF[A => B])(fa: MyF[A]): MyF[B] = MyF(() => ff.run()(fa.run()))
    def flatMap[A,B](fa: MyF[A])(f: A => MyF[B]): MyF[B] = MyF(() => f(fa.run()).run())
    def raiseError[A](e: Throwable): MyF[A] = throw e
    def handleErrorWith[A](fa: MyF[A])(f: Throwable => MyF[A]): MyF[A] =
      try fa catch { case t: Throwable => f(t) }

    // Async/Concurrency/Timing can be simplified or delegated to your runtime
    def async[A](k: (Either[Throwable,A] => Unit) => Unit): MyF[A] = ???
    def start[A](fa: MyF[A]): MyF[Fiber[MyF, A]] = ???
    def race[A,B](fa: MyF[A], fb: MyF[B]): MyF[Either[A,B]] = ???
    def racePair[A,B](fa: MyF[A], fb: MyF[B]): MyF[Either[(A,Fiber[MyF,B]),(Fiber[MyF,A],B)]] = ???
    def parProduct[A,B](fa: MyF[A], fb: MyF[B]): MyF[(A,B)] = ???
    def parTraverse[A,B](list: List[A])(f: A => MyF[B]): MyF[List[B]] = ???
    def bracket[A,B](acq: MyF[A])(use: A => MyF[B])(rel: A => MyF[Unit]): MyF[B] = ???
    def bracketCase[A,B](acq: MyF[A])(use: A => MyF[B])(rel: (A, ExitCase[Throwable]) => MyF[Unit]): MyF[B] = ???
    def delay[A](thunk: => A): MyF[A] = MyF(() => thunk)
    def suspend[A](fa: => MyF[A]): MyF[A] = fa
    def sleep(d: FiniteDuration): MyF[Unit] = ???
    def timeout[A](fa: MyF[A], d: FiniteDuration): MyF[A] = ???
    def tailRecM[A,B](a: A)(f: A => MyF[Either[A,B]]): MyF[B] = ???
  }
}
```

Most teams will reuse existing libraries instead (Cats-Effect IO, ZIO Task). FlowForge ships instances for IO and Task in `com.flowforge.core.instances.EffectInstances`.

## Resources with FlowforgeResource

Use `FlowforgeResource.make(acquire)(release)` for resource safety:

```scala
import com.flowforge.core.algebra.{ EffectSystem, FlowforgeResource }

def socketR[F[_]: EffectSystem]: FlowforgeResource[F, java.net.Socket] =
  FlowforgeResource.make(EffectSystem[F].delay(new java.net.Socket("localhost", 9000))) { s =>
    EffectSystem[F].delay(s.close())
  }

// Usage
socketR.use { s => EffectSystem[F].delay(s.getOutputStream.write(42)) }
```

## Interop helpers

We provide optional adapters (in infrastructure) for bridging:

```scala
// cats-effect Resource -> FlowforgeResource
import com.flowforge.infrastructure.ResourceInterop
val ffR = ResourceInterop.fromCats(catsResource)

// ZIO acquire/release -> FlowforgeResource[Task,*]
import com.flowforge.infrastructure.ResourceInterop.ZioInterop
val zff = ZioInterop.fromZIO(acquire)(release)

// Java AutoCloseable -> FlowforgeResource
import com.flowforge.infrastructure.ResourceInterop
def fromAutoCloseable[F[_], A <: AutoCloseable](fa: F[A])(implicit F: EffectSystem[F]) =
  ResourceInterop.fromAutoCloseable(fa)
```

## Using your F in pipelines

Once an `EffectSystem[F]` is in scope, everything composes:

```scala
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.{ EffectSystem }
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }
import com.flowforge.core.types._

def pipeline[F[_]: EffectSystem] =
  PipelineBuilder[F]("byo-effect")
    .addTypedSource[User, User, SchemaPolicy.Exact](TypedSource(src), _ => EffectSystem[F].pure(User(0,"e",0)))
    .addTransform[User](u => EffectSystem[F].pure(u.copy(age = u.age + 1)))
    .addTypedSink[User, SchemaPolicy.Exact](TypedSink(sink), (_, d) => dao.write(ds, d).void)
    .build()
```

Bring your own runtime and ecosystem; FlowForge stays out of your way.

