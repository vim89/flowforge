# EVIDENCE — Effect System & Purity

## 1) Problem & Constraints
- **Goal**: Verify pure Spark transforms; ensure single-effect-per-module discipline.
- **Non-goals**: Removing IO/ZIO instances from core.
- **Hard constraints**: Spark ops pure; F[_] at IO boundaries.

## 2) Codebase Recon
- **Modules involved**: core (EffectSystem, IO/ZIO instances), engines-spark, connectors, others pulling `common` deps.
- **Key files**:
  - `modules/core/algebra/EffectSystem.scala`
  - `modules/core/instances/EffectInstances.scala`
  - `modules/engines-spark/SparkDataAlgebra.scala`
  - `project/Dependencies.scala` (`common` pulls both IO and ZIO widely)
- **Findings**:
  - Transform methods are pure in Spark algebra (good).
  - Many modules inherit both IO and ZIO via `common` (violates “one effect per module” intent).

## 2.1) Detailed Findings
- `project/Dependencies.scala`: `common` aggregates `effectSystems` and is reused in many `forModule` branches, bringing IO and ZIO to leaf modules.
- No leaf module appears to directly rely on both stacks; most use `EffectSystem[F]` constraints, so the broad deps are unnecessary and risky.
- SparkDataAlgebra correctly keeps pure transforms; IO boundaries use EffectSystem as expected.

## 3) Prior Art & Sources
- ADR-012; AGENTS.md (effect rules); EffectSystemResearch (archived).

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Keep both only in core; slim leaf deps | Cleaner modules | Some refactor | Accepted (phased)
| Leave as-is | No work | Violates rule; heavier surface | Rejected

**Decision sketch**: Gradually reduce leaf modules to depend only on `EffectSystem[F]` (no direct IO/ZIO) and trim deps.

## 5) Edge Cases & Invariants
- Test modules can depend on either stack for convenience.

## 6) Success Criteria
- Leaf modules compile without importing both effect libs; EffectSystem constraints only.

## 7) Recommendations (Production-grade)
- Keep both IO and ZIO instances in core only; trim leaf module dependencies to avoid pulling both stacks where not needed.
- Add a lint/check in CI to detect direct imports of `cats.effect.IO` or `zio.ZIO` in leaf modules (allowances in tests if needed).
- Consider documenting recommended effect choice per module (e.g., core supports both; engines-spark tests on IO by default) to simplify onboarding.

## 8) Next Steps (Concrete)
- Update `forModule` cases in `project/Dependencies.scala` to drop `effectSystems` inclusion for leaf modules, where usage is absent.
- Grep leaf modules for IO/ZIO imports and refactor to `EffectSystem[F]` constraints if any are present.
- Add a GitHub Action job to fail if leaf modules import both stacks in main sources.
