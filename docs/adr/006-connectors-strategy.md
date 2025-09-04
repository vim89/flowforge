```markdown
# ADR 006 — Connectors Strategy (Local/HDFS now; Cloud later)

- Status: Accepted
- Date: 2025-09-04

## Context
Only Local/HDFS connectors are implemented. Cloud connectors (GCS/S3/BQ/Kafka/Azure) have module names but no code. We need to state a strategy.

## Decision
- Keep Local/HDFS as the default baseline connectors.
- Stage cloud connectors incrementally; use type classes/adapters; avoid engine-specific logic in business layers.

## Consequences
- Pros: Clear path; avoids premature complexity.
- Cons: Reduced immediate surface area.

## Verification
- Connector APIs remain engine-agnostic; examples build against filesystem first.

## References
- Evidence: `docs/plans/templates/EVIDENCE.md` (§2, §3)
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Baseline filesystem connectors; staged cloud connectors using adapters and engine-agnostic interfaces.

## Milestones
- M1: Filesystem/HDFS examples and tests stable.
- M2: Prioritize S3, then GCS/BigQuery.

## Open Questions
- Order and scope of cloud connectors; auth/secret patterns alignment with infra layer.
```
