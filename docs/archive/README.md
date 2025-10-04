# Archive Directory

This directory contains **historical documentation** that has been superseded by newer Architecture Decision Records (ADRs) or evidence documents. These files are preserved for historical context and lineage tracking.

## Archive Policy

- **DO NOT** use archived documents as authoritative sources
- **DO** refer to the superseding documents listed below
- **DO** preserve these for historical reference and decision lineage

## Archive Index

### Design Documents (Superseded by ADRs)

| Archived Document | Superseded By | Date Archived |
|-------------------|---------------|---------------|
| `design/CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md` | `docs/adr/010-contracts-authoring-operating-model.md` | 2025-09-04 |
| `design/CONTRACTS_AUTHORING_GUIDE.md` | `docs/adr/010-contracts-authoring-operating-model.md` | 2025-09-04 |
| `design/CONTRACTS_COMPILE_AND_BUILD_GATES.md` | `docs/adr/011-contracts-compile-build-gates.md` | 2025-09-04 |
| `design/EffectSystemResearch.md` | `docs/adr/012-effect-system-decision.md` | 2025-09-04 |
| `design/INFRASTRUCTURE_LAYER.md` | `docs/adr/013-infrastructure-layer.md` | 2025-09-04 |
| `design/QA_PLAN.md` | `docs/adr/014-qa-strategy.md` | 2025-09-04 |
| `design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md` | `docs/adr/015-scaffolding-vs-production-policy.md` | 2025-09-04 |
| `design/GROUND_REALITY_REPORT.md` | `docs/adr/016-ground-reality-governance.md` | 2025-09-04 |
| `design/GROUND_REALITY_REPORT_FULL.md` | `docs/adr/016-ground-reality-governance.md` | 2025-09-04 |
| `design/ALIGNMENT_STATUS.md` | `docs/adr/016-ground-reality-governance.md` | 2025-09-04 |
| `design/PRODUCTION_REALITY_UPDATE.md` | `docs/adr/016-ground-reality-governance.md` | 2025-09-04 |
| `design/RoadmapProposal.md` | `docs/adr/018-roadmap-baseline.md` | 2025-09-04 |
| `design/design.md` | Multiple ADRs (see `docs/adr/INDEX.md`) | 2025-09-04 |
| `design/Findings.md` | `docs/evidence/unvarnished-review.md` | 2025-09-04 |
| `design/IMPLEMENTATION_TODO.md` | `docs/plan/v1.0-readiness.md` | 2025-09-04 |

### Examples (Superseded by ADRs)

| Archived Document | Superseded By | Date Archived |
|-------------------|---------------|---------------|
| `examples/TYPED_CONTRACT_PIPELINES.md` | `docs/adr/019-typed-contract-pipelines-example.md` | 2025-09-04 |

### Connectors (Superseded by ADRs)

| Archived Document | Superseded By | Date Archived |
|-------------------|---------------|---------------|
| `connectors/HDFS.md` | `docs/adr/017-hdfs-connector-strategy.md` | 2025-09-04 |

### Reference Documents (Historical Context)

| Archived Document | Status | Notes |
|-------------------|--------|-------|
| `reference/FlowForge - Data Engineering Excellence Platform.md` | Vision document | Contains original vision and goals |
| `reference/FlowForge Complete System Architecture Overhaul.md` | Historical architecture | Early architecture exploration |
| `reference/30-Minute Production Setup Goal *.md` | Goal document | Original setup time goal |
| `reference/Complete Project Archive *.md` | Historical snapshot | Project state at specific points |
| `reference/Complete File Archive - All Project Content.md` | Historical snapshot | File inventory at specific point |
| `reference/AffectedPartitions.md` | Historical feature | Early partitioning strategy |

### Brainstorming (Exploratory)

| Archived Document | Status | Notes |
|-------------------|--------|-------|
| `brainstorming/flowforge/kyo-caprese.md` | Exploratory research | Kyo + Caprese effect system exploration |

### History

| Archived Document | Purpose |
|-------------------|---------|
| `history/2025-09-17-history.md` | Git history cleanup record |

## Finding Authoritative Sources

For any topic, use this priority order:

1. **Evidence** (`docs/evidence/`) - Current reality, what IS
2. **Plans** (`docs/plan/`) - Future work, what WILL BE
3. **ADRs** (`docs/adr/`) - Architectural decisions, immutable
4. **Design** (`docs/design/`) - Active design documents
5. **Archive** (`docs/archive/`) - Historical reference only

See `docs/adr/INDEX.md` for the complete mapping of archived sources to their superseding ADRs.

## Contributing

When archiving new documents:

1. Add a "Superseded by" banner at the top of the archived file
2. Update this README with the mapping
3. Update `docs/adr/INDEX.md` if the superseding document is an ADR
4. Use git mv to preserve history: `git mv old/path.md docs/archive/category/path.md`
