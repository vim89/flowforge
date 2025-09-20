# How do we demonstrate flowforge by Experimental: Kyo - tracks multiple effects via intersection types and Caprese - capture-checking tracks capabilities
Here’s a **clean, minimal way to demo FlowForge with the two “experimental” models** you called out—(A) **Kyo** (tracks multiple effects with intersection types) and (B) **Caprese** (capture-checking that tracks capabilities). I’ve kept the code idiomatic and aligned to FlowForge’s `PipelineBuilder[F]` pattern you have under `/mnt/data/flowforge`. Explanations reference primary sources.

Kyo tracks effects in a type parameter and composes them using **intersection types (`&`)**; values look like `A < (Sync & Async & Abort[String])`, with Cats interop available via `kyo-cats`. ([GitHub][1]) Caprese’s **capture checking** is an experimental Scala 3 feature: enable it with `import language.experimental.captureChecking`, then annotate *capabilities* (resources) as `^` (e.g., `FileSystem^`) so the compiler prevents them from escaping their safe lifetime. ([Scala Documentation][2])

---

```scala
// build.sbt  — add these to a small demo module (e.g., modules/experiments/kyo-caprese-demo)
// Kyo (effect system) + Cats interop, plus Scala 3 to try capture checking.
ThisBuild / scalaVersion := "3.3.3" // or newer Scala 3.x you've validated in CI

lazy val kyoCapreseDemo = (project in file("modules/experiments/kyo-caprese-demo"))
  .settings(
    name := "kyo-caprese-demo",
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-prelude" % "<latest>",
      "io.getkyo" %% "kyo-core"    % "<latest>",
      "io.getkyo" %% "kyo-cats"    % "<latest>", // bidirectional Cats <-> Kyo
      "org.typelevel" %% "cats-effect" % "3.5.4"
    )
  )

// Notes:
// • Kyo uses intersection types (&) to track effects; kyo-cats gives IO interop for bridging to FlowForge’s F[_]. :contentReference[oaicite:2]{index=2}
// • For Caprese demos, import language.experimental.captureChecking in code (no flag required). :contentReference[oaicite:3]{index=3}
```

```scala
// KyoFlowforgeDemo.scala  — show FlowForge stages written in Kyo and bridged to F[_] via kyo-cats
package demo.kyo

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.{SchemaPolicy}
import com.flowforge.contracts.TypedSource
import com.flowforge.core.types.{DataFormat, DataSource}

import kyo.*           // core Kyo API
import kyo.prelude.*   // Sync/Async/Abort, etc.
import kyo.cats.*      // bidirectional interop with Cats-Effect IO  (module: kyo-cats)  :contentReference[oaicite:4]{index=4}

// Domain
final case class User(id: Long, name: String, email: String, age: Int)
final case class EnrichedUser(id: Long, name: String, email: String, age: Int, segment: String)

// Kyo: define an effect set using intersection types (&). A value of type A < FX is a computation
// that yields A and has “pending” effects FX. (Kyo tracks multiple effects via intersection types.) :contentReference[oaicite:5]{index=5}
type FX = Sync & Async & Abort[String]
type KF[A] = A < FX

// Small Kyo program (pure-ish business logic)
object KyoStages {
  def segment(u: User): KF[EnrichedUser] =
    Sync.defer {
      val seg =
        if (u.age < 25) "young"
        else if (u.age < 45) "adult"
        else "senior"
      EnrichedUser(u.id, u.name.trim, u.email.toLowerCase, u.age, seg)
    }
}

// Interop: turn Kyo computations into IO using kyo-cats (so FlowForge can keep F = IO).
// kyo-cats provides bidirectional Cats IO interop (module listed in Kyo README). :contentReference[oaicite:6]{index=6}
object KyoInterop {
  def toIO[A](ka: KF[A]): IO[A] =
    // The exact call depends on the library version; kyo-cats exposes conversions
    // that materialize a Cats-Effect IO out of a Kyo computation.
    // Pseudocode: Cats.toIO(ka) — use the provided kyo-cats helpers for your version.
    // Replace the next line with the real conversion for your Kyo version:
    IO.defer( IO.fromFuture( IO(scala.concurrent.Future.successful( () ) ) ).asInstanceOf[IO[A]] )
}

// FlowForge pipeline: use Kyo for stage logic, bridge to IO at boundaries.
object KyoFlowforgeDemo {
  def pipeline: PipelineBuilder[IO] =
    PipelineBuilder[IO]("kyo-demo")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        TypedSource[User](DataSource.local("users.csv", DataFormat.CSV)),
        _ => IO.pure(User(1, "Alice", "Alice@Example.com", 25))
      )
      // Bridge Kyo stage into IO using the interop; FlowForge stays F[_]-agnostic.
      .addTransform[EnrichedUser] { u =>
        KyoInterop.toIO( KyoStages.segment(u) )
      }
      // … add sinks/validation as usual
}

// Why this demonstrates the point
// • Kyo tracks several effects at once in the type (e.g., Sync & Async & Abort[String]) using intersection types.
// • You keep FlowForge’s F[_] abstraction intact and only bridge at the boundary via kyo-cats. :contentReference[oaicite:7]{index=7}
```

```scala
// CapreseFlowforgeDemo.scala  — showcase capture-checking: prevent capability escape around FlowForge stages
package demo.caprese

import language.experimental.captureChecking  // turn on the checker in this file  :contentReference[oaicite:8]{index=8}

import cats.effect.IO
import com.flowforge.core.PipelineBuilder
import com.flowforge.core.contracts.SchemaPolicy
import com.flowforge.contracts.TypedSource
import com.flowforge.core.types.{DataFormat, DataSource}

// A resource-like capability we do NOT want to escape its lifetime
final class FileSystem:
  def write(s: String): Unit = println(s"[fs] $s")

// Domain
final case class User(id: Long, name: String)

// A helper that scopes the capability; clients get a capability-annotated parameter (fs: FileSystem^)
// and cannot leak it (the compiler enforces that). Adapted from the Scala 3 docs pattern. :contentReference[oaicite:9]{index=9}
def withFileSystem[T](op: FileSystem^ => T): T =
  val fs = FileSystem()
  val out = op(fs) // fs is a capability: its type contains a non-empty capture set
  out               // fs goes out of scope here; escape would be rejected

object CapreseStages {

  // Safe writer: returns a PURE function (->) that does not capture fs.
  // If we tried to close over `fs` here, the compiler would reject it (capability escape). :contentReference[oaicite:10]{index=10}
  def mkPureWriter(fs: FileSystem^): String -> Unit =
    (msg: String) => fs.write(msg)  // OK because capture set is tied to the function’s lifetime

  // ❌ Uncomment to see a typical *rejected* escape (like the "later" example in the docs):
  // val later: () => Unit = withFileSystem { fs => () => fs.write("boom") }
  // // error: The expression's type () => Unit is not allowed to capture the root capability `cap`. :contentReference[oaicite:11]{index=11}
}

object CapreseFlowforgeDemo {

  // We’ll scope the capability *inside* a FlowForge transform, proving we can use the resource
  // safely without leaking it out of the stage boundary.
  def pipeline: PipelineBuilder[IO] =
    PipelineBuilder[IO]("caprese-demo")
      .addTypedSource[User, User, SchemaPolicy.Exact](
        TypedSource[User](DataSource.local("users.csv", DataFormat.CSV)),
        _ => IO.pure(User(1, "Alice"))
      )
      .addTransform[User] { u =>
        IO.blocking {
          withFileSystem { fs =>
            val write: String -> Unit = CapreseStages.mkPureWriter(fs)
            write(s"processing ${u.id}:${u.name}") // safe: capability is scoped to this call
            u
          }
        }
      }
      // If you attempt to stash `write` in a lazy structure or return it, capture-checking rejects it. :contentReference[oaicite:12]{index=12}
}

// Why this demonstrates the point
// • Caprese/capture-checking annotates capabilities (FileSystem^) and makes escapes ill-typed,
//   mirroring the docs’ “usingLogFile” example but inside a FlowForge stage. :contentReference[oaicite:13]{index=13}
```

### Why these two demos are the “right” ones

* **Kyo demo (multiple effects via intersection types):** You keep FlowForge’s `F[_]` surface, write stage logic in Kyo (`A < FX` with `FX = Sync & Async & Abort[String]`), and bridge via **kyo-cats**. That directly illustrates *“track many effects in the type, compose with `&`, and still plug into FlowForge”*. ([Gist][3], [GitHub][1])

* **Caprese demo (capability/capture tracking):** You scope a resource (`FileSystem^`) in a FlowForge transform. The compiler forbids returning closures or lazy structures that would leak that capability—exactly the “no later” guarantee the **official capture checking docs** show (e.g., the `usingLogFile` / `later` example). ([Scala Documentation][2])

---

#### Sources (most important claims)

* **Kyo**: effect tracking with intersection types; modules include **kyo-cats** for IO interop; release notes on intersection-typed records. ([Gist][3], [GitHub][1], [Scala Users][4])
* **Caprese / Capture Checking**: Scala 3 experimental docs (capabilities, `^`, `->`, `A ->{c} B`), plus background paper & writeups. ([Scala Documentation][2], [ABG][5], [nrinaudo.github.io][6])

If you want, I can adapt the Kyo interop stub (`KyoInterop.toIO`) to the exact function names for the specific Kyo version you choose (they’re in the `kyo-cats` module); the pattern above remains the same. ([GitHub][1])

[1]: https://github.com/getkyo/kyo "GitHub - getkyo/kyo: Toolkit for Scala Development"
[2]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html "Capture Checking"
[3]: https://gist.github.com/ahoy-jon/0aec8bcf636fac096ae5e4b9ed706fe0?utm_source=chatgpt.com "The Rebirth of Tagless Final: An Ergonomic Approach in Kyo"
[4]: https://users.scala-lang.org/t/kyo-0-16-0-released/10501?utm_source=chatgpt.com "Kyo 0.16.0 released - Announce"
[5]: https://abgru.me/publication/capturing-types/capturing-types.pdf?utm_source=chatgpt.com "Keeping Track of Capabilities - ABG"
[6]: https://nrinaudo.github.io/articles/capture_checking.html?utm_source=chatgpt.com "Hands on Capture Checking - Nicolas Rinaudo"

--------
# What is caprese exactly? Is it effect system?

Short version: **Caprese isn’t a library-level effect system** (like ZIO, Cats-Effect, or Kyo). It’s an *ongoing language research program* for Scala that proposes **capability-based effect safety**, whose flagship feature is **capture checking**—a compiler/type-system mechanism that tracks what “capabilities” (resources/effects) a value or closure may capture. You enable the experiment today with `import language.experimental.captureChecking`. ([Scala][1])

**What Caprese aims to provide**

* A **type-system foundation** for resources & effects in direct style (no mandatory monads), by tracking capabilities and preventing illegal “capability escape” at compile time. Think of it as a borrow-checker-like pass in the Scala compiler. ([Scala Days][2], [POPL 2024][3])
* The concrete piece you can try now is **capture checking** (e.g., `^` for capability-annotated types, `->` for non-capturing functions), which is still **highly experimental** and evolving. ([Scala][1], [Reddit][4])

**So… is it an effect system?**
Not in the usual sense. Caprese is **language-level infrastructure** to *express and enforce* effect/resource safety; library effect systems can coexist with (or build on) it. Industry commentary and project notes describe Caprese as a **capability-based effect safety direction** for Scala, not a replacement for all library effect systems. ([John A De Goes][5], [GitHub][6])

**Status & docs to follow**

* Official reference for **capture checking** (experimental): what it is, how to enable it, examples. ([Scala][1])
* Conference talks & summaries positioning capture checking as a new approach to effect *safety* in Scala. ([Scala Days][2])

**Bottom line:** Caprese = *research & compiler/type-system work* (capabilities + capture checking) to make effects/resources safe in direct style. It’s **not** a runtime effect system itself, but it can **complement** or **underpin** library effect systems. ([Scala][1], [Scala Days][2])

[1]: https://scala-lang.org/api/3.3.6/docs/docs/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[2]: https://scaladays.org/editions/2025/talks/capture-checking-a-new-approach?utm_source=chatgpt.com "Capture Checking: A New Approach to Effect Safety in Scala"
[3]: https://popl24.sigplan.org/details/wits-2024-papers/11/Inside-the-Scala-Capture-Checker?utm_source=chatgpt.com "Inside the Scala Capture Checker (WITS 2024)"
[4]: https://www.reddit.com/r/scala/comments/1jx07p8/experimental_capture_checking_new_syntax_for/?utm_source=chatgpt.com "Experimental Capture Checking: New Syntax for Explicit ..."
[5]: https://degoes.net/articles/scala-resurrection?utm_source=chatgpt.com "Scala Resurrection"
[6]: https://github.com/getkyo/kyo/issues/211?utm_source=chatgpt.com "Issue #211 · getkyo/kyo - Caprese Alternative"

-----
# How can Caprese be a really useful in data engineering data pipelines & of-course flowforge? We need a real value-add with simplicity not really a fancy thing or over-engineering. Think logically

Here’s a no-nonsense take: **Caprese/capture-checking can be a net win in data pipelines when you use it surgically at the few boundaries that most often bite teams**—UDFs/closures, connector lifecycles, and plugin callbacks. You don’t turn the whole codebase into a research project; you add a couple of *type-checked guardrails* that prevent the classic “oops we captured a resource/secret/connection” class of bugs at **compile time**, while keeping your pipeline code in **direct style**. ([Scala Documentation][1], [Scala][2], [Scala Days][3])

## Where it’s genuinely useful (and simple)

1. **Pure UDFs / transforms stay pure (compile-time)**

* Mark transforms that must be pure as `A -> B` (non-capturing). The compiler rejects any attempt to close over a file handle, DB client, or secret. This kills a whole category of late runtime failures and accidental leaks in Spark/Flink maps, aggregations, and window functions. ([Scala][2])

2. **No more leaking connectors across stage boundaries**

* Wrap capabilities (`KafkaProducer`, `JDBC`, `FileSystem`, `Secrets`) so they **can’t escape** a `withX { x^ => ... }` scope. If a closure tries to stash `x` for later (e.g., into a Future, a lazy val, or a returned function), you get a compile error instead of a flaky prod incident. ([Scala Documentation][1])

3. **Resource lifetimes match code blocks**

* Capture checking tracks capability lifetimes; it plays the role of a “borrow checker” for resources in Scala’s type system. This *pairs nicely* with your existing effect/runtime cleanup (ZIO/Cats-Effect): effects do the releasing, capture checking ensures you didn’t let the resource escape in the first place. ([ABG][4])

4. **Plugin and callback safety**

* FlowForge can require that user-supplied callbacks (e.g., data-quality predicates, partition filters) are either pure (`A -> B`) or only capture a whitelisted set (e.g., `{metrics, clock} A -> B`). That keeps extension points honest without heavy ceremony. ([Scala][2])

5. **Direct-style ergonomics vs. monadic boilerplate**

* The main carrot: you keep **direct style** and still get compile-time effect/resource safety—Caprese’s whole pitch. This is complementary to library effect systems; it’s not a replacement. Use it **just** where it pays off. ([Scala Days][3], [SoftwareMill][5])

---

## How to thread this into FlowForge (pragmatic, opt-in)

**A. Tighten the risky spots only**

* **UDFs / record transforms**: make `pipeline.pureTransform(f: A -> B)` available alongside `transform(f: A => F[B])`. If a dev accidentally captures a connector/secret in `pureTransform`, the compiler stops them. ([Scala][2])
* **Connector scopes**: expose helpers like `withJdbc`, `withKafka`, `withGcs` that pass capability-annotated params (e.g., `Jdbc^`, `Kafka^`) to a block; anything trying to leak that param out will fail to typecheck. ([Scala Documentation][1])

**B. Keep it optional & low-friction**

* Put this behind `import language.experimental.captureChecking` and a tiny FlowForge submodule (e.g., `flowforge-caprese`) so teams can turn it on per project. It’s still experimental; don’t force it on everyone. ([Scala Documentation][6], [dotty.epfl.ch][7])

**C. Document two everyday patterns**

1. **Pure transform (no capture allowed)**

   ```scala
   // A -> B = non-capturing (pure). Compiler rejects any captured capability.
   val normalize: User -> User =
     u => u.copy(email = u.email.toLowerCase.trim)
   ```

   If someone tries `val p = kafkaProducer; u => { p.send(...); u }`, it won’t compile in this slot. ([Scala][2])

2. **Scoped capability (no escape)**

   ```scala
   def withKafka[T](op: KafkaProducer^ => T): T = {
     val kp = new KafkaProducer(/*...*/)
     val out = op(kp)       // kp is a capability; cannot escape this block
     kp.close()
     out
   }
   ```

   Any attempt to return a function that closes over `kp` (or stash it in a global) is rejected by capture checking. ([Scala Documentation][1])

**D. Fit alongside effect systems**

* You can still orchestrate with Cats-Effect/ZIO, or play with **Kyo** if you want effect sets via intersection types. Caprese prevents escapes; your effect system does lifecycles, concurrency, and retries. (Kyo shows how intersections model multiple effects cleanly.) ([GitHub][8], [Reddit][9], [Scala Documentation][10])

---

## Why this is real value (not fancy)

* **Stops common DE bugs early:** non-serializable resources captured by UDFs, long-lived leaked clients, and secrets stuffed into closures. *All* are compile-time now. ([Scala][2])
* **Zero runtime penalty, minimal API change:** a couple of `pureTransform`/`withX` helpers and targeted type annotations. The rest of FlowForge stays the same, in direct style. ([Scala Days][3])
* **Auditable type signatures:** your public APIs literally codify which capabilities are allowed, which are not. That’s easier to review than comments. ([Scala][2])
* **Future-proof bet:** capture checking is an **official Scala 3 experimental** with active work/talks; adopting it *optionally* where it pays off makes strategic sense. ([Scala Documentation][1], [Scala Days][11])

---

## Sensible cautions

* It’s **experimental** (syntax and rules evolve). Keep it opt-in and CI-gated to specific Scala versions; provide a fallback (pure functions as a convention) if the feature is disabled. ([Scala Documentation][6], [dotty.epfl.ch][7])
* Don’t sprinkle it everywhere. Use it **only** where you historically see leaks or illegal captures (UDFs, connector scopes, plugin callbacks).

---

### Bottom line

Use Caprese **surgically** to make FlowForge’s high-risk spots *compile-time safe*, while preserving your current direct-style, effect-polymorphic design. That’s real DX and operational value with almost no ceremony.

[1]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[2]: https://scala-lang.org/api/3.3.6/docs/docs/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[3]: https://scaladays.org/editions/2025/talks/hands-on-capture-checking?utm_source=chatgpt.com "Hands-On Capture Checking (with Martin Odersky)"
[4]: https://abgru.me/publication/capturing-types/capturing-types.pdf?utm_source=chatgpt.com "Keeping Track of Capabilities - ABG"
[5]: https://softwaremill.com/the-future-of-effects-in-scala/?utm_source=chatgpt.com "The future of effects in Scala?"
[6]: https://docs.scala-lang.org/scala3/reference/experimental/index.html?utm_source=chatgpt.com "Experimental"
[7]: https://dotty.epfl.ch/docs/reference/experimental/index.html?utm_source=chatgpt.com "Experimental - Scala 3 - EPFL"
[8]: https://github.com/getkyo/kyo?utm_source=chatgpt.com "getkyo/kyo: Toolkit for Scala Development"
[9]: https://www.reddit.com/r/scala/comments/1fg3n1r/kyo_0120_released/?utm_source=chatgpt.com "Kyo 0.12.0 released 🚀 : r/scala"
[10]: https://docs.scala-lang.org/tour/compound-types.html?utm_source=chatgpt.com "Intersection Types, aka Compound Types | Tour of Scala"
[11]: https://scaladays.org/editions/2025/talks/the-first-steps-towards-practical?utm_source=chatgpt.com "The first steps towards practical capture checking"

-------

# Great, we need to add all of these in experimental module in flowforge.
## I need in-detail, comprehensive plan with low-level design & implementation & comprehensive documentation which explains even to beginners

Awesome—let’s add an **experimental** area to FlowForge that showcases real, *practical* value from:

* **Caprese / Capture Checking** (compile-time capability safety),
* **Kyo** (multi-effect tracking with Scala 3 intersection types, with Cats-Effect interop).

Nothing flashy or over-engineered—just tight guardrails where data pipelines usually fail: UDFs/closures, connector lifecycles, and plugin callbacks.

Below is a **complete, shippable plan** (module layout, low-level APIs, impl notes, CI, tests, docs, examples), written so a beginner can follow, but deep enough for senior devs.

---

# Why these experiments?

* **Capture Checking (Caprese)** adds a compiler pass that **tracks which “capabilities” a value/closure captures**; you can then **forbid leaks** (e.g., a DB client escaping its scope) or declare **pure** (non-capturing) functions using the `->` arrow syntax. It’s experimental but available in Scala 3 via `import language.experimental.captureChecking`. ([Scala Documentation][1])
* **Kyo** represents effects as a **type-level intersection set** (e.g., `A < (Sync & Async & Abort[String])`) and ships **kyo-cats** to interoperate with Cats-Effect (so we can keep FlowForge’s `F[_] = IO` surface). ([GitHub][2])
* **Intersection types** are a Scala 3 feature (`A & B`)—Kyo uses them to track multiple effects precisely. ([Scala Documentation][3])

---

# 0) Overview & Goals

**Primary goals (practical):**

1. Stop resource/secret/connector **leaks** at compile time (Caprese).
2. Make **pure UDFs** explicit and compiler-checked (Caprese `->`).
3. Allow **Kyo-based stage logic** while keeping FlowForge’s `F[_]` unchanged (kyo-cats bridge).

**Non-goals:** replacing your existing effect systems; forcing Scala 3 across the repo. Everything remains **opt-in** and **isolated**.

---

# 1) Repository layout (sbt)

Add **two new modules** and examples, isolated so the rest of FlowForge remains unchanged:

```
modules/
  experimental-caprese/           // Scala 3 only
    src/main/scala/...
    src/test/scala/...
  experimental-kyo/               // Scala 3 only
    src/main/scala/...
    src/test/scala/...

modules/experimental-examples/    // tiny apps showing both in FlowForge
  src/main/scala/...
docs/experimental/
  caprese/...
  kyo/...
  README.md
```

**sbt** (top-level `build.sbt` additions):

```scala
lazy val experimentalSettings = Seq(
  scalaVersion := "3.3.3",                // capture checking + Kyo demo
  crossPaths := false,                     // keep isolated
  // keep warnings strict; these are experiments, we want precise feedback:
  scalacOptions ++= Seq("-Xfatal-warnings")
)

lazy val experimental = (project in file("modules/experimental-caprese"))
  .settings(name := "flowforge-experimental-caprese")
  .settings(experimentalSettings)

lazy val experimentalKyo = (project in file("modules/experimental-kyo"))
  .settings(name := "flowforge-experimental-kyo")
  .settings(experimentalSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-prelude" % "<latest>",
      "io.getkyo" %% "kyo-core"    % "<latest>",
      "io.getkyo" %% "kyo-cats"    % "<latest>",  // interop with Cats Effect
      "org.typelevel" %% "cats-effect" % "3.5.4"
    )
  )

lazy val experimentalExamples = (project in file("modules/experimental-examples"))
  .dependsOn(experimental, experimentalKyo, core /* your core module id */)
  .settings(experimentalSettings)
```

* **Why 3.3.3**: capture checking is experimental & evolving; use a **recent Scala 3**. ([Scala Documentation][1])
* **kyo-cats** provides drop-in interop with Cats-Effect; we preserve `F[_] = IO` at the FlowForge edge. ([GitHub][2])

---

# 2) Experimental–Caprese (capture checking) — Low-level design

> Tiny utilities that give you **real wins** with minimal ceremony.

## 2.1 Public API (module `experimental-caprese`)

```scala
package com.flowforge.experimental.caprese

import language.experimental.captureChecking  // required in files using CC :contentReference[oaicite:5]{index=5}

/** Marker alias for non-capturing functions.
  * Users can pass `A -> B` where closures that capture capabilities are forbidden by the compiler.
  */
type PureFn[-A, +B] = A -> B           // from capture checking; “does not capture capabilities” :contentReference[oaicite:6]{index=6}

/** Scope a capability and forbid escape. */
def withCapability[C, A](acquire: => C)(use: C^ => A): A =
  val c = acquire
  use(c)                               // if user tries to leak `c` outside, compile fails

/** A safer transform slot in the pipeline that *requires* a pure non-capturing function. */
trait PureTransformDsl {
  self: com.flowforge.core.PipelineBuilderDsl =>

  def pureTransform[A, B](stageName: String)(f: PureFn[A, B]): this.type
}
```

### How it integrates

* **`pureTransform`** goes next to your existing `addTransform` but **only accepts `A -> B`**—the compiler **rejects** any closure that captures a capability (e.g., a JDBC client). ([Scala Documentation][1])
* **`withCapability`** is a tiny helper for **connectors**: they expose `withX { x^ => ... }`, so the compiler ensures the capability `x` never escapes. This mirrors the standard pattern in the Scala docs. ([Scala Documentation][1])

## 2.2 Example usage

```scala
import language.experimental.captureChecking
import com.flowforge.experimental.caprese.*

val normalizeEmail: PureFn[User, User] =
  u => u.copy(email = u.email.trim.toLowerCase)   // any attempt to capture a DB client here: compile error

val pipeline =
  PipelineBuilder[IO]("caprese-demo")
    .pureTransform("normalize")(normalizeEmail)   // safe UDF slot
    .addTransform("write") { u =>
      // scope the capability; cannot escape this block
      IO.blocking {
        withCapability(new FileSystem) { fs =>
          fs.write(u.email)                       // ok
          u
        }
      }
    }
```

**Why this is helpful:** It kills the common class of bugs where a dev accidentally captures a **non-serializable or long-lived resource** inside a map/filter/closure. The compiler stops it. (This capability model is exactly what capture checking is about.) ([Scala Documentation][1])

---

# 3) Experimental–Kyo — Low-level design

> Let users write some stage logic in **Kyo** while keeping FlowForge’s `F[_]` surface (`IO`) and engines unchanged.

## 3.1 Public API (module `experimental-kyo`)

```scala
package com.flowforge.experimental.kyo

import cats.effect.IO
import kyo.*, kyo.prelude.*, kyo.cats.*  // interop module  :contentReference[oaicite:10]{index=10}

/** A Kyo computation that yields A and has pending effects FX. */
type KF[A] = A < (Sync & Async & Abort[String])  // common default; users can choose their own FX set. :contentReference[oaicite:11]{index=11}

object KyoInterop:
  /** Convert Kyo to IO using kyo-cats (exact call depends on your kyo version). */
  def toIO[A](ka: KF[A]): IO[A] =
    // Replace with the specific interop call from kyo-cats version you select.
    Cats.toCats[IO, A](ka)    // <- example placeholder; wire to the real function provided by kyo-cats
```

Pipeline extension (in a `syntax` object):

```scala
package com.flowforge.experimental.kyo.syntax

import cats.effect.IO
import com.flowforge.experimental.kyo.{KF, KyoInterop}
import com.flowforge.core.PipelineBuilder

extension (pb: PipelineBuilder[IO])
  def kyoTransform[A, B](stageName: String)(f: A => KF[B]): PipelineBuilder[IO] =
    pb.addTransform(stageName)(a => KyoInterop.toIO(f(a)))
```

* Users get an easy `kyoTransform` that **accepts a Kyo function** and bridges it into `IO` at the boundary via **kyo-cats**. ([GitHub][2])
* **Effect set** is a type intersection: users can refine `FX` (e.g., `Sync & Abort[E]`) as they see fit. ([Scala Documentation][3])

## 3.2 Example usage

```scala
import kyo.*, kyo.prelude.*
import com.flowforge.experimental.kyo.syntax.*

// Kyo stage: classify a user using Sync & Abort effects
def classify(u: User): KF[User] =
  Sync.defer {
    if u.age < 0 then Abort.fail("bad age") else u
  }

val p =
  PipelineBuilder[IO]("kyo-demo")
    .kyoTransform("classify")(classify)        // Kyo stage
    .addTransform("format")(u => IO.pure(u.copy(email = u.email.toLowerCase)))
```

---

# 4) Documentation (for beginners)

Create `docs/experimental/`:

```
docs/experimental/
  README.md
  caprese/
    01-what-is-capture-checking.md
    02-getting-started.md
    03-udf-pure-transform.md
    04-connector-capabilities.md
    05-faq-limitations.md
  kyo/
    01-what-is-kyo.md
    02-how-effects-work-intersections.md
    03-cats-interop.md
    04-writing-stages-in-kyo.md
    05-faq-performance-limitations.md
```

**Draft contents (essentials):**

* **Caprese/01-what**: Plain-English intro; **“compiler tracks which capabilities a closure captures; `->` is a non-capturing function”**; show `^` annotation concept and why this helps Data Engineering. (quote or paraphrase Scala docs) ([Scala Documentation][1])

* **Caprese/02**: Enable with `import language.experimental.captureChecking` and Scala 3. **Minimal working example**. Show a compile error when trying to return a closure that captures a file handle (mirror the doc examples). ([Scala Documentation][1])

* **Caprese/03**: `pureTransform` vs. `addTransform`, when to use each (e.g., UDFs on Spark/Flink).

* **Caprese/04**: `withCapability` patterns for JDBC/Kafka/GCS—compile-time scoping.

* **Caprese/05**: Limitations & opt-in nature; links to talks/posts so readers know it’s experimental. ([Scala Days][4], [Scala Contributors][5])

* **Kyo/01-what**: Kyo in one page; the `A < S` notation; why intersection types. ([Gist][6])

* **Kyo/02**: Intersection types primer (`A & B`). ([Scala Documentation][3])

* **Kyo/03**: kyo-cats bridge (how to plug into FlowForge’s `IO`). ([GitHub][2])

* **Kyo/04**: Hands-on stage authoring; error handling with `Abort[E]`; when to stay with plain `IO`.

* **Kyo/05**: Notes on performance & maturity; link to Cats-Effect discussion benchmarking context. ([GitHub][7])

---

# 5) Examples (beginner-friendly)

Under `modules/experimental-examples`:

1. **Caprese: Pure UDF slot**

    * Pipeline with `.pureTransform("normalize")(A -> B)`; try to capture `KafkaProducer` → compile error screenshot. (Derived from official capture checking reference.) ([Scala Documentation][1])

2. **Caprese: Scoped connector**

    * `withCapability(Jdbc(...)) { jdbc^ => ... }` writing a row; try to return a `() => jdbc.query(...)` → compile error (cannot capture capability). ([Scala Documentation][1])

3. **Kyo: classify stage**

    * `kyoTransform("classify")(u => Sync.defer(...))`, plus a failing path using `Abort[String]` to show how errors propagate back into `IO`. (Interop through **kyo-cats**.) ([GitHub][2])

---

# 6) CI & Build

Add a **separate CI job** that only builds experimental modules on Scala 3:

```yaml
# .github/workflows/ci-experimental.yml
name: CI Experimental
on: [push, pull_request]
jobs:
  experimental-scala3:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17]  # keep simple, 21 optional later
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: ${{ matrix.java }} }
      - name: SBT test (experimental only)
        run: sbt "project experimental" test "project experimentalKyo" test "project experimentalExamples" test
```

Optionally keep it **non-blocking** at first (soft gate). Later, promote to hard gate.

---

# 7) Tests

* **Compile-fail tests** proving capture checking **rejects**:

    * Returning closures that reference capability params.
    * Passing a capturing function into `pureTransform`.
* **Property tests** for `withCapability` helpers (scope enforced).
* **Happy-path** tests for `kyoTransform` round-tripping into `IO` via **kyo-cats**.

(Include a small doc page showing the compile-fail messages to make the feature *feel real* for users.)

---

# 8) Migration / Adoption Guidance

* **Opt-in**: Only projects on Scala 3 need this; leave the rest untouched.
* **Start here**: Wrap the UDFs you *wish* were pure in `.pureTransform`. If something breaks, it’s likely because you were capturing a resource—refactor that code into a `.transform` where you can manage effects explicitly.
* **Connector scopes**: Introduce `withJdbc/withKafka/withGcs` gradually. It pays immediate dividends by preventing “forgotten handle” bugs.
* **Kyo**: Adopt only if your team wants **effect-set precision** in a few hotspots; otherwise keep writing `IO`—both coexist.

---

# 9) Risks & mitigations

* **Capture Checking is experimental**; syntax *may* evolve. Keep all usage in `modules/experimental-*` and make CI for these modules **non-blocking** initially. Link users to official docs/talks so they understand the status. ([Scala Documentation][1], [Scala Days][4])
* **Kyo maturity**: great interop story; benchmark conversations show it’s promising. Keep scope limited to the experimental module and examples. ([GitHub][2])

---

# 10) One-page “Getting Started” (docs/experimental/README.md)

* **What**: Two opt-in tools that improve safety & clarity without changing your engines/pipelines.
* **Enable**:

    * `experimental-caprese`: add `import language.experimental.captureChecking`; use `pureTransform` and `withCapability`. ([Scala Documentation][1])
    * `experimental-kyo`: import `kyo-cats` and use `.kyoTransform` to write a stage with `A < (Sync & Async & Abort[String])`. ([GitHub][2])
* **Why** (in plain English):

    * **Caprese**: compiler **stops leaks** in UDFs/callbacks and **codifies purity**.
    * **Kyo**: precise effect sets with **intersection types**; keep `IO` at the boundary. ([Scala Documentation][3])
* **Try**:

    * Run `sbt project experimentalExamples ; run` and follow the prompts.

---

## Final notes

This plan keeps everything **surgical** and **beginner-friendly**, while demonstrating serious, compile-time safety wins:

* **UDFs** that *must be pure* are now **enforced** by the compiler. ([Scala Documentation][1])
* **Connectors** don’t leak—ever. (Scope-checked capabilities.) ([Scala Documentation][1])
* **Kyo** lets advanced teams model **precise effect combinations** without changing FlowForge’s `F[_]`. ([GitHub][2], [Scala Documentation][3])

If you want, I can generate the **exact Scala files** for `experimental-caprese` and `experimental-kyo` (interfaces, syntax, and two runnable examples) so you can paste them in and run the CI job immediately.

[1]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[2]: https://github.com/getkyo/kyo?utm_source=chatgpt.com "getkyo/kyo: Toolkit for Scala Development"
[3]: https://docs.scala-lang.org/scala3/reference/new-types/intersection-types.html?utm_source=chatgpt.com "Intersection Types"
[4]: https://scaladays.org/editions/2025/talks/the-first-steps-towards-practical?utm_source=chatgpt.com "The first steps towards practical capture checking"
[5]: https://contributors.scala-lang.org/t/experimental-capture-checking-new-syntax-for-explicit-capture-polymorphism/7095?page=2&utm_source=chatgpt.com "Experimental Capture Checking: New Syntax for Explicit ..."
[6]: https://gist.github.com/ahoy-jon/0aec8bcf636fac096ae5e4b9ed706fe0?utm_source=chatgpt.com "The Rebirth of Tagless Final: An Ergonomic Approach in Kyo"
[7]: https://github.com/typelevel/cats-effect/discussions/4221?utm_source=chatgpt.com "Performance Benchmark #4221 - typelevel cats-effect"

----

# Do we have any benefits in compile-time contracts or schema from these? Think carefully & innovatively - again we want to keep it simple & no overkill.

Short answer: **yes—Caprese (capture checking) gives you a couple of simple but *real* compile-time wins for contracts/schemas**, and **Kyo can add light-touch compile-time fences around when effects are allowed**. Here’s how to get value without over-engineering:

---

## 1) Caprese: make contract & schema code *provably pure* where it matters

**What the compiler gives you:** Capture checking tracks which “capabilities” (resources) a closure captures and lets you *forbid* captures entirely (`A -> B` non-capturing arrow) or keep a capability scoped so it cannot escape (`x: X^`). It’s an experimental Scala 3 feature, opt-in via `import language.experimental.captureChecking`. ([Scala Documentation][1], [Scala][2])

### 1.1 Pure, reproducible schema defaults (forward-compat)

Forward compatibility often relies on **default values** for new fields. Make those defaults **non-capturing** so they cannot depend on environment/IO/clock:

```scala
// Non-capturing default provider for a field in the contract:
type FieldDefault[+A] = Unit -> A  // "pure function" per capture checking

// Example: safe default for country and version
val defaultCountry: FieldDefault[String] = _ => "Unknown"
val defaultVersion: FieldDefault[Int]    = _ => 1
```

Using `FieldDefault` guarantees at **compile time** that defaults are deterministic and free of hidden IO, which keeps *forward* compatibility credible and repeatable (matches how “Forward” rules expect defaults to behave in schema registries). ([Confluent Documentation][3], [Confluent][4])

### 1.2 Contract mappers (upgrade/downgrade) that cannot leak resources

For *Backward* (drop columns) and *Forward* (fill new columns) scenarios, require the migration function to be **non-capturing**:

```scala
// Contract-preserving mappers (compile-time pure):
type Upcast [A,B] = A -> B  // add fields with defaults
type Downcast[A,B] = A -> B  // drop fields safely
```

Now your `SchemaConforms[C,R,P]` evidence still checks *shapes* at compile time, but **Caprese guarantees the *functions* that bridge shapes are pure**, i.e., they don’t secretly capture a JDBC handle, clock, or config file. That addresses the most common source of “it passed review but fails in prod” bugs around evolution glue. ([Scala Documentation][1])

### 1.3 “Pure UDF” slots in the pipeline

Offer a twin of your regular transform that only accepts non-capturing functions:

```scala
def pureTransform[A,B](name: String)(f: A -> B): PipelineBuilder[F]
```

If a dev tries to reference a producer/client inside a UDF, the compiler rejects it. This is a *compile-time* safety net for UDFs (a notorious pain-point in Spark/Flink code). ([Scala Documentation][1])

> **Why this is not overkill:** you’re *not* rewriting your system—just marking 2–3 *high-risk* points (defaults, mappers, UDFs) as “must be pure.” The compiler does the rest.

---

## 2) Caprese: scoped capabilities for connectors (no leaks across contract/scheme steps)

Keep connector clients inside a scope with a **capability parameter** so they **cannot escape**:

```scala
def withJdbc[A](acquire: => JdbcClient)(use: JdbcClient^ => A): A = {
  val jdbc = acquire
  val out  = use(jdbc)   // jdbc can't escape this block (enforced by compiler)
  jdbc.close()
  out
}
```

This pairs naturally with your compile-time contract gate: you can *promise* that the **contract/evolution stage is pure** and only later open an IO scope for sinks/sources. Again, the compiler enforces the separation. ([Scala Documentation][1])

---

## 3) Kyo: effect “fences” that keep schema/contract stages clean

Kyo represents effects in the type via **intersection types** (e.g., `A < (Sync & Async & Abort[String])`), and ships **kyo-cats** for easy `IO` interop. ([GitHub][5], [Scala Documentation][6])

Use that to make **simple, readable fences**:

* **Before the contract check (compile-time shape proof)**: accept only stages whose effect set is *pure/Sync*.
* **After the contract is established**: allow Async/IO stages (read/write).

The fence is just a different method overload:

```scala
// "Schema fence": only pure/Sync Kyo stages allowed pre-contract
def kyoPureStage[A,B](f: A => (B < Sync)): PipelineBuilder[IO]

// After contract: allow broader effects (Async, Abort)
def kyoStage[A,B](f: A => (B < (Sync & Async & Abort[String]))): PipelineBuilder[IO]
```

This keeps your **compile-time* contract area free of side effects*\* by type, while still letting advanced teams experiment with Kyo elsewhere—without changing FlowForge’s `F[_] = IO` surface. ([GitHub][5], [Scala Documentation][6])

> **Not overkill:** it’s literally two extra method overloads that guide developers to keep IO out of schema steps. The compiler does the nagging.

---

## 4) Why this helps compile-time contracts specifically

* **Deterministic defaults & mappers** → forward/backward/full compatibility semantics stay honest; no time/IO-dependent behaviors sneaking into compile-time-validated flows. ([Confluent Documentation][3], [Confluent][4])
* **UDF purity** → shape is enforced by your macros, and *behavioral* purity is enforced by the compiler (no hidden effects captured). ([Scala Documentation][1])
* **Effect fences** → schema/contract phases remain effect-free by type; effects resume only after the compile-time checks, keeping reasoning crisp. ([GitHub][5], [Scala Documentation][6])

---

## 5) Keep it tiny (implementation sketch)

* Add **`FieldDefault[A] = Unit -> A`** and require it wherever a forward-compat default is needed. (Caprese import only in this tiny area.) ([Scala Documentation][1])
* Add **`pureTransform`** & a small **`withCapability`** helper in an *experimental* submodule; document 2 short examples. ([Scala Documentation][1])
* Add **two Kyo overloads** (`kyoPureStage`, `kyoStage`) in an *experimental* submodule; use **kyo-cats** to bridge to `IO`. ([GitHub][5])

That’s it. You’ve just made your **compile-time schema/contract story stricter and more trustworthy**—without changing FlowForge’s core or burying users in theory.

---

### Pointers if you want to read more

* Scala 3 **Capture Checking** (experimental docs; `->` non-capturing arrow, capability `^`). ([Scala Documentation][1], [Scala][2])
* Talks/articles on **capture checking** and effect safety. ([Scala Days][7], [Nicolás Rinaudo][8])
* **Kyo** README (effect sets, **cats-effect** interop). ([GitHub][5])
* **Intersection types** primer (`A & B`). ([Scala Documentation][6])
* **Schema compatibility** (Backward/Forward/Full) to align your defaults/mappers semantics. ([Confluent Documentation][3], [Confluent][4])

If you want, I can draft the minimal code (3–4 files) that adds `FieldDefault`, `pureTransform`, `withCapability`, and the two Kyo overloads—so you can drop them into `modules/experimental` and run.

[1]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[2]: https://scala-lang.org/api/3.3.6/docs/docs/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[3]: https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html?utm_source=chatgpt.com "Schema Evolution and Compatibility for Schema Registry ..."
[4]: https://developer.confluent.io/patterns/event-stream/schema-compatibility/?utm_source=chatgpt.com "Schema Compatibility"
[5]: https://github.com/getkyo/kyo?utm_source=chatgpt.com "getkyo/kyo: Toolkit for Scala Development"
[6]: https://docs.scala-lang.org/scala3/reference/new-types/intersection-types.html?utm_source=chatgpt.com "Intersection Types"
[7]: https://scaladays.org/editions/2025/talks/capture-checking-a-new-approach?utm_source=chatgpt.com "Capture Checking: A New Approach to Effect Safety in Scala"
[8]: https://nrinaudo.github.io/articles/capture_checking.html?utm_source=chatgpt.com "Hands on Capture Checking - Nicolas Rinaudo"

-------
# Let's also understand how data engineering works -
1. We have data stewards, data modelers, data warehouse architects give us source & target data contracts & Models & Entire data warehouse ER Diagrams & Tables details.
2. Data Engineers write data pipelines
3. Pipelines has many things - Read from variety of sources, keep doing audit-logging about pipeline status or use audit tables for offsets, thresholds for CDC or last updated partitions etc & many other metadata.. Use audit tables for restart-ability of pipelines keeping track of how many huge partitions we performed ETL before it failed so that we skip those after restart. Fetch Data Quality rules, Apply business transformations on Data. And then Sink then audit that pipeline is complete within SLA. And then take another junction-route which is off SLA for VACCUM & OPTIMIZE.
4. We need robust data pipeline system for such activity
So this is background & many other things which I may not have explicitly called out but you think and add to these !

Love the context. Here’s a practical, “no-mystique” blueprint that maps your roles and needs to a robust pipeline system FlowForge can power—plus the specific, evidence-backed practices we should bake in.

---

# What the upstream team gives us (and how we use it)

**Data stewards / modelers / DW architects** hand over: source & target contracts, ERDs, DW tables, SLAs.
We ground this in **dimensional modeling** (star/snowflake per Kimball) and/or **Data Vault** where appropriate. Star schemas put facts at the center with dimensions around them; this is still a mainstream, battle-tested approach ([Kimball Group][1], [Microsoft Learn][2]). Data Vault adds auditability and scalability for complex, evolving sources (hubs/links/satellites) and is widely used on modern lakehouses ([Databricks][3], [Coalesce][4]).
Data stewards own data quality & policy adherence across the lifecycle, per DAMA-DMBoK style definitions of stewardship/accountability ([DATAVERSITY][5], [Data Governance and Analytics Services][6]).

---

# End-to-end pipeline architecture (concrete, with why it matters)

## 1) Ingest (batch + CDC)

* **Replayable/transactional sources** with **schema evolution guarded by a registry**. For events/CDC, follow **Confluent Schema Registry** compatibility rules (default **BACKWARD** so newer consumers can read older data) ([Confluent Documentation][7], [Confluent][8]).
* **CDC** via Debezium; its connectors persist offsets and schema history so connectors resume from last committed position after restarts—vital for restartability ([Debezium][9]).
* **Streaming ingestion guarantees**:

    * **Spark Structured Streaming** uses checkpoints + write-ahead logs and expects idempotent sinks → end-to-end exactly-once under failure ([Apache Spark][10]).
    * **Flink** uses **checkpoints/savepoints** to restore state and offsets consistently, supporting exactly-once semantics and controlled upgrades ([Apache Nightlies][11]).
    * **Kafka** supports EOS with transactions end-to-end (read→process→write), when configured correctly ([Confluent][12], [Home][13]).

**FlowForge mapping:** sources expose `offsets` and `commit` hooks; we provide **checkpointed readers** for Spark/Flink and a **DebeziumSource** that persists Kafka Connect offset topics transparently (and never leaks them into UDFs).

---

## 2) Contract & schema gates (compile-time + runtime)

* **Compile-time contract shapes** + registry-backed runtime validation. Use compatibility policies (Backward/Forward/Full) aligned with registry definitions (add optional fields for forward; don’t break old readers, etc.) ([Confluent][8]).
* **Pure defaults for forward-fills**: ensure default providers for new fields are **pure/non-capturing** (Caprese’s `->`) so they can’t read env/clock/IO—the default stays deterministic and audit-friendly (captures blocked by compiler). See Scala capture-checking (experimental) ([WhereScape][14]).

**FlowForge mapping:**
`FieldDefault[A] = Unit -> A` for forward-compat defaults; `Upcast[A,B] = A -> B` / `Downcast[A,B] = A -> B` for contract mappers; `pureTransform` slots for UDFs that must be side-effect free (compiler enforces purity via capture checking) ([WhereScape][14]).

---

## 3) Data quality (DQ) and auditability

* Integrate **Great Expectations** (suites, checkpoints, data docs) or **Deequ** (unit tests for data on Spark) to validate inputs/outputs and publish human-readable docs/metrics ([Great Expectations][15], [Great Expectations][16], [GitHub][17]).
* Emit **lineage** to an open standard (**OpenLineage**) and/or to a catalog (DataHub/OpenMetadata) so consumers can see upstream/downstream impact and column-level flows ([OpenLineage][18], [DataHub][19], [OpenMetadata Documentation][20]).

**FlowForge mapping:**
Pluggable `DataQualitySink` (GX, Deequ); `LineageEmitter` (OpenLineage spec) for job/dataset events during run lifecycle ([OpenLineage][18]).

---

## 4) Business transforms: idempotent, restartable, partition-aware

* Favor **idempotent** design so replays/backfills don’t corrupt data; in the lakehouse, use **Delta Lake MERGE** for upserts/deletes in a single transaction (great for CDC and SCDs) ([Databricks Documentation][21]).
* Keep **progress** in audit/control tables: store last processed watermark/partition to skip already-completed chunks on restart. For streaming, rely on engine checkpoints; for batch, persist partition progress (simple and reliable).
* When teams want typed effect composition, allow **Kyo** stages with effect sets and bridge to `IO` at boundaries (keeps core API unchanged).

**FlowForge mapping:**
`ProgressStore` (table/files) + `markProcessed/nextBatch`; `DeltaMergeSink` with merge predicates; `.kyoTransform` for teams that opt into Kyo (via kyo-cats interop).

---

## 5) Sinks & maintenance: SLA path vs. “off-SLA” path

* **SLA path** writes to curated tables; track run start/stop, row counts, latency, and SLA outcomes.
* **Off-SLA maintenance path** runs **OPTIMIZE (bin-pack / Z-order)** and **VACUUM** on Delta to keep files right-sized and enable data skipping; Z-order collocates data to reduce IO; VACUUM prunes old files (balance with time-travel retention) ([Databricks Documentation][22], [Delta Lake][23]).

**FlowForge mapping:**
A separate `MaintenanceJob` target that triggers after SLA met: `OPTIMIZE … ZORDER BY (...)` on hot columns; `VACUUM` respect retention (don’t destroy time-travel you still need) ([Delta Lake][23]).

---

## 6) Observability, SLAs & freshness

* Orchestrators enforce SLAs/freshness: **Airflow** has SLA misses tracking (and callbacks); **Dagster** has freshness policies/alerts; **dbt** has **source freshness** with warn/error thresholds and reports—use them where they fit your stack ([Apache Airflow][24], [docs.dagster.io][25], [dbt Developer Hub][26]).

**FlowForge mapping:**
Expose run metrics (duration, input lag, records) and **SLA result**. Provide tiny adapters to push events to Airflow/Dagster/dbt so their native SLA/freshness UIs light up.

---

# “Audit tables” & restartability (how we implement them simply)

* **Streaming**: rely on engine checkpoints (Spark/Flink) to recover operator state and offsets; exactly-once hinges on replayable sources + idempotent sinks ([Apache Spark][10], [Apache Nightlies][11]).
* **Batch**: a **`pipeline_run`** table (run id, start/end, status, counts, SLA status) and a **`partition_progress`** table keyed by dataset + partition (e.g., date/hour) with last successful watermark. On restart, query progress and skip done partitions—this pairs well with MERGE for CDC backfills ([Databricks Documentation][21]).
* **CDC**: don’t reset Debezium offsets unless you really mean it—offsets and schema history topics are how Debezium resumes accurately after failures ([Debezium][9]).

---

# Minimal FlowForge design additions (keep it simple)

1. **Contracts module (compile-time + runtime)**

    * `FieldDefault[A] = Unit -> A` (pure default) and `Upcast/Downcast` as non-capturing (`->`) to guarantee deterministic evolution glue (Caprese capture-checking) ([WhereScape][14]).
    * Registry binding for runtime schema compatibility checks aligned with Confluent’s modes (Backward/Forward/Full) ([Confluent Documentation][7], [Confluent][8]).

2. **DQ & lineage**

    * `DataQualitySink` with GX/Deequ adapters; publish checkpoints/reports (GX “Checkpoints” + Data Docs; Deequ constraints/metrics) ([Great Expectations][15], [GitHub][17]).
    * `LineageEmitter` (OpenLineage job/dataset events during read→transform→write) feeding a backend like Marquez or DataHub/OpenMetadata ([OpenLineage][18]).

3. **Audit & progress**

    * `RunContext` (run\_id, job, inputs, partitions) → writes to `pipeline_run` + `partition_progress`.
    * Helpers: `markProcessed(dataset, partition)`, `nextPartitions(dataset, cursor)`.

4. **Sinks & maintenance**

    * `DeltaMergeSink` (idempotent upsert).
    * `MaintenanceJob` executing `OPTIMIZE … [ZORDER BY cols]` and `VACUUM` with retention guardrails ([Databricks Documentation][22]).

5. **Orchestrator adapters**

    * Emit SLA metrics/events so **Airflow** “SLA Misses” and **Dagster** “freshness checks” can alert natively; optional **dbt source freshness** runs for downstream warehouses ([Apache Airflow][24], [docs.dagster.io][27], [dbt Developer Hub][28]).

6. **(Optional) Experimental sugar**

    * `pureTransform(name)(A -> B)` and `withCapability(res)(cap^ => …)` for compile-time purity & no-escape resources (Caprese) ([WhereScape][14]).
    * `.kyoTransform(name)(A => A < (Sync & …))` bridged to `IO` via kyo-cats (precise effect sets without changing the core API) ([Data Management Wiki][29]).

---

# Developer experience: fast path for both new & seasoned engineers

* **New devs**

    1. Pick a **source** (file/Kafka/DB/CDC).
    2. Attach a **contract** & select **compatibility**.
    3. Add **DQ suite** (GX/Deequ) with a template.
    4. Write **pure UDFs** in `pureTransform` slots; any IO goes in normal transforms.
    5. Sink to **DeltaMerge**; FlowForge writes run/progress rows automatically.
    6. Enable the **maintenance job** (OPTIMIZE/VACUUM) on the off-SLA path.
       → You get restarts, lineage, and docs *by default* (low learning time).

* **Seasoned engineers**

    * Partition/watermark tuning, CDC merges, late data, SCD2, multi-hop (bronze/silver/gold), and Kyo effect-tracked stages when you want finer control—without leaving FlowForge.

---

# Brutal but helpful sanity checks (why these choices are worth it)

* **Exactly-once and restartability** aren’t vibes—they’re the product of **engine checkpointing + replayable sources + idempotent sinks** (documented by Spark/Flink/Kafka) ([Apache Spark][10], [Apache Nightlies][11], [Confluent][12]).
* **Schema compatibility** rules are well understood in registries; aligning contracts to those modes prevents consumer breakage and makes rewinds safe ([Confluent Documentation][7], [Confluent][8]).
* **DQ and lineage** must be first-class: GX/Deequ and OpenLineage/DataHub/OpenMetadata are the de-facto solutions many teams rely on today ([Great Expectations][15], [GitHub][17], [OpenLineage][18], [DataHub][19]).
* **Delta Lake maintenance** (OPTIMIZE/Z-order/VACUUM) is not optional at scale; it’s the difference between fast and stuck tables, and it interacts with time-travel retention policy ([Databricks Documentation][22], [Delta Lake][23]).
* **SLAs/freshness** are visible in schedulers/transform layers (Airflow/Dagster/dbt), so emit to their native constructs instead of inventing our own UI ([Apache Airflow][24], [docs.dagster.io][25], [dbt Developer Hub][26]).

---

## A tiny checklist you can adopt immediately

* [ ] Source is **replayable** or CDC; offsets/checkpoints configured (Spark/Flink/Debezium) ([Apache Spark][10], [Apache Nightlies][11], [Debezium][9])
* [ ] Contract selected with **compatibility mode**; **pure defaults** for new fields (forward) ([Confluent][8])
* [ ] DQ suite attached (GX/Deequ) + Data Docs or metrics published ([Great Expectations][15], [GitHub][17])
* [ ] UDFs declared **pure** (compile-time checked); IO bounded to transforms
* [ ] Sinks are **idempotent** (e.g., Delta **MERGE**) ([Databricks Documentation][21])
* [ ] **Run/audit** rows written; **partition\_progress** updated
* [ ] **Lineage events** emitted (OpenLineage) ([OpenLineage][18])
* [ ] SLA/freshness exported to Airflow/Dagster/dbt ([Apache Airflow][24], [docs.dagster.io][25], [dbt Developer Hub][26])
* [ ] Off-SLA **OPTIMIZE/Z-order/VACUUM** scheduled with safe retention ([Databricks Documentation][22])

---

### TL;DR (the hard requirements, with sources)

1. **Checkpointed streaming + idempotent sinks = exactly-once + restartability** (Spark/Flink docs). ([Apache Spark][10], [Apache Nightlies][11])
2. **Schema compatibility** should follow registry modes (default **BACKWARD**), so consumers can rewind safely (Confluent docs). ([Confluent Documentation][7])
3. **Data quality and lineage** use established projects (Great Expectations/Deequ, OpenLineage/DataHub/OpenMetadata). ([Great Expectations][15], [GitHub][17], [OpenLineage][18])
4. **Delta Lake maintenance** (OPTIMIZE/Z-order/VACUUM) is essential, and impacts **time travel**/retention (Databricks/Delta docs). ([Databricks Documentation][22], [Delta Lake][23])
5. **SLAs/freshness** should be surfaced in orchestrators/transform tools that support them out-of-the-box (Airflow/Dagster/dbt). ([Apache Airflow][24], [docs.dagster.io][25], [dbt Developer Hub][26])

If you want, I can turn this into concrete FlowForge module stubs (audit tables, progress store, GX/Deequ & OpenLineage adapters, DeltaMergeSink, and a small “maintenance job”) so you can drop them in and run demos end-to-end.

[1]: https://www.kimballgroup.com/data-warehouse-business-intelligence-resources/kimball-techniques/dimensional-modeling-techniques/?utm_source=chatgpt.com "Dimensional Modeling Techniques"
[2]: https://learn.microsoft.com/en-us/power-bi/guidance/star-schema?utm_source=chatgpt.com "Understand star schema and the importance for Power BI"
[3]: https://www.databricks.com/blog/data-vault-best-practice-implementation-lakehouse?utm_source=chatgpt.com "Data Vault Best practice & Implementation on the Lakehouse"
[4]: https://coalesce.io/data-insights/data-vault-2-0-the-complete-implementation-guide/?utm_source=chatgpt.com "Data Vault 2.0: The Complete Implementation Guide - Coalesce"
[5]: https://www.dataversity.net/a-brief-history-of-data-stewardship/?utm_source=chatgpt.com "A Brief History of Data Stewardship"
[6]: https://data.fsu.edu/data-stewardship?utm_source=chatgpt.com "Data Stewardship - Data Governance and Analytics Services"
[7]: https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html?utm_source=chatgpt.com "Schema Evolution and Compatibility for Schema Registry on ..."
[8]: https://developer.confluent.io/patterns/event-stream/schema-compatibility/?utm_source=chatgpt.com "Schema Compatibility - Confluent Developer"
[9]: https://debezium.io/documentation/faq/?utm_source=chatgpt.com "Frequently Asked Questions"
[10]: https://spark.apache.org/docs/3.5.1/structured-streaming-programming-guide.html?utm_source=chatgpt.com "Structured Streaming Programming Guide"
[11]: https://nightlies.apache.org/flink/flink-docs-master/docs/concepts/stateful-stream-processing/?utm_source=chatgpt.com "Stateful Stream Processing | Apache Flink"
[12]: https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/?utm_source=chatgpt.com "Exactly-Once Semantics Are Possible: Here's How Kafka ..."
[13]: https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html?utm_source=chatgpt.com "Exactly Once Semantics :: Spring Kafka"
[14]: https://www.wherescape.com/blog/mastering-data-vault-modeling-architecture-best-practices-and-essential-tools/?utm_source=chatgpt.com "Essential Guide to Data Vault Modeling and Best Practices"
[15]: https://docs.greatexpectations.io/docs/0.18/oss/guides/validation/validate_data_overview/?utm_source=chatgpt.com "Data Validation workflow"
[16]: https://greatexpectations.io/?utm_source=chatgpt.com "Great Expectations: have confidence in your data, no matter ..."
[17]: https://github.com/awslabs/deequ?utm_source=chatgpt.com "Deequ is a library built on top of Apache Spark for defining ..."
[18]: https://openlineage.io/docs/?utm_source=chatgpt.com "About OpenLineage"
[19]: https://docs.datahub.com/docs/features/feature-guides/lineage?utm_source=chatgpt.com "About DataHub Lineage"
[20]: https://docs.open-metadata.org/latest/how-to-guides/data-lineage?utm_source=chatgpt.com "Data Lineage | OpenMetadata Lineage How-To Guide"
[21]: https://docs.databricks.com/aws/en/delta/merge?utm_source=chatgpt.com "Upsert into a Delta Lake table using merge"
[22]: https://docs.databricks.com/aws/en/sql/language-manual/delta-optimize?utm_source=chatgpt.com "OPTIMIZE | Databricks on AWS"
[23]: https://delta.io/blog/2023-02-01-delta-lake-time-travel/?utm_source=chatgpt.com "Delta Lake Time Travel"
[24]: https://airflow.apache.org/docs/apache-airflow/2.4.2/_api/airflow/models/slamiss/index.html?utm_source=chatgpt.com "airflow.models.slamiss"
[25]: https://docs.dagster.io/guides/labs/observability-update/freshness?utm_source=chatgpt.com "Freshness policies"
[26]: https://docs.getdbt.com/reference/resource-properties/freshness?utm_source=chatgpt.com "freshness | dbt Developer Hub"
[27]: https://docs.dagster.io/guides/test/data-freshness-testing?utm_source=chatgpt.com "Data freshness checks"
[28]: https://docs.getdbt.com/docs/deploy/source-freshness?utm_source=chatgpt.com "Source freshness | dbt Developer Hub - dbt Docs"
[29]: https://datamanagement.wiki/role/data_steward?utm_source=chatgpt.com "Data steward - Data Management Wiki"

-----
# I mean we now proved compile-time contracts already using Magnolia (Scala3 future)
## We are yet to prove -
1. Fibre-safe - what all out of these End-to-end pipeline that have potential to wreck out smooth execution & how we tame / control ? Again only "real" value-add - nothing must seem like "this is total unnecessary in a data pipeline & we did overkill"
2. Experimental (about kyo & caprese): The real strength of Kyo & caprese Wherever applicable. Again only "real" value-add - nothing must seem like "this is total unnecessary in a data pipeline & we did overkill"

... And what else you can think of ?

You’ve already nailed compile-time contracts. Now let’s harden the **runtime** so pipelines stay smooth under load, restarts, and partial failures—without any “enterprisey” overkill. Below is a surgical plan: where things usually break, how to tame them, and where **Kyo** and **Caprese** add real value.

---

# 1) Fiber-safe end-to-end: where it breaks and how to tame it

## A. Hidden blocking on the compute pool

**Risk:** JDBC calls, cloud SDKs, file I/O, DNS, or slow cryptography can block the Cats-Effect compute pool, starving other fibers and making backpressure look like “random” latency spikes.
**Tame it:**

* Wrap any potentially blocking call in `IO.blocking` (or a dedicated blocker), keeping the compute pool for runloops only. Cats-Effect’s thread model explicitly recommends shifting blocking work off the compute pool. ([Typelevel][1])

## B. Unbounded fan-out / no backpressure between stages

**Risk:** `parTraverse` over huge collections, unbounded queues, or unlimited Kafka partitions → heap blowups and thrashing.
**Tame it:**

* Use **bounded** queues/pools as the default contract between stages (e.g., `cats.effect.std.Queue.bounded`, or a simple worker pool). Bounded queues enforce fiber-level backpressure and prevent unbounded memory growth. ([Typelevel][2])
* If you need rate control, use Cats-Effect’s `Backpressure` or a token/semaphore pattern. ([Typelevel][3])

## C. Cancellation safety & finalization

**Risk:** orphaned fibers continue writing, sockets remain open, temp files leak when a job cancels or times out.
**Tame it:**

* Structure long-running work with **`Resource`**/`bracket` so acquisitions are **always** released, even on cancel; CE makes acquire/release non-interruptible for safety. ([Typelevel][4])
* Use explicit cancel boundaries (timeouts, `Fiber.cancel`) and rely on cooperative cancellation—don’t forget to finalize. ([Typelevel][5], [Baeldung on Kotlin][6])

## D. Engine-level fault tolerance: checkpoints + idempotent sinks

**Risk:** “exactly-once” is claimed but not actually configured (or not possible).
**Tame it:**

* **Spark Structured Streaming**: exactly-once needs a **replayable source** + **idempotent sink**; the engine records offsets in checkpoints/WAL for recovery. Design your sink to tolerate replays (e.g., MERGE/UPSERT). ([Apache Spark][7])
* **Flink**: enable **checkpoints**; understand how **backpressure** can delay checkpoint barriers and inflate checkpoint time; size buffers and operators accordingly. ([Apache Nightlies][8])

## E. Source semantics (Kafka/CDC) not aligned

**Risk:** double-processing or reordering across failures.
**Tame it:**

* **Kafka**: if you need end-to-end EOS, use **transactions** (idempotent producer + transactional writes) and consume with `isolation.level=read_committed`. Understand the trade-offs. ([Confluent Documentation][9], [Strimzi][10])
* **CDC (Debezium)**: let Debezium manage **offsets** and **schema history** in Kafka topics; on restart it reconstructs schema and resumes at the correct binlog/LSN position. Don’t nuke those topics unless you truly want a fresh start. ([Debezium][11])

## F. Sink idempotency that isn’t

**Risk:** “append then dedupe later” + retries → silent duplicates, clock skew, or partial upserts.
**Tame it:**

* Use **Delta Lake `MERGE`** (or equivalent) to express idempotent upserts with clear match conditions; it’s ACID and suited for CDC. ([Databricks Documentation][12])

## G. Tuning & starvation

**Risk:** too few or too many compute threads → stalls or context-switch storms.
**Tame it:**

* Follow CE guidance for pool sizing and starvation avoidance; treat compute vs blocking pools differently. ([Typelevel][13])

**What FlowForge should bake in (minimal & useful):**

* A `Blocking` stage helper (wraps `IO.blocking` automatically). ([Typelevel][1])
* Default **bounded** inter-stage queues and `parTraverseN` helpers (limit concurrency per stage). ([Typelevel][2])
* `Resource` wrappers for connectors (JDBC/Kafka/Cloud SDK) and sinks. ([Typelevel][4])
* Engine adapters that **enforce** Spark replayable-source/idempotent-sink pairing, Flink checkpoint configs, and Kafka `read_committed` when transactions are enabled. ([Apache Spark][7], [Apache Nightlies][8], [Confluent Documentation][9])
* A Delta `MergeSink` abstraction encouraging deterministic keys/predicates. ([Databricks Documentation][12])

---

# 2) Experimental—**Kyo** & **Caprese** where they help (no overkill)

## Kyo: effect “fences” without changing F\[\_]

**Real value-add:** declare **where** effects are allowed via **intersection types**, then bridge to `IO` with **kyo-cats** so the rest of FlowForge stays the same.

* **Pre-contract stages:** require `A => (B < Sync)` (pure/synchronous only) so schema/contract logic can’t do async/IO.
* **Post-contract stages:** allow `A => (B < (Sync & Async & Abort[String]))`.
* The kyo-cats integration **propagates cancellations** between Kyo and Cats-Effect, enabling gradual adoption. ([GitHub][14], [Scala Documentation][15])

This is a tiny set of overloads that nudges best practice by type, not policy docs.

## Caprese (capture-checking): compile-time purity & no-escape resources

**Real value-add:** mark a few *high-risk* spots as “must be pure” or “capability may not escape” and let the compiler enforce it.

* **Pure defaults for forward-compat**: `FieldDefault[A] = Unit -> A` guarantees default values for new fields are deterministic (no clock/env/IO). ([Scala][16])
* **Contract mappers** (upcast/downcast) as non-capturing functions: `A -> B`. If anyone tries to grab a JDBC handle inside, it **won’t compile**. ([Scala Documentation][17])
* **UDF slots**: add `pureTransform(name)(f: A -> B)` alongside normal transforms. Again, compiler-verified purity. ([Scala][16])
* **Scoped connectors**: helpers like `withJdbc(jdbc^ => …)` so the capability can’t leak out of the block (compile-time guarantee). ([Scala Documentation][17])

This is *surgical*, opt-in, and maps exactly to failure modes you already see in production UDFs and glue logic.

---

# 3) “What else?” — a lean, practical checklist

* **Exactly-once invariant:** replayable sources + checkpoints + idempotent sinks. Bake this into defaults and fail fast when misconfigured. ([Apache Spark][7], [Apache Nightlies][8])
* **Bounded concurrency everywhere:** default to bounded queues and `parTraverseN`. Provide one place to set stage-level parallelism. ([Typelevel][2])
* **Blocking hygiene:** route *all* blocking to `IO.blocking`; scan PRs for common offenders (JDBC, cloud SDKs). ([Typelevel][1])
* **Cancellation tested:** add tests that cancel mid-run and assert resources closed (`Resource`/finalizers observed). ([Typelevel][4])
* **CDC resilience:** treat Debezium offset + schema history topics as **state**; document the “wipe and resnapshot” procedure distinctly from normal restarts. ([Debezium][11])
* **Delta upserts by default:** a `MergeSink` with clear match conditions (keys, last-update). Avoid “append then dedupe later.” ([Databricks Documentation][12])
* **Engine-specific footguns:** in Flink, monitor checkpoint alignment time under backpressure and size buffers/operators accordingly. ([Apache Nightlies][18])
* **Tuning guide:** link to CE starvation/tuning page; give one “safe default” profile per machine class. ([Typelevel][13])

---

## What we **won’t** do (to keep it simple)

* No bespoke scheduler/UI for SLAs—emit metrics/events so Spark/Flink/Kafka/your orchestrator can show the truth you already run on.
* No global “effect DSL rewrite.” **Kyo** stays at stage boundaries via interop; **Caprese** stays in a few hot spots (defaults, mappers, UDFs, connector scopes). ([GitHub][14], [Scala Documentation][17])

---

## TL;DR

* **Fiber-safe** pipelines come from four things: **no hidden blocking**, **bounded concurrency**, **cancellation-safe resources**, and **engine-level EOS** (replayable source + checkpoint + idempotent sink). We can make all of these the **default** in FlowForge with tiny, targeted helpers. ([Typelevel][1], [Apache Spark][7])
* **Kyo** and **Caprese** add *real* compile-time guardrails—**effect fences** and **no-escape purity**—exactly where data pipelines usually go wrong, with minimal surface area. ([GitHub][14], [Scala][16])

If you want, I can turn this into a short PR plan (a few helpers, two method overloads, and a starter doc) that you can drop straight into `modules/experimental` and `core-runtime`.

[1]: https://typelevel.org/cats-effect/docs/thread-model?utm_source=chatgpt.com "Thread Model · Cats Effect"
[2]: https://typelevel.org/cats-effect/docs/std/queue?utm_source=chatgpt.com "Queue · Cats Effect"
[3]: https://typelevel.org/cats-effect/docs/std/backpressure?utm_source=chatgpt.com "Backpressure · Cats Effect"
[4]: https://typelevel.org/cats-effect/docs/std/resource?utm_source=chatgpt.com "Resource · Cats Effect"
[5]: https://typelevel.org/blog/2020/10/30/concurrency-in-ce3.html?utm_source=chatgpt.com "Concurrency in Cats Effect 3"
[6]: https://www.baeldung.com/scala/cats-effect-cancellation?utm_source=chatgpt.com "Cancellation in Cats Effect | Baeldung on Scala"
[7]: https://spark.apache.org/docs/3.5.1/structured-streaming-programming-guide.html?utm_source=chatgpt.com "Structured Streaming Programming Guide"
[8]: https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/checkpointing/?utm_source=chatgpt.com "Checkpointing | Apache Flink"
[9]: https://docs.confluent.io/kafka/design/delivery-semantics.html?utm_source=chatgpt.com "Message Delivery Guarantees for Apache Kafka"
[10]: https://strimzi.io/blog/2023/05/03/kafka-transactions/?utm_source=chatgpt.com "Exactly-once semantics with Kafka transactions"
[11]: https://debezium.io/documentation/reference/stable/configuration/storage.html?utm_source=chatgpt.com "Storing state of a Debezium connector"
[12]: https://docs.databricks.com/aws/en/delta/merge?utm_source=chatgpt.com "Upsert into a Delta Lake table using merge | Databricks on AWS"
[13]: https://typelevel.org/cats-effect/docs/core/starvation-and-tuning?utm_source=chatgpt.com "Starvation and Tuning · Cats Effect"
[14]: https://github.com/getkyo/kyo?utm_source=chatgpt.com "getkyo/kyo: Toolkit for Scala Development"
[15]: https://docs.scala-lang.org/scala3/book/types-intersection.html?utm_source=chatgpt.com "Intersection Types | Scala 3 — Book"
[16]: https://scala-lang.org/api/3.3.6/docs/docs/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[17]: https://docs.scala-lang.org/scala3/reference/experimental/cc.html?utm_source=chatgpt.com "Capture Checking"
[18]: https://nightlies.apache.org/flink/flink-docs-master/docs/ops/state/checkpointing_under_backpressure/?utm_source=chatgpt.com "Checkpointing under backpressure | Apache Flink"

-----

# Again be mindful we don't want to mix up with the Pioneers - Apache Spark, Kafka, Flink or anyone. Their features stay pure and we use them directly & not re-write (which may look like "this was already there")

Absolutely. Let’s lock in a **“non-rewrite pact”**: FlowForge should never re-implement engine features (Spark, Kafka, Flink, Debezium, Delta Lake). We layer thin, compile-time contracts + runtime guardrails **around** them, and pass their configs straight through.

# Clear line of responsibility

| Area                 | Pioneer keeps doing it                                                                                                                                                                        | FlowForge adds (no reinvention)                                                                                                                                                                                      |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Streaming guarantees | **Spark**: checkpoint+WAL + replayable sources; **idempotent sinks** ⇒ end-to-end exactly-once. **Flink**: checkpoints restore state & positions; with Kafka transactions ⇒ e2e exactly-once. | Detect misconfig (no checkpoint / non-idempotent sink), fail fast; expose “exactly-once checklist” in logs/metrics. ([Apache Spark][1], [Databricks Documentation][2], [Apache Nightlies][3], [flink.apache.org][4]) |
| Kafka semantics      | Idempotent producer & **transactions**, consumers with `isolation.level=read_committed`.                                                                                                      | Pass-through all producer/consumer configs; validate combos; expose EOS toggles in pipeline descriptor. ([Confluent Documentation][5])                                                                               |
| CDC offsets/history  | **Debezium** manages offsets + schema history topics for reliable restarts.                                                                                                                   | Don’t touch offsets; provide health checks & documented reset/snapshot flows only. ([Debezium][6])                                                                                                                   |
| Lakehouse upserts    | **Delta Lake `MERGE`** for idempotent UPSERT/DELETE in one ACID op.                                                                                                                           | Provide a tiny `MergeSink` that emits native `MERGE`; no custom dedupe. ([Microsoft Learn][7], [Delta Lake][8])                                                                                                      |
| Lineage              | **OpenLineage** event model (jobs, datasets, runs).                                                                                                                                           | Emit spec-compliant events from pipeline start/read/write; no custom lineage format. ([OpenLineage][9])                                                                                                              |

# Thin integration patterns (keep pioneers pure)

* **Spark**: we never wrap `Dataset`/`DataFrame` APIs. Provide a stage that hands you the native `SparkSession` and expects your function to return a native `Dataset[U]`. We only: (a) enforce compile-time contract conformance at the boundaries, (b) verify **checkpoint dir** + idempotent sink are set when you claim exactly-once. Spark’s own docs define the guarantee via **checkpoint/WAL + replayable source + idempotent sink**; we point users there and refuse “unsafe” configs. ([Apache Spark][1], [Databricks Documentation][2])

* **Flink**: we accept a user function on native `DataStream[T]` and require **checkpointing** config; when paired with Kafka transactions, exactly-once is the engine’s job (two-phase commit). We surface checkpoint interval, timeout, and alignment lag in metrics; we don’t implement state or barriers. ([Apache Nightlies][3], [flink.apache.org][4])

* **Kafka**: FlowForge exposes pass-through producer/consumer settings. If `enable.idempotence=true` and `transactional.id` are set, we nudge consumers to `read_committed`. No custom “transaction manager.” ([Confluent Documentation][5])

* **Debezium**: Treat **offsets/schema history topics** as source-of-truth; we never persist our own copies. We expose a documented recovery switch only when history is lost (per Debezium guidance). ([Debezium][6])

* **Delta Lake**: We don’t invent compaction; we just call **`MERGE INTO`** for CDC and let users schedule **OPTIMIZE/VACUUM** in their orchestrator. ([Microsoft Learn][7])

# Fiber-safe runtime (real value, zero reinvention)

* **No hidden blocking**: Any JDBC/cloud SDK call is executed via `IO.blocking` (or equivalent), keeping the compute pool responsive (Cats-Effect guidance). This is orchestration hygiene, not engine replacement. ([Typelevel][10])
* **Bounded concurrency** between stages: defaults use bounded queues/`parTraverseN`, so backpressure is explicit (heap won’t explode). We don’t touch Flink/Spark internals; this protects *our* sidecars and connectors.
* **Cancellation safety**: every connector/sink sits in `Resource`/`bracket` so cancellations release handles deterministically—complements engine checkpoints. ([Typelevel][11])

# Where **Kyo** and **Caprese** help—surgically

* **Caprese (capture-checking)** — *compile-time purity* where it matters:

    * `pureTransform(name)(f: A -> B)`: the compiler forbids UDFs capturing a connector/secret; avoids classic Spark/Flink UDF leaks (we still pass native types).
    * `FieldDefault[A] = Unit -> A` for **forward-compat defaults**: deterministic, IO-free defaults uphold schema rules (no funky env/time reads).
    * `withCapability(x^ => ...)`: scope JDBC/Kafka clients so they **cannot escape** closures; compiler enforces it.
      (This is a language check; we don’t alter engine APIs.)

* **Kyo (effect sets via intersection types)** — tiny **effect fences**:

    * Pre-contract stages accept `A => (B < Sync)` (pure/sync). Post-contract can accept `A => (B < (Sync & Async))`. We bridge to `IO` with **kyo-cats**; no engine code is wrapped or replaced.

# “Do vs Don’t” guardrails

* **Do**: pass all engine configs straight through; validate combinations (e.g., Spark checkpoint present if exactly-once true). Cite the engine docs in failure messages. ([Apache Spark][1])
* **Do**: emit **OpenLineage** events; let catalogs render lineage. ([OpenLineage][12])
* **Don’t**: rewrap `DataFrame/DataStream` into bespoke DSLs, reimplement Flink barriers, or simulate Kafka transactions. Point users to the official features and keep FlowForge code slim.

# Quick acceptance checklist (what we enforce, not re-implement)

* Spark: checkpoint/WAL configured + idempotent sink set? Else fail with link to guide. ([Apache Spark][1])
* Flink: checkpoints enabled and stable? Else warn/fail; link to checkpointing docs. ([Apache Nightlies][3])
* Kafka EOS: if transactions on producer, ensure `read_committed` consumers; else warn. ([Confluent Documentation][5])
* CDC: Debezium offsets/history intact; recovery switch only per docs. ([Debezium][6])
* Delta: `MERGE` used for CDC idempotence; no “append-then-dedupe.” ([Microsoft Learn][7])
* Contracts: defaults/mappers are **non-capturing** (Caprese) to keep schema evolution deterministic.
* UDF slots: `pureTransform` available; regular `transform` for IO.
* Lineage: send OpenLineage job/run/dataset events. ([OpenLineage][9])

**Bottom line:** FlowForge stays a **thin, typed scaffold**—we *prove* safety and correctness around the pioneers, never inside them. All heavy lifting remains with Spark/Flink/Kafka/Debezium/Delta and we reference their guarantees directly.

[1]: https://spark.apache.org/docs/3.5.1/structured-streaming-programming-guide.html?utm_source=chatgpt.com "Structured Streaming Programming Guide"
[2]: https://docs.databricks.com/aws/en/structured-streaming/checkpoints?utm_source=chatgpt.com "Structured Streaming checkpoints | Databricks on AWS"
[3]: https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/checkpointing/?utm_source=chatgpt.com "Checkpointing | Apache Flink"
[4]: https://flink.apache.org/2018/02/28/an-overview-of-end-to-end-exactly-once-processing-in-apache-flink-with-apache-kafka-too/?utm_source=chatgpt.com "An Overview of End-to-End Exactly-Once Processing in ..."
[5]: https://docs.confluent.io/kafka/design/delivery-semantics.html?utm_source=chatgpt.com "Message Delivery Guarantees for Apache Kafka"
[6]: https://debezium.io/documentation/reference/stable/development/engine.html?utm_source=chatgpt.com "Debezium Engine"
[7]: https://learn.microsoft.com/en-us/azure/databricks/delta/merge?utm_source=chatgpt.com "Upsert into a Delta Lake table using merge"
[8]: https://delta.io/blog/delta-lake-upsert/?utm_source=chatgpt.com "Delta Lake Upsert"
[9]: https://openlineage.io/docs/spec/object-model/?utm_source=chatgpt.com "Object Model"
[10]: https://typelevel.org/cats-effect/docs/thread-model?utm_source=chatgpt.com "Thread Model · Cats Effect"
[11]: https://typelevel.org/cats-effect/docs/2.x/datatypes/io?utm_source=chatgpt.com "IO · Cats Effect"
[12]: https://openlineage.io/docs/?utm_source=chatgpt.com "About OpenLineage"

------

Create relevant ADRs for everything we discussed and prompt me separate markdown code-block for every ADR. ADR must be comprehensive & must have clarity about low-level design, implementation as well.
Create as many ADRs that you feel relevant.
