# BYO‑F Examples

This page demonstrates using FlowForge with different effect systems using the same core APIs.

## IO (Cats‑Effect)

```scala
import cats.effect.IO
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance
import com.flowforge.core.algebra.FlowforgeResource

val F = EffectSystem[IO]
val res: FlowforgeResource[IO, java.io.ByteArrayOutputStream] =
  FlowforgeResource.make(F.delay(new java.io.ByteArrayOutputStream()))(s => F.delay(s.close()))

res.use { s => F.delay(s.write(1)) }
```

## ZIO Task

```scala
import zio._
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances.zioEffectSystemInstance
import com.flowforge.core.algebra.FlowforgeResource

val F  = EffectSystem[Task]
val r: FlowforgeResource[Task, java.io.ByteArrayInputStream] =
  FlowforgeResource.make(F.delay(new java.io.ByteArrayInputStream(Array[Byte](1,2,3))))(s => F.delay(s.close()))

val prog: Task[Int] = r.use(is => F.delay(is.read()))
```

## Pipeline with BYO‑F

```scala
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.types._
import com.flowforge.core.contracts.{ SchemaConforms, SchemaPolicy }

final case class User(id: Long, email: String, age: Int)
implicit val conforms: SchemaConforms[User, User, SchemaPolicy.Exact] = implicitly

def buildPipeline[F[_]: EffectSystem](src: DataSource, snk: DataSink, dao: com.flowforge.core.algebra.DataAlgebra[F]) =
  PipelineBuilder[F]("byo-pipeline")
    .addTypedSource[User, User, SchemaPolicy.Exact](TypedSource(src), _ => EffectSystem[F].pure(User(0, "x@y", 18)))
    .addTransform[User](u => EffectSystem[F].pure(u.copy(age = u.age + 1)))
    .addTypedSink[User, SchemaPolicy.Exact](TypedSink(snk), (_, d) => dao.read[User](src).flatMap(ds => dao.write(ds, d)).void)
    .build()
```

