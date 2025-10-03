# flowforge documentation map

This index organizes all existing documents into clear sections and points to the authoritative sources for current truth.

- Authority order: EVIDENCE → PLAN → ADRs → Design → Reference → Backups/Archive.
- Reality banner: Claim-heavy references now link back to EVIDENCE for ground truth.

## Plans & evidence
- Current reality: `docs/plans/templates/EVIDENCE.md`
- Current plan: `docs/plans/templates/PLAN.md`
- ADR template: `docs/adr/000-template.md`

## Design (canonical ADRs)
- Alignment/status: `docs/adr/016-ground-reality-governance.md`
- Contracts (operating model): `docs/adr/010-contracts-authoring-operating-model.md`
- Contracts build gates: `docs/adr/011-contracts-compile-build-gates.md`
- Infrastructure layer: `docs/adr/013-infrastructure-layer.md`
- Effect system decision (ADR): `docs/adr/012-effect-system-decision.md`
- QA strategy: `docs/adr/014-qa-strategy.md`
- Roadmap baseline: `docs/adr/018-roadmap-baseline.md`
- Scaffolding vs production policy: `docs/adr/015-scaffolding-vs-production-policy.md`
- Typed example: `docs/adr/019-typed-contract-pipelines-example.md`
- ADR index: `docs/adr/INDEX.md`

## Reference (with reality banner)
- 30-minute setup goal: `docs/reference/30-Minute Production Setup Goal 253e51dc9bf0812f8058d4bb4d07ba3f.md`
- Excellence platform: `docs/reference/FlowForge - Data Engineering Excellence Platform.md`
- System architecture overhaul: `docs/reference/FlowForge Complete System Architecture Overhaul.md`
- Complete project archives: `docs/reference/Complete Project Archive & Implementation Strategy 253e51dc9bf0810083d1d26f794cf1cb.md`, `docs/reference/Complete Project Archive & Implementation Strategy 253e51dc9bf08137a898fbbcea1b479b.md`
- Complete file archive: `docs/reference/Complete File Archive - All Project Content.md`

## Connectors
- HDFS: `docs/connectors/HDFS.md`

## Examples
- Typed contract pipelines: `docs/examples/TYPED_CONTRACT_PIPELINES.md`

## Prompts / Operations
- Previous chats: `docs/previous-chats/previous-conv1.txt`
- Backups: `docs/backups/ASSISTANTS.2025-09-03.md`

## Snapshots
- State snapshot: `docs/STATE_SNAPSHOT.md`

## Notes
- For big decisions, create an ADR from `docs/adr/000-template.md` and link it in EVIDENCE and PLAN.
- For any claim-heavy doc, add/update a "Reality" banner referencing `docs/plans/templates/EVIDENCE.md`.
- Developer tooling docs: `docs/contributing/HANDBOOK.md`, `docs/contributing/COVERAGE.md`
## Core Design
- Effect system (EffectSystem[F]) and resources (FlowforgeResource[F,R]): `docs/effects/bring-your-own-effect.md`
- Core design & diagrams: `docs/design/core-design.md`
- Runners & Connectors: `docs/design/runners-and-connectors.md`

## Diagrams
- Architecture: `docs/diagrams/architecture.md`
- Pipeline sequence: `docs/diagrams/pipeline-sequence.md`
- Core class diagram: `docs/diagrams/core-class-diagram.md`
