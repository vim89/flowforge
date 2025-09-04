# EVIDENCE — Contracts Authoring & Operating Model

## 1) Problem & Constraints
- **Goal**: Verify non-technical authoring + publisher CI + SDK JARs exist or define gaps.
- **Non-goals**: Building a portal now.
- **Hard constraints**: Contracts-first; compile/build gates later.

## 2) Codebase Recon
- **Modules involved**: none dedicated; CLIs exist (contracts-extractor-cli), but no publisher CI.
- **Key files**:
  - `modules/contracts-extractor-cli/**` (schema extraction CLI)
  - No contracts repo wiring or codegen plugin present.
- **APIs impacted**: TypedSource/TypedSink present; no SDK pipeline.

## 3) Prior Art & Sources
- ADR-010; archived CONTRACTS_* docs.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Minimal publisher CI (contracts repo) | Realistic path | Separate repo needed | Accepted (tracked)
| Inline codegen in this repo | Fast demo | Conflates concerns | Rejected

**Decision sketch**: Stand up minimal publisher CI outside; in this repo add consumer wiring and examples.

## 5) Edge Cases & Invariants
- Schema/backward policy must be explicit; offline mode for local builds.

## 6) Success Criteria
- Documented publisher CI design; consumer demo compiles with typed endpoints.
