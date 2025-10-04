# Core Module Coverage Plan — Path to 90% Statement Coverage

Owner: Core module (com.flowforge.core)
Date: 2025-10-04
Target: ≥90% statement coverage (scoverage) for `modules/core`

## Philosophy (Quality > Threshold)
- Coverage is a metric, not the goal. Our goal is specification fidelity: tests that prove the framework’s design, contracts, and operational guarantees. We will not write trivial tests just to raise numbers.
- Every added spec must teach something about FlowForge (builder typestates, effect semantics, schema/contract guarantees, observability, error paths). If a test wouldn’t catch a realistic regression, don’t add it.
- Deep-dive first: read the code and its docs (AGENTS.md guidance, design docs, code comments), then design tests from the intended behavior and invariants.
- Use external best practices where helpful (property-based testing, deterministic time, effect-friendly runners). Links in “External references”.

## WHY → HOW → WHAT (Inside‑Out Testing)

We structure core tests to reflect FlowForge’s communication philosophy (see docs/talks/flowforge-approach.png):

- WHY (beliefs, purpose, non‑negotiables)
  - Prove the big claims that define FlowForge’s identity.
  - Examples: “Make impossible states impossible” (typestate builder), “Make runtime errors compile‑time errors” (compile‑fail contract evidence), “Functional‑first, effects at the edges” (effects algebra laws, absence of hidden IO in pure code paths).
  - Test surfaces: compile‑fail tests; typestate invariants; schema conformance evidence; law tests (associativity/identity for core combinators where applicable).

- HOW (principles, approach, design patterns)
  - Demonstrate how the beliefs are realized as architecture and patterns.
  - Examples: Tagless‑final algebras, effect polymorphism (Cats Effect/ZIO), resource safety (bracket), concurrency semantics (par*, race), pipeline combinators, observability hooks.
  - Test surfaces: PipelineBuilder stage folding and metadata; PipelineCombinators (sequence/parallel/conditional/retry/batch); effect syntax (timed/timeout/retry/logging); Tracer/Lineage helpers.

- WHAT (capabilities, adapters, surfaces)
  - Validate concrete features work as advertised without leaking policy or framework internals.
  - Examples: Default codecs, schema builders and rendering, data types, typed IO wrappers, small utilities.
  - Test surfaces: DefaultCodecs success/error paths; DataSchema builders and Show; DataSource/DataSink helpers.

Implementation mechanics to make WHY/ HOW/ WHAT visible:
- Organize specs by intent: packages or prefixes `why/`, `how/`, `what/` under module test roots, or ScalaTest tags (`@Why`, `@How`, `@What`).
- Every spec starts with a brief preamble (“WHY/HOW/WHAT”) explaining the invariant or behavior being demonstrated.
- Pull the “WHY” into compile‑fail and property‑based proofs first; only then add “HOW” behavioral specs and finally “WHAT” feature checks.
- Review checklist on PRs: each change adds or updates at least one WHY spec when touching foundational behavior.

Acceptance heuristics for distribution (core):
- ≥30% of executed statements covered by WHY‑class specs (typestate, compile‑fail, laws, invariants).
- ≥50% by HOW‑class specs (algebras, builders, combinators, effect semantics, observability).
- Remainder by WHAT‑class specs (codecs, types, helpers) — never the majority in a given PR.

## Guardrails
- Statement coverage (scoverage) is our primary gate; branch coverage is tracked but not gated. Excludes remain minimal (only macro/legacy witness already configured in build.sbt).
- Prefer `IO[Assertion]` style over `unsafe*` in tests; only use `unsafeRunSync` for focused interop or when a test runner requires it.
- Default to public APIs and black‑box behavior; white‑box testing only where necessary and justified.

## Current Baseline (local)
- modules/core statement coverage: ~53.6% (as of 2025-10-04 via `sbt clean coverage "core/test" coverageReport`).
- Largest uncovered surfaces (from HTML report):
  - `PipelineBuilder` (typestate, stage folding, metadata)
  - `PipelineCombinators` (sequence/parallel/conditional/retry/batch)
  - Observability/lineage helpers (no-op fallbacks, run-id generation)
  - Internal macros/AST are compile‑time; runtime coverage is not applicable (remain excluded)

## Strategy (4 Milestones)
1) 53% → 65% — Builder & Combinators
   - `PipelineBuilder`: withDescription/withLineageEmitter/withTracer, typed source→(noTransform|transform)→typed sink, build path, stage names, metadata tags.
   - `PipelineCombinators`: sequence (NonEmptyList), parallel (compose + metadata), conditional (both branches), retry (cap, backoff), batch.
   - Negative flow checks aligned with actual behavior (no brittle assumptions).

2) 65% → 75% — Typeclasses & Codecs Matrix
   - `DataEncoder/DataDecoder.instance` defaults (estimateSize/supportsFormat/optimizationHints) executed.
   - DefaultCodecs: success/failure paths for JSON/JSONL/CSV and explicit unsupported branches where applicable.
   - Metrics: `MetricsCollector.instance/noop`, `collectTimed` custom metric, `aggregate`, `ProcessingMetrics.combine/custom`.

3) 75% → 85% — Effects & Syntax
   - Effect syntax: bracket/resource, race/parMapN/parWith, timed/timeout/logging; assert both happy‑path and error‑path behavior.
   - `PipelineSyntax` DSL sugar: filter/validate/quality/retry/timeout/config wired to builder/interpreters at least once.
   - Schema builders and `Show` rendering (eventSchema/userSchema) validated by behavior, not string‑only tests.

4) 85% → 90% — Edges & Observability
   - Observability: Tracer.noop semantics; prometheus/noop selection where feasible without external deps.
   - Lineage helpers: noop emitter flows and run‑id generation (env/property vs generated) executed.
   - Error ADTs utility surfaces (`withContext/withCause` stubs) exercised where meaningful.

## Test Design Principles (Precision & Learning)
- Prove invariants: typestate safety, schema conformance evidence, idempotent combinators, resource safety.
- Prefer property‑based checks for algebraic laws (e.g., composition/identity laws) and data‑shaping invariants.
- For time/concurrency, use deterministic test runtimes where needed (e.g., cats‑effect TestControl/TestContext) to avoid flakiness and slow sleeps.
- Keep tests fast: run under a few seconds locally/CI; avoid real network and heavy engines in unit scope.

## Execution Plan (Now → Next)
- Landed (M1-start):
  - WHY: Tagged compile‑time contract spec; invariants documented and enforced via compile‑fail posture.
  - HOW: Added `PipelineBuilderSpec` (mutators + typed source/sink + tracer/lineage tags), `PipelineCombinatorsSpec` (sequence/parallel/conditional/retry/batch), effects error‑paths (`retryWithBackoff`, `timeoutAfter`, `raceWith`, `parWith`, `parMapN`).
  - WHAT: Codecs matrix fixes; DataSchema Show; DataSource/DataSink option setters (GCS/S3/JDBC/Local); lineage helpers + property‑based run‑id branch; tracer no‑op semantics.
  - Net movement: core stmt coverage ~+3pp cumulative since baseline, with stability preserved (all tests green).

- Next PR (M1-complete target: +8–10pp additional on core):
  1) PipelineBuilder (HOW): multi‑transform chain and filter/validate/quality wiring through `PipelineSyntax` to exercise stage folds and metadata counters; assert lineage/tracer wrapping path executes during fold.
  2) Observability/lineage (HOW): add failure mapping tests for Http emitter (already started), plus helpers for COMPLETE/FAIL paths checked with deterministic IDs; verify tag propagation.
  3) Effects syntax (HOW): deterministic‑time variants using cats‑effect TestControl for timeout/retry backoff branches; property‑based checks for par composition associativity/commutativity where applicable.
  4) Data types (WHAT): extend sinks/sources option coverage (Local write mode, DataSource schema attachments), and DataSchema builder edge cases (optional/required field sets).

Milestone acceptance: measured +8–10pp stmt increase on `modules/core` and ≥50% of new coverage coming from HOW‑class specs, ≥30% from WHY.

## Frozen Next Steps (Do Not Drift)

Locked immediate work for the next PR to land a single +8–10pp core statement coverage increase without sacrificing quality.

- PipelineBuilder fold & DSL wiring (HOW)
  - Add `PipelineBuilderDslSpec` exercising:
    - Multiple transforms (≥3) composed through the DSL to increment `metadata.transformations` and preserve stage order.
    - `filter/validate/quality` steps: success + failure paths (failure should short‑circuit with documented exceptions).
    - `withConfig` mutator: set/non‑validated (assert value is attached; no validation attempt).
    - Tracer wrapping: provide a minimal tracer that records visited stage names; assert every stage is wrapped.
  - Acceptance: transformations count matches chain length; recorded stage sequence equals metadata.stages; config present; tracer observed all stages.

- Effects with deterministic time (HOW)
  - Add `EffectsDeterministicTimeSpec` using cats‑effect TestControl/TestContext:
    - `retryWithBackoff`: verify exact backoff sequence and max‑retry termination without real sleeps.
    - `timeoutAfter`: tick virtual time to trigger timeout; assert TimeoutException.
    - `raceWith/parWith/parMapN`: property‑style checks for failure propagation and simple algebraic sanity (no flakiness).
  - Acceptance: no wall‑clock sleeps; specs complete under 1s locally; asserts use virtual time advances.

- Lineage helpers & failure mapping (HOW)
  - Extend `OpenLineageEmitterHelpersSpec` to cover:
    - `emitPipelineComplete/Fail` helper branches with deterministic runId from property and from env var.
    - Http emitter failure mapping remains Left(LineageError) (no network dependence).
  - Acceptance: both property and env branches covered; error mapping asserted.

- Data types & IO options (WHAT)
  - Extend option setters/tests:
    - Local sink: assert write mode toggles and compression round‑trips.
    - DataSource.withSchema attaches schemas and is reflected at use‑site helpers.
    - WriteMode: cover `ErrorIfExists` and `Ignore`.
  - Acceptance: assertions on getters for each setter; all branches executed at least once.

- Tagging & execution
  - Tag new specs per philosophy: HOW for builder/effects/lineage; WHAT for option surfaces.
  - Run focused loops: `sbt "core/testOnly -- -n com.flowforge.tags.HOW"` and `...WHAT`.

- Target for this PR
  - Measured core stmt coverage delta: +8–10pp over current (~54.5% → ~62–65%).
  - Stability: all tests green on repeated runs; no sleeps; no network.


## CI/Tooling
- Local workflow: `sbt clean coverage "core/test" coverageReport`; aggregate via `coverageAggregate` if needed for multi‑module reports.
- Keep excludes minimal and documented in `build.sbt`.

## Risks & Mitigations
- Macro internals not coverable at runtime → validated by compile‑fail tests instead.
- Timing and concurrency flakes → deterministic test runtime, avoid real sleeps, assert behaviors not durations.

## External references (for approach)
- ScalaTest property‑based testing guide; ScalaCheck integration and generator‑driven checks.
- ScalaCheck user guide and API docs.
- sbt‑scoverage plugin usage (coverage/coverageReport/coverageAggregate) and excludes.
- Cats Effect testing best practices; deterministic time with TestControl/TestContext; cats‑effect‑testing for ScalaTest.
