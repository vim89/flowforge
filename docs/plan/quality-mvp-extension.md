# PLAN - Quality MVP Extension (Deequ: Range + Pattern)

## 1) Scope (Minimal Viable Change)
- Extend `quality-deequ` adapter to support `Range` and `Pattern` constraints; add basic metrics for DQ runs.

## 2) Deliverables
- Mapping:
  - `QualityConstraint.Range(field, min, max)` → Deequ `isContainedInRange`/equivalent.
  - `QualityConstraint.Pattern(field, regex)` → Deequ `hasPattern`.
- Metrics:
  - Duration per DQ run (histogram).
  - Violations count by rule (counter).

## 3) Tasks
1. Extend `DeequAdapter.runChecks` to translate new rules; craft clear rule names/messages.
2. Add timing via `EffectSystem.timed`-like utility or `System.nanoTime` locally; emit Prometheus metrics if available.
3. Add unit tests for pass/fail cases (local Spark session), alongside existing not_null/unique tests.

## 4) Tests
- Extend `DeequAdapterSpec` to include:
  - Range pass/fail (numeric field).
  - Pattern pass/fail (string field with regex).

## 5) Risks & Mitigations
- Deequ API surface variations: keep implementation minimal and pin version from `Dependencies.scala`.
- Performance: tests use tiny local DFs; production runs remain bounded to checks performed.

## 6) Validation & Acceptance
- New rules mapped and covered by unit tests.
- Metrics emitted when adapter runs (if metrics available); no API changes; CI green.

