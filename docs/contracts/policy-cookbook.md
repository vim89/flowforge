# Schema Policy Cookbook

This page shows minimal pass/fail examples for each `SchemaPolicy` with the exact kind of compile-time failures you should expect. Copy-paste these snippets into a small sbt project that depends on FlowForge or see the runnable tests in:

- modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail

Notes

- All examples use `PipelineBuilder` + `TypedSource/TypedSink` so the compiler must materialize `SchemaConforms[Out, Contract, P]` evidence.
- On mismatch, compilation aborts with a diff like:
  FlowForge: Contract drift (policy: com.flowforge.core.contracts.SchemaPolicy.Exact).
  Out: Producer vs Contract: Contract
  Missing: email:string
  Extra: age:int
  Mismatched: name expected string found int

Exact

Fail (extra field):

```scala
import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.contracts.{TypedSink, TypedSource}
import com.flowforge.core.types.{DataFormat, DataSink, DataSource}

case class Contract(id: Long, name: String)
case class Producer(id: Long, name: String, age: Int)

val src  = TypedSource[Contract](DataSource.local("/tmp/s1", DataFormat.Parquet))
val sink = TypedSink[Contract](DataSink.local("/tmp/d1", DataFormat.Parquet))

PipelineBuilder[IO]("exact-extra")
  .addTypedSource[Producer, Contract, SchemaPolicy.Exact](src, _ => IO.pure(Producer(1L, "a", 42)))
  .noTransform
  .addTypedSink[Contract, SchemaPolicy.Exact](sink, (_, _) => IO.unit)
```

Pass:

```scala
case class Producer(id: Long, name: String)
// same as Contract
```

ExactUnordered

- Field order ignored; names and types must match.

Fail (type mismatch):

```scala
case class Contract(id: Long, name: String)
case class Producer(id: Long, name: Int)
// same pipeline as above but with SchemaPolicy.ExactUnordered
```

ExactOrdered

- Order matters; names and types must match in order.

Fail (order mismatch):

```scala
case class Contract(a: Int, b: String)
case class Producer(b: String, a: Int)
```

ExactByPosition

- Names ignored; types must match by index.

Fail (type at position 0 differs):

```scala
case class Contract(a: Long, b: String)
case class Producer(a: String, b: Long)
```

Backward

- Missing fields are allowed only if the contract marks them optional or has defaults. Extra fields are allowed in Out.

Fail (required contract field missing):

```scala
case class Contract(id: Long, name: String, email: String)
case class Producer(id: Long, name: String) // missing email
```

Forward

- Fewer output fields allowed. Extra fields in Out are rejected.

Fail (extra field):

```scala
case class Contract(id: Long, name: String)
case class Producer(id: Long, name: String, age: Int) // extra
```

Full

- Always succeeds (no structural check).

Pass:

```scala
case class Contract(id: Long)
case class Producer(a: String, b: Int)
```

Troubleshooting

- If you don’t see a compile-time error where you expect one, ensure the code path forces the compiler to summon `SchemaConforms[...]` (e.g., by actually calling `.addTypedSource/.../addTypedSink` or summoning `implicitly[SchemaConforms[...]]`).
- The full set of runnable tests uses `assertTypeError/ assertCompiles` and can be used as templates.

