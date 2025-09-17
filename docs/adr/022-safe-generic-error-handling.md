# ADR 022 — Safe, Generic Error Handling Utilities (Result/Validated + Effect Abstraction)

- Status: Accepted
- Date: 2025-09-14

## Context

FlowForge already defines a rich, typed error ADT (`FlowForgeError` and subtypes in `modules/core/.../types/ErrorTypes.scala`) and a
unified effect abstraction (`EffectSystem[F]`). However, code paths still mix styles:

- Direct `try { ... } catch { ... }` blocks in several modules (Spark, connectors, config) for exceptional paths.
- Ad‑hoc conversions between `Try`, `Either`, and `ValidatedNel` with uneven domain mapping from `Throwable`.
- Resource lifecycle handled via `ResourceSafety` in infrastructure, but callers sometimes do manual `try/finally`.

We want a single, idiomatic, FP‑first way to: (1) run potentially unsafe code safely; (2) map throwables to our domain
errors; (3) aggregate multiple independent validation errors; and (4) do all of this polymorphically over `F[_]` without
leaking exceptions.

This ADR standardizes the approach and provides small utilities that are easy to adopt incrementally.

## Decision

Introduce a tiny, universal “safety” surface in `core`:

- New package: `com.flowforge.core.safety`
  - `Safety` — pure and effectful helpers for safe execution.
  - `ErrorMapper` — pluggable mapping from `Throwable` → `FlowForgeError` tuned per layer.

Decisions aligned with existing architecture:

- Typed error channels everywhere: results use `Either[FlowForgeError, A]` or `ValidatedNel[FlowForgeError, A]`.
- Effectful safety is expressed via `EffectSystem[F]`, not concrete Cats/ZIO types, preserving engine neutrality.
- Resources remain under `ResourceSafety` (already present in infrastructure). `Safety` provides thin bridges to avoid
  manual `try/finally` at call sites.
- No behavior changes at call sites by default; adoption is incremental and review‑driven.

## Non‑Goals

- Do not replace the existing error ADTs or collapse categories; the mapper composes with them.
- Do not introduce runtime exceptions, except in tests/IOApps where bridging may be necessary.
- Do not add heavy dependencies or alternative effect systems.

## Detailed Design

Package: `modules/core/src/main/scala/com/flowforge/core/safety/`

Type aliases (ergonomics, local to `Safety`):

- `type Result[+A]  = Either[FlowForgeError, A]`
- `type ValidatedResult[+A] = ValidatedNel[FlowForgeError, A]`

Error mapping:

- `trait ErrorMapper { def apply(t: Throwable): FlowForgeError }`
- Provide `DefaultErrorMapper` that delegates to existing helpers, e.g. `FlowForgeError.fromThrowable(t)` and applies
  sensible category‑aware mapping:
  - `IllegalArgumentException`, parsing/type errors → `ValidationError`.
  - IO/FS/network/service errors → `SystemError.ServiceUnavailable` (or more specific when available).
  - `TimeoutException` → `SystemError.OperationTimeout` with derived elapsed/timeout if present.
  - Fallback → `SystemError.ServiceUnavailable` with `cause = Some(t)`.
- Modules can supply focused mappers (e.g., connectors map `SQLException`/`IOException` to connector‑specific wrappers).

Pure helpers (no `F`):

- `def safely[A](thunk: => A)(implicit em: ErrorMapper): Result[A]`
  - Impl: `Either.catchNonFatal(thunk).leftMap(em(_))`
- `def safelyV[A](thunk: => A)(implicit em: ErrorMapper): ValidatedResult[A]`
  - Impl: `Validated.fromEither(safely(thunk)).toValidatedNel`
- Conversions: `fromTry`, `fromOption(err)`, `fromEither`, `sequenceV(xs: List[Result[A]]): ValidatedResult[List[A]]`.
- Transformations: `mapError(f)`, `toValidatedNel`, and a test‑only `leftOrRaise` bridge.

Effectful helpers (with `EffectSystem[F]`):

- `object Safety { def in[F[_]: EffectSystem]: In[F] }`
- `trait In[F[_]]`:
  - `def attempt[A](fa: F[A])(implicit em: ErrorMapper): F[Result[A]]`
  - `def attemptV[A](fa: F[A])(implicit em: ErrorMapper): F[ValidatedResult[A]]`
  - `def orFail[A](fa: F[Result[A]])(implicit F: EffectSystem[F]): F[A]` — bridge where an `A` is required.
  - `def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A]` — delegates to `EffectSystem.guarantee`.
  - `def bracket[A,B](acq: F[A])(use: A => F[B])(rel: A => F[Unit]): F[B]` — delegates to `EffectSystem.bracket`.
- Optional syntax: `fa.safely`, `result.mapError`, `result.toValidatedNel`, `fa.withErrorMapper(em)`, and
  `fa.logLeft(implicit L: CoreLogger[F])`.

Notes on namespacing:

- `ResourceSafety` lives today under `com.flowforge.safety` (infrastructure). `Safety` is placed in
  `com.flowforge.core.safety` to avoid cycles and keep pure/effect helpers close to `EffectSystem` and `FlowForgeError`.

## Rationale

- Aligns with ADR‑012 (effect abstraction) and the repo’s FP style: pure functions, explicit effects, and typed errors.
- Reduces boilerplate and eliminates `try/catch/finally` from main sources.
- Unifies `Either` vs `ValidatedNel` usage: `Either` for dependent computations; `ValidatedNel` for aggregating
  independent checks (config, DQ).
- Keeps mappers local to modules/layers so error semantics remain domain‑accurate.

## Consequences

Pros:

- Consistent, typed error handling across modules; fewer ad‑hoc conversions.
- Easier testing and property checks; simpler negative‑path specs.
- Safer resource usage via `bracket/guarantee` helpers that compose with existing `ResourceSafety`.

Cons:

- Requires adding a small amount of plumbing (mappers) per module.
- Some refactors to replace `try`/`Try{}` and scattered `handleError` blocks.

Performance:

- `Either.catchNonFatal` and effect `attempt` are constant‑factor overheads; negligible compared to I/O/Spark.
- Aggregation with `ValidatedNel` is allocation‑friendly and used only for validations.

## Migration Plan

1) Introduce API (core)

- Add `Safety.scala`, `ErrorMapper.scala` in `modules/core/src/main/scala/com/flowforge/core/safety/`.
- Provide `DefaultErrorMapper` delegating to `FlowForgeError.fromThrowable` plus the category tweaks above.

2) Soft adopt in new/changed code

- Encourage `Safety.safely` / `Safety.in[F].attempt` during reviews. Add examples to Handbook.

3) Targeted refactors (module by module)

- Connectors: replace repetitive `F.handleError`/`Try{}` with `in[F].attempt(...).map(_.leftMap(toConnectorError))` or
  module mapper.
- Infrastructure: config parsing/file ops to use `safelyV` for aggregated errors.
- Engines‑Spark: wrap blocking calls with `F.blocking` + `in[F].attempt` and map to domain error ADTs.

4) Guardrails

- Scalafix: keep `DisableSyntax.noThrows = true` (already present) and add CI grep to ban `try {` in main sources
  (allow in tests/examples/migrations). Example step:
  - `rg -n "\btry\s*\{" --glob 'modules/**/src/main/scala/**/*.scala' && echo 'Found try blocks' && exit 1 || true`
- Add CI check to fail on `unsafeRunSync` in main sources (allow in tests/examples/IOApp):
  - `rg -n "unsafeRunSync" --glob 'modules/**/src/main/scala/**/*.scala' && exit 1 || true`

5) Tests & docs

- Add unit specs: `SafetySpec` (pure) and `SafetyEffectSpec` (effectful) under `core`.
- Update `docs/contributing/HANDBOOK.md` quick‑reference and ADR‑020 checklist with the Either vs Validated rule of thumb.

## Usage Examples

Pure compute

- `val r: Result[Int]  = Safety.safely(riskyParse(s))(DefaultErrorMapper)`
- `val v: ValidatedResult[Unit] = Safety.safelyV(validateField(x)).void`

Effectful I/O (Spark/Cloud)

- `val R = Safety.in[F]`
- `for { res <- R.attempt(F.blocking(storage.get(bucket, key))); _ <- R.orFail(res) } yield ()`
- Or aggregate: `R.attemptV(F.blocking(readPart(p1)))`

Aggregating validations

- Keep DSLs that already return `ValidatedNel`; use `safelyV` only for unsafe sub‑steps inside each rule.

## Alternatives Considered

- Rely solely on `EffectSystem[F].attempt` + ad‑hoc mapping at call sites — rejected; increases duplication and
  encourages inconsistency.
- Introduce a global `Throwable => FlowForgeError` implicit — rejected; different layers need different semantics.
- Add more exception types and throw/catch internally — rejected; breaks purity and testability.

## Appendix — Concise API Sketch

- Safety.scala
  - `object Safety {`
  - `  type Result[+A]  = Either[FlowForgeError, A]`
  - `  type ValidatedResult[+A] = ValidatedNel[FlowForgeError, A]`
  - `  def safely[A](thunk: => A)(implicit em: ErrorMapper): Result[A] = Either.catchNonFatal(thunk).leftMap(em(_))`
  - `  def safelyV[A](thunk: => A)(implicit em: ErrorMapper): ValidatedResult[A] = Validated.fromEither(safely(thunk)).toValidatedNel`
  - `  def fromTry[A](t: Try[A])(implicit em: ErrorMapper): Result[A] = t.toEither.leftMap(em(_))`
  - `  def fromOption[A](oa: Option[A], ifEmpty: => FlowForgeError): Result[A] = oa.toRight(ifEmpty)`
  - `  def sequenceV[A](xs: List[Result[A]]): ValidatedResult[List[A]] = xs.traverse(_.toValidatedNel)`
  - `  def in[F[_]: EffectSystem]: In[F] = new In[F] { ... }`
  - `}`
  - `trait In[F[_]] {`
  - `  def attempt[A](fa: F[A])(implicit em: ErrorMapper): F[Result[A]] =` 
  - `    EffectSystem[F].handleErrorWith(EffectSystem[F].map(fa)(Right(_): Result[A]))(t => EffectSystem[F].pure(Left(em(t))))`
  - `  def attemptV[A](fa: F[A])(implicit em: ErrorMapper): F[ValidatedResult[A]] = EffectSystem[F].map(attempt(fa))(_.toValidatedNel)`
  - `  def orFail[A](fr: F[Result[A]])(implicit F: EffectSystem[F]): F[A] = F.flatMap(fr)(_.fold(F.raiseError, F.pure))`
  - `  def guarantee[A](fa: F[A])(finalizer: F[Unit]): F[A] = EffectSystem[F].guarantee(fa)(finalizer)`
  - `  def bracket[A,B](acq: F[A])(use: A => F[B])(rel: A => F[Unit]): F[B] = EffectSystem[F].bracket(acq)(use)(rel)`
  - `}`

- ErrorMapper.scala
  - `trait ErrorMapper { def apply(t: Throwable): FlowForgeError }`
  - `object DefaultErrorMapper extends ErrorMapper {`
  - `  def apply(t: Throwable): FlowForgeError = FlowForgeError.fromThrowable(t)`
  - `}`

## Status & Rollout

- Status: Accepted for implementation in `core` first, then adopted in connectors/engines/infrastructure.
- Rollout owner: Core maintainers; module owners contribute mappers and local refactors.
