```markdown
# ADR 000 — <Title>

- **Status**: Proposed / Accepted / Superseded by ADR-XXX
- **Date**: <YYYY-MM-DD>

## Context
- Problem statement and scope.
- Constraints and invariants (see AGENTS.md §Build & Repo Invariants, Effect Rules).
- Prior behavior and observed gaps (reference EVIDENCE.md section/lines).

## Decision
- The chosen approach (link Option from EVIDENCE.md), explicitly stating:
  - Impacted modules/packages.
  - Effect boundaries (pure vs F[_]).
  - Contract implications (compile-time vs runtime checks).

## Consequences
- Positive: <benefits>
- Negative: <trade-offs/risks>
- Compatibility: <API/behavior changes>
- Operational: <perf, observability, testing>

## Alternatives Considered
- Option A — <why rejected>
- Option B — <why rejected>

## Verification
- Build/test steps (sbt compile/scalafmtAll/test).
- Specific tests or examples validating this ADR.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`
- House Rules: `AGENTS.md`
- Related: <issues/PRs/design docs>
```
