# ADR 021 — Contracts Source of Truth and Codegen (g8 + CI, no avro4s/avrohugger at build)

- Status: Accepted
- Date: 2025-09-05

## Context
We need a contract authoring and propagation model that:
- Empowers non‑technical stakeholders (via GitHub Actions Forms) to author/update contracts.
- Yields compile‑time safety for Scala consumers (typed case classes + typed endpoints) and CI‑time physical schema gates (validation‑cli).
- Preserves cross‑language compatibility (Avro `.avsc`), without introducing flaky local codegen or heavy sbt plugins.

Earlier designs proposed avro4s or sbt‑avrohugger. We now prefer a CI‑first approach where Forms are the source of truth for a contract model; CI generates both the canonical Scala case classes and Avro `.avsc` deterministically.

## Decision
- Source of Truth: GitHub Actions Form inputs (domain, entity, fields, nullability, metadata, version, policy).
- Code artifacts generated in CI (single step):
  - Scala case classes per entity under a contracts SDK module/repo.
  - Avro schemas (Avro JSON `.avsc`) for cross‑language interoperability — optional, controlled via a CI flag (see Toggle below).
- Tools:
  - giter8: to template case classes and companion boilerplate non‑interactively in CI using Form inputs.
  - A tiny CI script/action to render `.avsc` from Form inputs (field list → Avro JSON) without relying on avro4s/avrohugger at build time (can be disabled via a flag).
- Consumers:
  - Depend on the published contracts SDK JAR (contains case classes) for typed compile gates.
  - CI uses validation‑cli with `.avsc` (downloaded from SDK artifacts or repo) to validate physical schemas; PRs fail on mismatches.
- No sbt AutoPlugin; no local avro4s/avrohugger codegen at build time.

## Consequences
- Pros:
  - Clear separation of concerns: CI is the single generation point; developers consume stable artifacts.
  - Non‑technical authoring via Forms; diffs are reviewable (case classes + `.avsc`).
  - Compile‑time (typed) + CI‑time (physical) gates aligned to ADR‑011.
  - Cross‑language support preserved via `.avsc`.
- Cons:
  - Requires maintaining a CI template that maps field definitions to both Scala and Avro consistently.
  - Nested/complex types and defaults must be supported deliberately in the mapping script.

## Verification
- A demo contract PR renders a case class and `.avsc`; contracts SDK compiles; consumer compile with typed gates succeeds.
- CI validation‑cli fails PRs when physical schema drifts from `.avsc`.

## Mapping (initial)
- Supported types: string → `String`; int → `Int`; long → `Long`; double → `Double`; float → `Float`; boolean → `Boolean`.
- Nullability: Scala `Option[T]` ↔ Avro `{"type": ["null", "<t>"]}` (ordered with null first for consistency).
- Names and docs: field names become Scala param names and Avro field names; descriptions become Scala Scaladoc and Avro `doc`.
- Versioning: require `vX.Y.Z` in filenames for `.avsc` and increment on breaking changes (compat policy documented in ADR‑010).

## Risk Mitigations
- Validation script enforces consistency between rendered case class parameters and `.avsc` (fail CI if mismatch).
- Keep an allowlist of types in Forms; advanced types staged later.
- For nested records: initially unsupported or generated as separate case classes + Avro nested records; CI validates referential integrity.

## Alternatives
- avro4s derive-from-case-class at build time (Rejected): Adds build-time codegen, drift risk, and local complexity.
- sbt‑avrohugger generate-from‑avro (Rejected): Useful but heavier; ties codegen to build, not CI; diverges from CI‑first model.
- Maintain `.avsc` as source of truth and generate case classes (Rejected for now): Viable, but we prefer a single templating path with Forms → both outputs.

## Migration Strategy
- For existing demo: replace naive in-repo generator with CI rendering of case classes + `.avsc` from Forms.
- Consumers: update docs to depend on published SDK JAR; adopt CI validation‑cli with `.avsc`.

## References
- ADR‑010 (Contracts Authoring & Operating Model) — updated to Forms + CI publisher.
- ADR‑011 (CI-first compile/build gates) — authoritative physical gates; no sbt plugin.
- Toggle (.avsc generation):
  - Expose a `generateAvsc` boolean in GitHub Actions Forms (or use a repo variable `FF_GENERATE_AVSC`).
  - When false, CI skips `.avsc` generation; typed compile gates still work for Scala consumers, and validation-cli can validate against Spark JSON canonicalization instead.
  - When true (default), CI writes `.avsc` alongside case classes for cross-language consumers and validation-cli.
