# FlowForge Idiomatic Scala 2.13 Refactor Plan (v1.x line)

Status: proposal (ready to execute)
Scope: all modules; no functional or signature changes; behavior preserved.
Goals: eliminate Java‑like patterns, align with FP idioms, readability, maintainability, and safety per CONTRIBUTING.md and docs/plan/refactor-coding-pattern.md.

## 0) Ground Rules
- No public API changes: function names, signatures, return types preserved.
- Pure transforms, effectful edges: Spark transforms stay pure (Dataset[...]), IO/orchestration through `EffectSystem[F]`.
- No semantic changes; only structure/style/idioms, naming, and organization.
- Mechanical refactors scripted where possible (scalafix/scalafmt); small, reviewable PRs.
- Type safety first: eliminate or strictly confine all uses of `Any`, `asInstanceOf`, unchecked casts, and partial functions in production sources. Unsafe code paths must be encapsulated in a single, well‑documented internal utility with property tests.

## 1) Repository‑wide Mechanical Refactors (Wave A)
- Remove wildcard imports; prefer explicit imports.
- Organize imports and unused imports removal (scalafix `RemoveUnused` + scalafmt).
- Prefer `val` over `var`; none found in sources, but enforce via scalafix rule.
- Replace `println` with structured logger except in CLIs:
  - Maintenance CLI: swap `println` → logger; still prints on success at INFO.
- No `null` usage (found only in tests for invalid values; keep tests as-is).
- Eliminate `asInstanceOf`/`Any` where possible, and otherwise localize:
  - Introduce `KleisliCasting` (internal) as the only location where stage composition erases types. Mark it `private[spark]`, add scaladoc explaining why, and property tests proving identity and associativity of composition.
  - Add a scalafix rule to forbid `asInstanceOf` and `Any` in main sources except in that utility and macro internals.
- Replace Java time math with FiniteDuration where present.
- Prefer `Either`/`ValidatedNel` over exceptions in pure codepaths; in edge code catch→map to `Left`.
- Add `final` to small ADTs/objects where appropriate to aid inlining and clarity.

Tools: add/refine scalafix config rules
- Disable: `DisableSyntax.var`, `DisableSyntax.return`, `DisableSyntax.throw` (allow only at edges), `DisableSyntax.null`.
- RemoveUnused imports; `OrganizeImports` rule.
- Lint: reiterate `-Xlint` flags already present.
- Enable helpful warnings: `-Ywarn-dead-code`, `-Ywarn-value-discard`, `-Xlint:-missing-interpolator` already set; keep them and treat as errors where safe. Add `-Wconf:cat=unchecked:error` to surface unchecked casts during refactor (relax after wave A).

Acceptance:
- `sbt fmtCheck`/`fixCheck` green; no functionality change; test suite green.

## 2) Core Modules (Wave B)
### core/algebra, core/types, syntax
- `EffectSystem` usage
  - Replace chained F.* calls with for‑comprehensions in longer sequences to aid readability.
  - Keep tagless final style; avoid mixing ZIO imports in core (already gated in CI).
- `OpenLineageEmitter`
  - Http emitter: wrap network side effects in `F.blocking` + `F.attempt` (already partly); replace `Try{}` usages with `F.attempt` + pattern match.
  - Provide logger; deprecate ad‑hoc printlns.
- `Lineage` async wrapper
  - Use single fiber consumer + bounded queue; use `F.sleep` for backoff (never `Thread.sleep`). Keep best‑effort semantics; document loss‑tolerance.
- `AsyncOpenLineageEmitter`
  - Remove existential warning by type alias for `Fiber[F, Unit]`; keep fiber creation, add brief comment; reduce noise. No user‑visible change.
- Contracts macros
  - Keep logic as-is; normalize error message building; ensure all policies covered; add comments.
- Syntax modules
  - Ensure `EnhancedPipelineBuilder` methods are small, use pure combinators; replace ad‑hoc exceptions with `F.raiseError` where present.

Acceptance: no changes to effect surfaces or signatures; tests green.

## 3) Spark Engine (Wave C)
- `SparkDataAlgebra` read/write
  - Use for‑comprehensions for the longer `timed`/`blocking` chains.
  - Centralize Spark write option application (repartition/coalesce/shufflePartitions) in a helper.
  - Replace stringy logs with structured logger.
- Spark best‑practices guardrails (no perf regressions):
  - Favor Dataset/DataFrame APIs; avoid `.collect`/driver side transformations in core paths.
  - Respect Adaptive Query Execution (AQE) and broadcast hints; expose knobs only via config/WriteOptions.
  - Partition sizing: prefer target file sizes 128–512MB; use `coalesce` for post‑shuffle compaction prudently.
  - Join strategy: allow Catalyst to choose; provide hints only when stats are poor.
  - Ensure data skipping and statistics for Delta/Parquet are preserved; do not strip column stats.
- CDC/SCD helpers
  - Extract helpers for key extraction and SCD2 columns to remove comment noise and duplication.

Performance acceptance:
- Micro‑bench equivalence: run selected source→transform→sink on small fixtures to detect accidental slowness; AQE on by default; no `.repartition(1)` in hot paths.

Acceptance: examples and ITs continue to cancel/green; perf unaffected.

## 4) Connectors (Wave D)
- GCS connector
  - Replace non‑exhaustive pattern matches with total matches; remove duplication via small helpers.
  - Use `ConnectorResult` consistently; convert stray exceptions to failures.
- JDBC (new)
  - Factor mapping of writer options (user/password/extraOptions) into a tiny util.
  - Ensure blocking sections (`DriverManager.getConnection`, JDBC I/O) are wrapped in `F.blocking` or `Resource` in future work; current Spark JDBC path already effect‑safe.

Acceptance: connector tests green.

## 5) CLIs & Examples (Wave E)
- Maintenance CLI
  - Replace println with logger; validate args via scopt’s `.validate`.
  - Wrap SparkSession in Resource for parity.
- Examples
  - Keep DF constructions where they avoid implicits cycles; add comments.
  - For lineage demos, prefer `OpenLineageEmitter.asyncHttp()`; document env vars OPENLINEAGE_URL/OPENLINEAGE_NAMESPACE.

Acceptance: examples still compile quickly; ITs unchanged.

## 6) Flink (Wave F)
- Prefer case classes for data; remove deprecated `writeAsText` where simple; otherwise keep minimal.
 - Keep engine parity example small; avoid deep refactors that could change semantics.

Acceptance: unit tests green; parity spec green.

## 7) Documentation & CI (Wave G)
- Add `docs/style/scala2-idioms.md` summary; link from CONTRIBUTING.md.
- CI: keep leakage check; add “refactor-lint” job running scalafix dry‑run.
 - Add a gate that fails on `asInstanceOf`/`Any` usage outside whitelisted files (KleisliCasting, macro internals).

### Error Handling and Resource Safety (ADR‑022)

- Replace try/catch/finally with typed, functional patterns:
  - Pure code: `Safety.safely[A](thunk): Result[A]` and `Safety.safelyV[A](thunk): ValidatedResult[A]`.
  - Effectful code: `EffectSystem[F].handleErrorWith`, `Safety.in[F].attempt/attemptV`, and `EffectSystem[F].bracket/guarantee` for resource cleanup.
  - Throwable → domain mapping via `ErrorMapper` (module‑specific or `DefaultErrorMapper`).
- Reflection/edge operations: wrap with `Either.catchNonFatal { ... }` and convert to options or domain errors as appropriate.
- IO streams, network clients, Spark/Hadoop handles: always use `EffectSystem.bracket` for acquisition/use/release; never `try { … } finally { close() }`.

#### Migration checklist (grep‑first)

- Ban raw exceptions usage in main sources (tests/examples/migrations allowed):
  - `rg -n "\\btry\\s*\\{" --glob 'modules/**/src/main/scala/**/*.scala'`
  - `rg -n "\\bscala\\.util\\.Try\\b" --glob 'modules/**/src/main/scala/**/*.scala'`
  - `rg -n "unsafeRunSync" --glob 'modules/**/src/main/scala/**/*.scala'`
- Replace with:
  - Pure: `Safety.safely / safelyV`
  - Effectful: `Safety.in[F].attempt`, `EffectSystem.bracket/guarantee`, `Either.catchNonFatal`
- Ensure errors map to `FlowForgeError` via `ErrorMapper` at boundaries.

#### Code patterns

- Before (imperative):
  - `try { val is = fs.open(p); val bytes = is.readAllBytes(); is.close(); bytes }`
- After (functional):
  - `F.bracket(F.blocking(fs.open(p)))(is => F.blocking(is.readAllBytes()))(is => F.blocking(is.close()).void)`

- Before (reflection):
  - `try { val cls = Class.forName(name); cls.getMethod(...).invoke(...)} catch { ... }`
- After:
  - `Either.catchNonFatal { val cls = Class.forName(name); ... }.toOption`

Acceptance: CI remains green; developer docs discoverable.

## 8) Module-by-Module To‑Do Matrix (excerpt)
- core
  - for‑comprehensions in longer F chains; logger in emitters; cleanup prints.
- engines-spark
  - write tuning util; structured logs; tidy timed chains.
  - eliminate duplicate pattern matches in write/read; move to helpers; comprehensive tests for JDBC + file sinks.
- connectors & connectors-gcs/jdbc
  - result helpers; exhaustive matches; dedupe.
- infrastructure
  - partitions util already idiomatic; consider laws later.
- examples & CLIs
  - println→logger; Resource for Spark where applicable.

## 9) Risk & Backout
- Changes are syntactic/organizational; tests and compile‑fail specs catch regressions.
- Each wave is a shippable PR; revertible by PR if needed.

## 10) Timeline & PR Breakdown
- Wave A: Mechanical (1 day) - single PR
- Wave B/C: Core + Spark (2–3 days) - 2 PRs
- Wave D/E: Connectors + CLIs/Examples (1–2 days) - 2 PRs
- Wave F: Flink polish (0.5 day) - 1 PR
- Wave G: Docs/CI (0.5 day) - 1 PR

Total: ~5 working days, ~7 small PRs. Repo stays green throughout.

## 12) References & Best Practices (for reviewers)
- Scala 2.13 Style Guide (naming, imports, pattern matching)
- Apache Spark SQL Performance Tuning (AQE, stats, joins, partition sizing)
- Delta Lake OSS optimizations & VACUUM guidance (Z‑Ordering, retention caveats)
- Cats Effect 3 concurrency model (fibers, sleep, blocking)


## 11) Acceptance Checklist per PR
- sbt fmtCheck, fixCheck, compileAll, testAll green.
- No public signature changes; no return type changes.
- Coverage not worse; compile‑fail tests unchanged or expanded.
- Changelog and brief refactor notes included.
