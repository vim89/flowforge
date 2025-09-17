# EVIDENCE — Modules/Templates Alignment

## 1) Problem & Constraints
- **Goal**: Align declared `templates` module with actual template sources so repo hygiene and discoverability improve without changing build.sbt structure.
- **Non-goals**: Do not modify `build.sbt` structure or introduce new SBT plugins.
- **Hard constraints**:
  - Keep Spark ops pure; effects at boundaries.
  - SBT invariants: no structural edits.

## 2) Codebase Recon
- **Modules involved**: `templates` (declared), repository-level `templates/data-pipeline.g8`.
- **Key files**:
  - `build.sbt` (declares `templates` module)
  - `templates/data-pipeline.g8/**` (actual g8 sources)
  - `modules/templates` (empty)
- **APIs / Types impacted**: None (docs/layout only).
- **Effect boundaries touched**: None.

## 2.1) Detailed Findings
- `build.sbt` declares a `templates` module, but there are no sources under `modules/templates/**`.
- The actual, usable g8 template lives under `templates/data-pipeline.g8/**` at the repository root.
- Developers browsing modules expect template sources there; the empty module path causes confusion and missed discoverability.
- CI and release flows don’t depend on the module path — mirroring files imposes no build graph change.

## 3) Prior Art & Sources

## 3) Prior Art & Sources
- ADR-004 — Modules, Engines, and Templates Alignment.
- CONTRIBUTING.md — Template generation philosophy.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Mirror g8 under `modules/templates` | Improves discoverability; no build change | Duplication until consolidation | Accepted
| Move g8 fully under module | Clean layout | Might require build changes | Rejected (for now)
| Leave as-is | No work now | Confusing empty module | Rejected

**Decision sketch**: Mirror `templates/data-pipeline.g8` into `modules/templates` as files-only; keep build unchanged.

## 5) Edge Cases & Invariants
- Keep sources identical; add README banner clarifying canonical path.

## 6) Success Criteria
- `modules/templates/**` contains mirrored g8 sources.
- No `build.sbt` structural changes; `sbt compile` unaffected.

## 7) Recommendations (Production-grade)
- Mirror current g8 under `modules/templates` to eliminate empty module confusion and ease IDE discoverability.
- Add a `modules/templates/README.md` stating canonical source at `templates/data-pipeline.g8` and a periodic sync note.
- Later, consider full consolidation (moving g8 into the module) in a breaking-change window if we want single-source.

## 8) Next Steps (Concrete)
- Copy the entire `templates/data-pipeline.g8/**` tree under `modules/templates/data-pipeline.g8/**`.
- Create `modules/templates/README.md` with sync instructions and canonical path.
- Verify `sbt compile` is unaffected; add a short DX note in repo README linking both locations.
