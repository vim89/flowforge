# ADR Index and Source Mapping (2025-09-04)

This index maps original documents to their canonical ADRs. Use this as the checklist before archiving.

- Contracts Authoring & Operating Model
  - Source: docs/archive/design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md, docs/archive/design/CONTRACTS_AUTHORING_GUIDE.md
  - ADR: docs/adr/010-contracts-authoring-operating-model.md
  - Notes migrated: 2025-09-04 (see ADR-010 Appendix: Source Notes)
- Contracts Compile & Build Gates
  - Source: docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md
  - ADR: docs/adr/011-contracts-compile-build-gates.md
  - Notes migrated: 2025-09-04 (see ADR-011 Appendix: Source Notes)
- Effect System Decision
  - Source: docs/archive/design/EffectSystemResearch.md, CONTRIBUTING.md (effect rules)
  - ADR: docs/adr/012-effect-system-decision.md
  - Notes migrated: 2025-09-04 (see ADR-012 Appendix: Source Notes)
- Infrastructure Layer
  - Source: docs/archive/design/INFRASTRUCTURE_LAYER.md
  - ADR: docs/adr/013-infrastructure-layer.md
  - Notes migrated: 2025-09-04 (see ADR-013 Appendix: Source Notes)
- QA Strategy
  - Source: docs/archive/design/QA_PLAN.md
  - ADR: docs/adr/014-qa-strategy.md
  - Notes migrated: 2025-09-04 (see ADR-014 Appendix: Source Notes)
- Scaffolding vs Production Policy
  - Source: docs/archive/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md
  - ADR: docs/adr/015-scaffolding-vs-production-policy.md
  - Notes migrated: 2025-09-04 (see ADR-015 Appendix: Source Notes)
- Ground Reality & Alignment Governance
  - Source: docs/archive/design/GROUND_REALITY_REPORT.md, docs/archive/design/GROUND_REALITY_REPORT_FULL.md, docs/archive/design/ALIGNMENT_STATUS.md, docs/archive/design/PRODUCTION_REALITY_UPDATE.md
  - ADR: docs/adr/016-ground-reality-governance.md
  - Notes migrated: 2025-09-04 (see ADR-016 Appendix: Source Notes)
- HDFS Connector Strategy
  - Source: docs/archive/connectors/HDFS.md
  - ADR: docs/adr/017-hdfs-connector-strategy.md
  - Notes migrated: 2025-09-04 (see ADR-017 Appendix: Source Notes)
- Roadmap Baseline
  - Source: docs/archive/design/RoadmapProposal.md
  - ADR: docs/adr/018-roadmap-baseline.md
  - Notes migrated: 2025-09-04 (see ADR-018 Appendix: Source Notes)
- Typed Contract Pipelines Example
  - Source: docs/archive/examples/TYPED_CONTRACT_PIPELINES.md
  - ADR: docs/adr/019-typed-contract-pipelines-example.md
  - Notes migrated: 2025-09-04 (see ADR-019 Appendix: Source Notes)

- Pipeline 30-Point Checklist
  - ADR: docs/adr/020-pipeline-30-point-checklist.md

- Contracts Source of Truth & Codegen
  - ADR: docs/adr/021-contracts-source-of-truth-and-codegen.md

- Safe, Generic Error Handling
  - ADR: docs/adr/022-safe-generic-error-handling.md

- Guardrail Against Shadowing Cats Syntax
  - ADR: docs/adr/025-guardrail-against-shadowing-cats-syntax.md

Coverage and Known Gaps
- Rules_Notes.md: House rules overlap with CONTRIBUTING.md; some nuances may not be duplicated in ADRs. Action: keep archived; cross-link from CONTRIBUTING.md.
- design.md: Diagrams and narrative; ADRs capture decisions, but not all diagrams. Action: keep archived for context.
- IMPLEMENTATION_TODO.md: Detailed to-dos not fully itemized into PLAN.md. Action: import top items into PLAN or a tracked issue list before archiving.
- Findings.md: Historical assessments with numbers/dates; ADR-016 summarizes governance but not every historical datapoint. Action: keep archived.
- Reference docs (docs/reference/*): Visionary material; ADRs/Evidence reflect current truth, but references contain context/value propositions. Action: archive with banners, don’t delete.
- backups/ASSISTANTS.2025-09-03.md and previous-chats/previous-conv1.txt: Historical; keep.

Archive Recommendation
- Do NOT hard-delete. Archive the listed sources under docs/archive/ with a “Superseded by ADR-XXX” banner at top and keep links alive for context/history.
- After 2–3 weeks of usage with no gaps reported, consider pruning only if necessary.
