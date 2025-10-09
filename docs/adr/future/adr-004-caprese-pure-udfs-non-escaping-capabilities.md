# ADR-004: Experimental – Caprese (Capture Checking) for Pure UDFs & Non-Escaping Capabilities

- **Status**: Proposed (Opt-In)
- **Date**: 2025-09-10
- **Owner**: Experimental
- **Related**: ADR-001, ADR-003

## Context
We want **compile-time** guarantees that:
- UDFs intended to be **pure** cannot capture IO/capabilities,
- Connectors/handles **cannot escape** lifecycle scopes,
- Forward-compat **defaults** are deterministic.

Scala 3’s **capture checking** (experimental) provides `A -> B` (non-capturing function) and **capability** types (`X^`). :contentReference[oaicite:31]{index=31}

## Decision
Introduce a small, opt-in module `experimental-caprese`:
- `type PureFn[-A,+B] = A -> B` for **pure transforms** (compiler-enforced). :contentReference[oaicite:32]{index=32}
- `def withCapability[C, A](acquire: => C)(use: C^ => A): A` to scope connectors (no escape). :contentReference[oaicite:33]{index=33}
- `type FieldDefault[+A] = Unit -> A` for forward-compat defaults (pure by type). :contentReference[oaicite:34]{index=34}
- Add `PipelineBuilder.pureTransform(name)(f: A -> B)` alongside normal `transform`.

## Low-Level Design
- Mark example/demo files with `import language.experimental.captureChecking`. :contentReference[oaicite:35]{index=35}
- Compiler rejects:
  - closures in `pureTransform` that reference connectors/secrets,
  - returning a function that closes over a `C^` param from `withCapability`.
- No changes to engine adapters; this is **compile-time only**.

## Consequences
- Fewer production issues from UDFs capturing non-serializable or long-lived resources.
- Deterministic defaults for schema evolution.

## Risks
- Experimental feature; syntax and rules may evolve → isolate in module, make optional. :contentReference[oaicite:36]{index=36}

## References
- Scala 3 Capture Checking reference. :contentReference[oaicite:37]{index=37}
- “Capturing Types” paper excerpt. :contentReference[oaicite:38]{index=38}
- Noel Welsh, “Direct‑style Effects Explained” (Scala 3): https://noelwelsh.com/posts/direct-style/
- Nicolas Rinaudo, “Effects as Capabilities”: https://nrinaudo.github.io/articles/capabilities.html
- Nicolas Rinaudo, “Hands‑on Capture Checking”: https://nrinaudo.github.io/articles/capture_checking.html

---

## Related Work & Alignment (Post‑Reading Notes)

- Capabilities for control flow (Nicolas Rinaudo): https://nrinaudo.github.io/articles/capabilities_flow.html
  - Takeaway: model special powers (labels/boundaries) as capabilities that must not escape their scope.
  - Relevance: identical non‑escape discipline applies to data/IO resources (connectors, sessions, handles). Our goal is to ensure such capabilities cannot leak into pure UDFs or lazy structures.

- Effects as Capabilities (N. Rinaudo): https://nrinaudo.github.io/articles/capabilities.html
  - Takeaway: express required effects/resources as context functions (direct‑style) rather than monads, keeping execution requirements explicit and composable.
  - Relevance: informs an optional Scala 3 facade to declare engine/connectors as required capabilities without changing core runtime.

- Direct‑style Effects (N. Welsh): https://noelwelsh.com/posts/direct-style/
  - Takeaway: direct‑style APIs with effect handlers/context functions can improve ergonomics while preserving separation of description vs. action.
  - Relevance: we can prototype a facade using context functions alongside our existing tagless‑final API; this remains optional and non‑blocking.

Conclusion: the article validates our direction - use Scala 3 capture checking to enforce non‑escaping capabilities and pure arrows (`A -> B`) for UDFs.

## ADR Amendments (No deletions; clarifications and additions)

### 1) Terminology & Surface Types

- Adopt Scala 3 terminology explicitly:
  - Pure functions use the pure arrow: `A -> B` (non‑capturing).
  - Tracked capabilities use the caret type: `C^`.
- Keep existing aliases (for readability) but document their mapping:
  - `type PureFn[-A,+B] = A -> B` (already planned - reaffirmed).

### 2) API Shapes Informed by Capabilities Article

Add the following opt‑in APIs (names stable; semantics experimental):

```scala
// Scope a capability so it cannot escape; pure by construction
def withCapability[C, A](acquire: => C)(use: C^ => A): A

// Effectful variant for interop (scoped within F, but still non‑escaping in the "use" lambda)
def withCapabilityF[F[_], C, A](acquire: F[C])(use: C^ => F[A]): F[A]

// Pipeline builder addition: enforce purity at the type level for internal transforms
def pureTransform[A, B](name: String)(f: A -> B): PipelineBuilder[WithTransform, F, In, B]
```

Design notes:
- `withCapability` mirrors the boundary/break model: callers get a scoped power (`C^`), but cannot store or return it.
- `pureTransform` makes “pure inside, effects at the edges” the default for UDFs.

### 3) Laziness & Non‑Escape (Caveats)

Pitfalls to prevent (mirroring the article’s Iterator example):
- Returning closures that capture `C^` from a `withCapability` region.
- Storing `C^` in a field of an object that outlives the region.
- Building lazy collections/streams that reference `C^` (evaluation may occur after the region closes).

Mitigations (compile‑time):
- Let capture checking reject escaping `C^` in the cases above.
- Provide guidance to prefer eager, total transformations in `A -> B`; if laziness is required, ensure all use happens inside the capability scope.

### 4) Error Message Guidance (DX)

When capture checking rejects a program, aim for messages of the form:

```
Caprese: capability C^ escapes its scope
  • captured in closure returned from `withCapability` at Foo.scala:42
  • referenced by lazy value `it` evaluated outside scope
Hint: compute eagerly inside `withCapability { (c: C^) => ... }` and return plain values (A -> B).
```

### 5) Interop & Incremental Adoption

- Interop: allow existing `A => B` transforms to coexist; `pureTransform` is opt‑in.
- Escape hatch (temporary): an explicit, scoped suppression annotation (e.g., `@capreseUnsafeEscape`) for code that cannot be rewritten immediately. Not for production paths; tracked in CI.

### 6) Open Questions to Validate in POC

- False positives/negatives around laziness (Iterators, Streams, fs2/ZIO streams).
- Ergonomics: can we keep ceremony low for common UDFs (does `A -> B` feel natural for teams)?
- Tooling: scalafix lints to recommend `pureTransform` for obvious pure lambdas.

### 6.1) POC Hardening Plan (Realistic, Value‑Add)

- Tests that must fail compilation (ensure capture checking works in practice):
  1. Returning `C^` from `withCapability`.
  2. Storing `C^` in an object that outlives the scope (val/field).
  3. Creating lazy collections/streams (Iterator/Stream/fs2/ZIO) that capture `C^` then evaluate outside scope.
  4. Writing a `pureTransform` that closes over an IO handle (should be rejected).

- Tests that must pass:
  1. `A -> B` transforms with no captures; composition of multiple pure transforms.
  2. `withCapability` used to compute a plain `A` result that does not leak `C^`.
  3. Interop: `A => B` transforms continue to work (without purity guarantees).

- DX checks:
  - Error message clarity: include escape site and hint (see Error Message Guidance above).
  - Scalafix lint (advisory): suggest `pureTransform` when lambda is syntactically pure.

- Deliverables (time‑boxed):
  - Experimental Scala 3 module (exists: experimental‑caprese) with CI task to compile both “good” and “bad” examples.
  - Short migration note for authors (how to move `A => B` to `A -> B`).

### 7) Adoption Plan (Updated)

Phase 0 (branch):
- Ship `experimental-caprese` module behind a flag; examples require `import language.experimental.captureChecking`.

Phase 1 (pilot):
- Convert 1–2 inner transforms to `pureTransform`; wrap one connector with `withCapability`.
- Measure compile errors and developer friction; refine error text.

Phase 2 (template):
- Add a template switch that generates pipelines using `pureTransform` by default, with a documented interop path for `A => B`.

### 7.1) Optional Scala 3 Facade (Direct‑Style) - Exploratory Only

- Goal: improve ergonomics for some code by expressing required resources as capabilities via context functions, while keeping core runtime unchanged.
- Sketch (non‑binding):

```scala
// Requires a DataAlgebra capability in scope; still pure in the middle
def runJob[A, B](using da: DataAlgebraCapability): A -> B = ???

// IO edges remain effect‑polymorphic (Cats‑Effect/ZIO) in existing APIs
def read[F[_]: EffectSystem, A](src: DataSource): F[Dataset[A]]
```

- Constraints:
  - Facade must not change engine adapters or distributed semantics.
  - Opt‑in; keep Tagless‑Final `F[_]` API as the stable default.
  - Ship only after POC proves value and zero regression in clarity/perf.

### 8) Non‑Goals (unchanged)

- No changes to engine adapters or distributed runtime semantics.
- No promise to enforce purity across third‑party libraries; only at our API boundaries.

### 10) Bottom Line & Scope (Sign‑off Criteria)

- This ADR is about compile‑time guarantees that add real engineering value: pure UDFs (`A -> B`) and non‑escaping capabilities (`C^`).
- Runtime stays the same: effect‑polymorphic edges (Cats‑Effect/ZIO), algebraic engine boundary, no engine rewrites.
- Optional Scala 3 facade (direct‑style) is exploratory and strictly additive; we will not gate core features on it.
- Success criteria:
  - Clear compile errors on escapes; zero runtime cost for purity.
  - Authors can adopt `A -> B` incrementally; interop remains smooth.
  - No regressions in performance or operator ergonomics.

### 9) Traceability

- This amendment was informed by: “Capabilities and Control Flow in Scala”, Nicolas Rinaudo (link above). The non‑escape discipline and laziness caveats map directly to our goals for pure UDFs and scoped resources.

---

## Appendix - Tiny Examples (for reviewers)

Scala 3 source files should include:
```scala
import language.experimental.captureChecking
```

1) Rejected (capability escapes)
```scala
def withCapability[C, A](acquire: => C)(use: C^ => A): A = { val c = acquire; use(c) }
final case class Connector(token: String)

// ERROR: C^ escapes the capability scope
def unsafeEscape: Connector =
  withCapability(Connector("t")) { c =>
    c // <- reject: returning the capability
  }
```

2) Accepted (pure transform and scoped capability)
```scala
type PureFn[-A,+B] = A -> B
val pureUpper: PureFn[String, String] = s => s.toUpperCase

def safeUse: String =
  withCapability(Connector("t")) { c =>
    pureUpper("ok") // c is used but does not escape
  }
```
