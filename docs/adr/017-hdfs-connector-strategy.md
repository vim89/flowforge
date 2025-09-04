```markdown
# ADR 017 — HDFS Connector Strategy

- Status: Accepted
- Date: 2025-09-04

## Context
HDFS connector documentation exists; filesystem connector code and HDFS test are present. Other cloud connectors are pending.

## Decision
- Keep filesystem/HDFS as baseline connectors with clear interfaces; evolve cloud connectors following the same patterns; avoid tight engine coupling.

## Consequences
- Pros: Immediate functionality; consistent adapter pattern for future providers.
- Cons: Limited cloud coverage until implemented.

## Verification
- Examples and tests use filesystem/HDFS now; roadmap tracks cloud connector additions.

## References
- Source: `docs/archive/connectors/HDFS.md`
- Plan: `docs/plan/UMBRELLA.md`

## End Goal (Big Picture)
- Solid filesystem baseline with examples/tests; blueprint for cloud connectors.

## Milestones
- M1: Example + ITs stabilized.
- M2: Cloud connector parity prioritized.

## Open Questions
- Handling security/auth patterns uniformly across providers.

## Appendix: Source Notes (archive/connectors/HDFS.md)

- Source: `docs/archive/connectors/HDFS.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Captures HDFS connector setup, dependencies (Hadoop client artifacts), configuration options, and runtime considerations; positions HDFS/filesystem as baseline connectors pending cloud parity.
```
