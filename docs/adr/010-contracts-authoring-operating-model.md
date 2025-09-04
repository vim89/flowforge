```markdown
# ADR 010 — Contracts Authoring & Operating Model

- Status: Accepted
- Date: 2025-09-04

## Context
Non-technical teams need to author data contracts without code; engineers consume typed SDKs. Contracts live in Git with governance and CI publisher.

## Decision
- Use a contracts repo with Avro schemas, DQ YAML, and metadata YAML per domain/entity.
- Provide a portal/PR workflow; CI publishes typed SDK JARs and updates registry/catalog.
- Consumers depend on SDKs and use typed endpoints only.

## Consequences
- Pros: Clear ownership, governance, audit trail; compile-time safety for consumers.
- Cons: CI/publisher complexity; initial portal investment.

## Verification
- Publisher CI validates schemas, compatibility, generates SDKs, and publishes artifacts.

## References
- Source: `docs/design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md`, `docs/design/CONTRACTS_AUTHORING_GUIDE.md`
- Evidence: `docs/plans/templates/EVIDENCE.md`
- Plan: `docs/plans/templates/PLAN.md`

## End Goal (Big Picture)
- Contracts authored by non-technical users, published as typed SDKs, with compile/build-time enforcement downstream.

## Milestones
- M1: Publisher CI spec documented; consumer demo present.
- M2: Codegen/SDK pipeline proven in a separate contracts repo.

## Open Questions
- Which schema/registry stack to adopt first (Avro + Schema Registry baseline)?
```
