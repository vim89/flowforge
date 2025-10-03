# Canonical Sources Map

This document defines the **single source of truth** for every major topic in FlowForge. Use this to avoid confusion when multiple documents discuss the same topic.

## 🎯 Purpose

When multiple documents cover the same topic, this map tells you which one to trust as the authoritative source.

## 📚 Authority Hierarchy

Documents are authoritative in this order:

1. **Evidence** (`docs/evidence/`) - Current reality, what IS (updated continuously)
2. **Plans** (`docs/plan/`) - Future work, what WILL BE (becomes evidence when implemented)
3. **ADRs** (`docs/adr/`) - Architectural decisions (immutable, historical record)
4. **Design** (`docs/design/`) - Active design documents
5. **Archive** (`docs/archive/`) - Historical reference only (DO NOT USE as source)

---

## 📖 Topic to Source Mapping

### Contracts

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Contracts Authoring & Operating Model** | `docs/adr/010-contracts-authoring-operating-model.md` | `docs/evidence/contracts-operating-model.md` | `docs/archive/design/CONTRACTS_AUTHORING_*.md` |
| **Contracts Compile & Build Gates** | `docs/adr/011-contracts-compile-build-gates.md` | `docs/evidence/compile-build-gates.md` | `docs/archive/design/CONTRACTS_COMPILE_AND_BUILD_GATES.md` |
| **Contracts Current Implementation** | `docs/evidence/compile-time-contract-implementation.md` | `IMPLEMENTATION-SUMMARY.md` | - |
| **Contracts Overview** | `docs/contracts/OVERVIEW.md` | `docs/adr/010-*`, `docs/adr/011-*` | - |
| **Typed Contract Pipelines Example** | `docs/adr/019-typed-contract-pipelines-example.md` | `docs/evidence/typed-example.md` | `docs/archive/examples/TYPED_CONTRACT_PIPELINES.md` |
| **Contracts Source of Truth & Codegen** | `docs/adr/021-contracts-source-of-truth-and-codegen.md` | - | - |

### Effect System

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Effect System Decision** | `docs/adr/012-effect-system-decision.md` | `docs/effects/bring-your-own-effect.md` | `docs/archive/design/EffectSystemResearch.md` |
| **Effect System Abstraction** | `docs/adr/001-effect-system-abstraction.md` | `docs/adr/012-*` | - |
| **Spark Purity & IO Boundaries** | `docs/adr/002-spark-purity-and-io-boundaries.md` | `docs/evidence/effect-per-module-and-purity.md` | - |
| **Effect Per Module** | `docs/evidence/effect-per-module-and-purity.md` | `docs/adr/002-*` | - |

### Infrastructure & Platform

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Infrastructure Layer** | `docs/adr/013-infrastructure-layer.md` | `docs/evidence/infrastructure-layer.md` | `docs/archive/design/INFRASTRUCTURE_LAYER.md` |
| **Observability & Infra** | `docs/adr/007-observability-and-infra-layer.md` | `docs/plan/observability.md` | - |
| **Configuration Management** | `docs/adr/013-infrastructure-layer.md` (section) | - | - |
| **Audit Logging** | `docs/adr/023-audit-logging.md` | `docs/plan/audit-logging-sprint-plan.md` | - |

### Quality & Testing

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **QA Strategy** | `docs/adr/014-qa-strategy.md` | `docs/evidence/qa-strategy.md` | `docs/archive/design/QA_PLAN.md` |
| **Quality & Deequ** | `docs/adr/005-quality-and-deequ-adapter.md` | `docs/evidence/quality-deequ.md` | - |
| **Safe Generic Error Handling** | `docs/adr/022-safe-generic-error-handling.md` | - | - |
| **Compile-Fail Tests** | `modules/compile-fail-tests/README.md` | `docs/adr/014-*` | - |

### Connectors

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Connectors Strategy** | `docs/adr/006-connectors-strategy.md` | - | - |
| **HDFS Connector** | `docs/adr/017-hdfs-connector-strategy.md` | - | `docs/archive/connectors/HDFS.md` |
| **Multi-Cloud Storage** | `docs/operating/multi-cloud-storage.md` | `docs/adr/006-*` | - |
| **GCS Connector Plan** | `docs/plan/connectors-gcs.md` | - | - |

### Engines & Execution

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Modules, Engines & Templates** | `docs/adr/004-modules-engines-and-templates-alignment.md` | - | - |
| **Spark Engine Plan** | `docs/plan/engines-spark.md` | `docs/adr/002-*` | - |
| **Partitions & Table Ops** | `docs/plan/partitions-and-table-ops.md` | - | - |

### Architecture & Design

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Core Design** | `docs/design/core-design.md` | `docs/diagrams/architecture.md` | `docs/archive/design/design.md` |
| **Runners & Connectors** | `docs/design/runners-and-connectors.md` | `docs/adr/006-*` | - |
| **Framework Behaviors** | `docs/design/framework-behaviors.md` | - | - |
| **Compile-Time Contracts Diagrams** | `docs/diagrams/compile-time-contracts/` | - | - |

### Project Management & Process

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Ground Reality & Governance** | `docs/adr/016-ground-reality-governance.md` | `docs/evidence/unvarnished-review.md` | `docs/archive/design/GROUND_REALITY_*.md` |
| **Roadmap Baseline** | `docs/adr/018-roadmap-baseline.md` | `docs/plan/v1.0-readiness.md` | `docs/archive/design/RoadmapProposal.md` |
| **v1.0 Readiness** | `docs/plan/v1.0-readiness.md` | `docs/quality/release-criteria.md` | - |
| **Scaffolding vs Production** | `docs/adr/015-scaffolding-vs-production-policy.md` | - | `docs/archive/design/SCAFFOLDING_VS_PRODUCTION_AUDIT.md` |
| **Unvarnished Review** | `docs/evidence/unvarnished-review.md` | `docs/evidence/unvarnished-review-2025-10-03.md` | `docs/archive/design/Findings.md` |

### Templates & Developer Experience

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Templates Alignment** | `docs/plan/templates-alignment.md` | `docs/adr/004-*` | - |
| **DX Dogfooding** | `docs/plan/dx-dogfooding.md` | - | - |
| **Giter8 Template** | `flowforge.g8/README.md` | `docs/templates/` | - |

### Compile-Time Contracts (Active Work)

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **End-to-End Compile-Time** | `docs/plan/End-to-End-Compile-time.md` | `docs/evidence/compile-time-contract-implementation.md` | - |
| **100% Compile-Time Contracts** | `docs/plan/100-percent-compile-time-contracts.md` | - | - |
| **Compile-Time Issues** | `docs/plan/End-to-End-Compile-Issues.md` | - | - |
| **Compile Build Gates** | `docs/plan/compile-build-gates.md` | `docs/adr/011-*` | - |

### Scala & Language

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Scala 3 Alignment** | `docs/evidence/scala3-alignment.md` | `COMPATIBILITY.md` | - |
| **Scala 2/3 Derivation Facade** | `docs/adr/ADR-Scala2-3-Derivation-Facade-and-SchemaAST.md` | - | - |
| **Idiomatic Scala 2 Refactor** | `docs/plan/refactor-idiomatic-scala2.md` | - | - |

### Migration & Compatibility

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Migration Guide** | `MIGRATION.md` | `docs/migration/` | - |
| **Compatibility Matrix** | `COMPATIBILITY.md` | `docs/evidence/scala3-alignment.md` | - |
| **Version Management** | `VERSION_MANAGEMENT.md` | - | - |

### Getting Started & Onboarding

| Topic | Canonical Source | Also See | Archived |
|-------|-----------------|----------|----------|
| **Getting Started** | `docs/getting-started.md` | `README.md`, `docs/start-here.md` | - |
| **Quick Start** | `docs/getting-started-quick.md` | `docs/getting-started.md` | - |
| **Onboarding** | `docs/onboarding.md` | `AGENTS.md` | - |
| **How It Fails** | `docs/how-it-fails.md` | - | - |
| **Why Compile-Time** | `docs/why-compile-time.md` | `docs/why/` | - |

---

## 🔍 How to Use This Map

### For Contributors
1. Find your topic in the table above
2. Use the "Canonical Source" as your primary reference
3. Check "Also See" for additional context
4. Ignore "Archived" documents (historical reference only)

### For Maintainers
1. When creating new documentation, check if topic exists here
2. If it exists, either update canonical source OR create new canonical source and archive old one
3. Always update this map when archiving or creating new canonical sources
4. Link from non-canonical docs to canonical source with banner

### Document Status Banners

Add these banners to non-canonical documents:

**For archived documents:**
```markdown
> **Archived**: Superseded by [`docs/adr/XXX-name.md`](../adr/XXX-name.md). See [Canonical Sources Map](../CANONICAL_SOURCES.md).
```

**For supplementary documents:**
```markdown
> **Note**: This is supplementary documentation. For authoritative source, see [`docs/adr/XXX-name.md`](../adr/XXX-name.md).
```

---

## 🤝 Contributing

When you:
- **Archive a document**: Add entry to this map, update "Archived" column
- **Create canonical source**: Add entry to this map as "Canonical Source"
- **Find duplicate topic**: Consolidate or designate one as canonical, update map

---

**Last Updated**: 2025-10-03
**Maintainer**: FlowForge Core Team
