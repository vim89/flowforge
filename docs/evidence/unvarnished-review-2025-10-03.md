# Unvarnished Review — 2025-10-03

Scope: full-tree scan of docs, modules, build, CI; deep reads of core compile-time contracts and engine abstractions. Files referenced below are repository-relative.

## What’s Strong
- Compile-time contracts: `modules/core/src/main/scala/com/flowforge/core/contracts/SchemaConforms.scala` + `.../internal/ContractMacros.scala` materialize evidence with path-aware diffs and a clear policy lattice.
- Typestate pipeline: `modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala` enforces stage order via phantom states; `build` requires `Complete`.
- Effect hygiene: `modules/core/src/main/scala/com/flowforge/core/algebra/DataAlgebra.scala` keeps pure transforms outside `F[_]`, restricting `F[_]` to external IO.
- Spark engine: `modules/engines-spark/src/main/scala/com/flowforge/engines/spark/SparkDataAlgebra.scala` implements real read/write, CDC (Delta SCD1/2), with resource-safe session lifecycle.
- DQ strategy: `modules/quality-deequ` defaults to native Spark checks and upgrades to Deequ via reflection (optional, no hard dep).
- CI hygiene: `.github/workflows/ci.yml` includes scalafmt/scalafix gates, unsafe cast gate, doc coverage, and compile-fail tests in the core matrix.

## Gaps and Risks (with fixes)
1) CI JDK mismatch for Spark ITs
- Evidence: `.github/workflows/ci.yml` had `spark-it` job on JDK 21; main matrix uses JDK 17.
- Risk: Spark 3.5.x targets Java 17; JDK 21 can break runtime/bytecode in ITs.
- Fix: switched `spark-it` to JDK 17 (this change committed in this review).

2) println in production sources
- Evidence: examples legitimately use printing; production modules should not. No CI gate previously enforced the ban.
- Risk: noisy logs, bypassed structured logging, untestable side-effects.
- Fix: added a CI job “println-guard” to ban `println/print/System.out.println` in `src/main` excluding examples/CLIs/perf (committed).

3) Compile-fail tests: broaden coverage to builder typestate
- Evidence: `modules/compile-fail-tests` cover policy lattice; add negative cases for missing sink/transform and contract-free endpoints.
- Fix plan: add `BuilderTypestateFailSpec.scala` asserting `build()` is unavailable without source/transform/sink, and that typed endpoints require `SchemaConforms[_,_,_]`.

4) Observability/config TODOs
- Evidence: `modules/infrastructure/src/main/scala/com/flowforge/config/ConfigurationManagement.scala` keeps a `var` and notes TODO for proper decoding.
- Risk: global mutable state; mismatch with scalafix DisableSyntax philosophy; limited type-safe config.
- Fix plan: replace `var config` with `Ref[F, Config]`; provide proper `FlowForgeConfig` decoders; add minimal tests for reload/watch.

5) Talk materials miss strong WHY and boundaries
- Evidence: `docs/talks/*` focus heavily on WHAT; WHY and clear compile-time vs runtime, DX vs process are under-expressed.
- Fix: added `docs/talks/WHY-HOW-WHAT-outline.md` (this review) with a story and boundary slides. Update decks accordingly.

6) Documentation acceptance checks
- Evidence: Good baselines in `docs/quality/release-criteria.md` and `docs/plan/v1.0-readiness.md` but acceptance criteria lack explicit “builder typestate compile-fail” and “Spark IT JDK lock”.
- Fix plan: extend both docs with explicit criteria and pointers to tests/jobs.

## v1.0 Readiness — Current Verdict
- Not yet. Contracts and engine core are strong, but we need: (a) typestate compile-fail coverage, (b) config/observability hardening, (c) talk/docs WHY-first pass, and (d) CI gates just added to bake for a cycle.

## Remediation Plan (critical → high)
- Critical
  - Lock Spark ITs to JDK 17 in CI (DONE).
  - Add println ban in production sources (DONE).
  - Add builder typestate compile-fail specs (NEW tests in `modules/compile-fail-tests`).
- High
  - Replace config `var` with `Ref[F, Config]`; implement `FlowForgeConfig` decoders; unit tests.
  - Observability: no-op + OTEL tracing hooks; metrics counters present but add minimal docs and a smoke test.
  - Talks/docs: integrate WHY-first outline; add runtime vs compile-time and DX vs process slides.

## DX Benchmarks to Hit (laptop)
- `ffDev` (compile + focused tests) under ~3s for pure code change.
- `compile-fail-tests/test` under ~10s with isolated macro paths.
- Template red→green demo under 3 minutes including sbt warm start.

## Notes on Uniqueness & Positioning
- Unique core: compile-time contract gates plus typestate builder; optional DQ plug-in without forcing a hard dependency.
- Where to push further: first-class “contract-as-code” workflow (extract → review → generate SDK), and a zero-escape framework story (templates, connectors, engine adapters) so teams never have to “roll their own glue”.

