# Repository Guidelines

## Project Structure & Module Organization
- Multi‑module SBT repo: sources in `modules/*`, tests under `src/test/scala` mirroring packages.
- Key modules: `core` (algebras, types, builders), `contracts` (typed contracts), `engines-spark`, `connectors`, `quality`, `infrastructure`; CLIs: `validation-cli`, `contracts-extractor-cli`.
- Docs: ADRs `docs/adr/*` (decisions), Plans/Evidence in `docs/plan` and `docs/evidence`, Agents Handbook in `docs/agents`.

## Build, Test, and Development Commands
- `sbt compile` — compile all modules; `sbt test` — run all tests (non‑parallel).
- Formatting: `sbt fmt` / `sbt fmtCheck`; Linting: `sbt fix` / `sbt fixCheck`.
- Focused runs: `sbt core/test`, `sbt engines-spark/compile`.
- CLIs: `sbt validation-cli/run` and `sbt contracts-extractor-cli/run`.

## Coding Style & Naming Conventions
- Scala 2.13; idiomatic FP: immutability, pure functions, explicit effects.
- Keep Spark transforms pure (return `Dataset[...]`), move external IO/orchestration to `F[_]` via `EffectSystem`.
- Prefer typed contracts (`TypedSource/TypedSink/PipelineBuilder2` + `SchemaEq`), use `ValidatedNel` for multi‑rule DQ.
- Naming: `camelCase` vals/defs, `PascalCase` types, lowercase packages; avoid one‑letter names.

## Testing Guidelines
- Frameworks: ScalaTest (+ property/law tests), optional ZIO Test; scoverage target ≥ 80% on changed code.
- Conventions: name specs `*Spec.scala`; place fixtures under the same package path in `src/test/scala`.
- Strategy: test algebras/instances first; integration/engine tests opt‑in and minimal.

## Commit & Pull Request Guidelines
- Commits: imperative subject (≤72 chars) + concise body (what/why); reference ADRs (e.g., ADR‑012 for effect rules).
- PR checklist: `sbt fmtCheck` + `sbt fixCheck` + `sbt test` green; link checker passing; description, test plan, and any CLI logs.
- Scope: keep PRs focused; avoid mixing refactors with feature changes.

## Agent‑Specific Guardrails (Read First)
- Effect boundaries (ADR‑002/012): never wrap pure Spark ops in `F[_]`; all external IO uses `Resource[F,_]`/bracket.
- Contracts‑first (ADR‑011/019): favor typed endpoints; flag legacy/untyped usage in new code.
- Use the 30‑point checklist (ADR‑020) during design/review; minimize shuffles, compute affected partitions first, ensure idempotency and DLQ where relevant.

## Session Workflow (Fast Loop)
- Initialize: read `README.md`, relevant ADRs (see below), and issue context.
- Align: write goals, decide engine and effect (Cats‑Effect/ZIO), pick typed contracts.
- Implement: prefer typed path; keep Spark ops pure; wrap IO in `EffectSystem[F]`.
- Validate: `sbt fullCheck` locally (fmt, scalafix, compile, tests); run targeted module tests.
- PR: link ADRs; include test plan and any CLI logs; keep scope tight.

## Condensed Pipeline Checklist (use ADR‑020 for full)
- Contracts: typed endpoints + SchemaEq; fail on drift.
- Effects: pure Spark transforms; IO in F[_] with Resource/bracket.
- Quality: DQ via ValidatedNel pre/post; profile counts/schema/stats.
- CDC: partition‑aware joins; compute affected partitions; ensure idempotence.
- Performance: minimize shuffles; avoid blanket repartition.
- Reliability: audit + lineage at each stage; DLQ for record‑level failures; metrics exported.

References: ADR Index `docs/adr/INDEX.md`; Agents Handbook `docs/agents/HANDBOOK.md`.
