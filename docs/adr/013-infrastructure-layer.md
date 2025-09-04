```markdown
# ADR 013 — Infrastructure Layer Implementation

- Status: Accepted
- Date: 2025-09-04

## Context
Infrastructure layer (resource safety, config, logging/metrics/tracing, testing harness) is fully designed but partially implemented.

## Decision
- Implement ResourceSafety, ConfigurationManagement, StructuredLogger, MetricsCollector, DistributedTracing, and a basic testing harness as modular services using `EffectSystem[F]`.
- Wire observability hooks at IO boundaries in engines/connectors.

## Consequences
- Pros: Consistent cross-cutting services; safer IO; better diagnosability.
- Cons: Additional surface area; needs careful dependency layout to avoid coupling.

## Verification
- Unit tests for each service; sample instrumentation present in Spark algebra; compile green.

## References
- Source: `docs/design/INFRASTRUCTURE_LAYER.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- A consistent infrastructure layer offering logging, metrics, tracing, config, and safety as effectful services.

## Milestones
- M1: Minimal implementations + usage examples.
- M2: Optional tracing/lineage.

## Open Questions
- Which metrics/tracing stacks to standardize on for v1.
```
