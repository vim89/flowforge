```markdown
# ADR 010 — Contracts Authoring & Operating Model

- Status: Accepted
- Date: 2025-09-04

## Context
Non-technical teams need to author data contracts without code; engineers consume typed SDKs. Contracts live in Git with governance and CI publisher.

## Decision
- Use a contracts repo/SDK with Scala case classes and Avro `.avsc` per domain/entity, both rendered from GitHub Actions Forms in CI (see ADR‑021).
- Provide a portal/PR workflow; prefer GitHub Actions Forms as a submission channel for non‑technical authors. CI generates case classes + `.avsc`, publishes typed SDK JARs, and updates registry/catalog.
- Consumers depend on SDKs and use typed endpoints only.

## Consequences
- Pros: Clear ownership, governance, audit trail; compile-time safety for consumers.
- Cons: CI/publisher complexity; initial portal investment.

## Verification
- Publisher CI validates schemas, compatibility, generates SDKs, and publishes artifacts.

## References
- Source: `docs/archive/design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md`, `docs/archive/design/CONTRACTS_AUTHORING_GUIDE.md`
- Evidence: `docs/evidence/contracts-operating-model.md`
- Plan: `docs/plan/contracts-operating-model.md`

## End Goal (Big Picture)
- Contracts authored by non-technical users, published as typed SDKs, with compile/build-time enforcement downstream.

## Milestones
- M1: Publisher CI spec documented; consumer demo present.
- M2: Codegen/SDK pipeline proven in a separate contracts repo.

## Open Questions
- Which schema/registry stack to adopt first (Avro + Schema Registry baseline)?

## Appendix: Source Notes (archive/design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md)

- Source: `docs/archive/design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Describes non-technical contract authoring via portal/PR workflow, roles (stewards/product/architect/platform/consumers), artifacts (schemas, DQ, metadata), and publishing SDK JARs for compile-time safety; emphasizes governance, versioning, and CI enforcement.

## Appendix: Source Notes (archive/design/CONTRACTS_AUTHORING_GUIDE.md)

- Source: `docs/archive/design/CONTRACTS_AUTHORING_GUIDE.md` @ 877826bbf05636a3db581e425901fc490cac224d on 2025-09-04T17:18:50+05:30
- Summary: Practitioner-oriented guide for contracts-as-code: repository layout, schema/DQ/metadata conventions, PR flow, validation steps, and outputs; aligns with ADR-010 operating model.
```
