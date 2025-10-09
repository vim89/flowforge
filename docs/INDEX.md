# FlowForge Documentation Index

> **Master Navigation** - Your guide to all FlowForge documentation

## 🚀 Quick Start Paths

### For New Users
1. [Getting Started](getting-started.md) - Your first FlowForge pipeline in 30 minutes
2. [AGENTS.md](../AGENTS.md) - Complete framework guide (comprehensive overview)
3. [How It Fails](how-it-fails.md) - Understanding FlowForge's compile-time guarantees
4. [Why Compile-Time?](why-compile-time.md) - The philosophy behind FlowForge

### For Data Engineers
1. [Getting Started](getting-started.md) - First pipeline
2. [Contracts Overview](contracts/OVERVIEW.md) - Understanding data contracts
3. [Pipeline Examples](examples/) - Working code samples
4. [30-Point Checklist](adr/020-pipeline-30-point-checklist.md) - Production pipeline requirements

### For Platform Engineers
1. [Operating Guide](operating/) - Multi-cloud deployment
2. [Infrastructure Layer](adr/013-infrastructure-layer.md) - Infrastructure design
3. [Observability](plan/observability.md) - Monitoring and metrics
4. [Version Management](operating/version-management.md) - Release strategy

### For Contributors
1. [Contributing Guide](../CONTRIBUTING.md) - How to contribute
2. [Developer Handbook](contributing/HANDBOOK.md) - Deep technical guide
3. [ADR Index](adr/INDEX.md) - Architectural decisions
4. [Code Coverage Map](contributing/COVERAGE.md) - Implementation status

---

## 📚 Documentation Categories

### ✅ Core Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| [README.md](../README.md) | Project overview, quick links | Everyone |
| [AGENTS.md](../AGENTS.md) | **Complete framework guide** (read this!) | AI agents, architects, senior engineers |
| [Getting Started](getting-started.md) | Your first pipeline | New users |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Contribution workflow | Contributors |
| [CHANGELOG.md](../CHANGELOG.md) | Release history | Users, maintainers |

### 📖 Reference & Guides

| Category | Location | Description |
|----------|----------|-------------|
| **Contracts** | [contracts/](contracts/) | Data contracts and schema management |
| **Effects** | [effects/](effects/) | Effect system (Cats Effect vs ZIO) |
| **Examples** | [examples/](examples/) | Working code samples |
| **Templates** | [templates/](templates/) | Giter8 templates and scaffolding |
| **Tutorials** | [tutorials/](tutorials/) | Step-by-step learning paths |

### 🏗️ Architecture & Design

| Category | Location | Description |
|----------|----------|-------------|
| **ADRs** | [adr/INDEX.md](adr/INDEX.md) | Architectural Decision Records (immutable) |
| **Design Docs** | [design/](design/) | Active design documents |
| **Diagrams** | [diagrams/](diagrams/) | Architecture visualizations |
| **Evidence** | [evidence/](evidence/) | Current reality - what IS |
| **Plans** | [plan/INDEX.md](plan/INDEX.md) | Future work - what WILL BE |

### 🔧 Operations & Deployment

| Category | Location | Description |
|----------|----------|-------------|
| **Operating** | [operating/](operating/) | Production deployment guides |
| **Quality** | [quality/](quality/) | Release criteria and QA |
| **Migration** | [migration/](migration/) | Migration guides |
| **Reference** | [reference/](reference/) | Compatibility, versions |

### 📝 Meta Documentation

| Document | Purpose |
|----------|---------|
| [CANONICAL_SOURCES.md](CANONICAL_SOURCES.md) | Single source of truth map |
| [Archive Index](archive/README.md) | Historical documentation |

---

## 🎯 By Topic

### Compile-Time Contracts (Killer Feature!)
- **Overview**: [Contracts OVERVIEW](contracts/OVERVIEW.md)
- **ADRs**: [ADR-010](adr/010-contracts-authoring-operating-model.md), [ADR-011](adr/011-contracts-compile-build-gates.md)
- **Evidence**: [Implementation Status](evidence/compile-time-contract-implementation.md)
- **Plans**: [100% Coverage](plan/100-percent-compile-time-contracts.md), [End-to-End](plan/End-to-End-Compile-time.md)
- **Proof**: [Compile-Fail Tests](../modules/compile-fail-tests/)
- **Diagrams**: [Visual Guide](diagrams/compile-time-contracts/)

### Effect System
- **ADR**: [Effect System Decision](adr/012-effect-system-decision.md)
- **Guide**: [Bring Your Own Effect](effects/bring-your-own-effect.md)
- **Evidence**: [Per-Module Effects](evidence/effect-per-module-and-purity.md)

### Engines & Execution
- **Spark**: [ADR-002](adr/002-spark-purity-and-io-boundaries.md), [Plan](plan/engines-spark.md)
- **Flink**: [Engine Config](../build.sbt) (Scala 2.12 only)
- **Multi-Engine**: [ADR-004](adr/004-modules-engines-and-templates-alignment.md)

### Connectors
- **Strategy**: [ADR-006](adr/006-connectors-strategy.md)
- **GCS**: [Plan](plan/connectors-gcs.md), [Implementation](../modules/connectors-gcs/)
- **JDBC**: [Implementation](../modules/connectors-jdbc/)
- **Multi-Cloud**: [Operating Guide](operating/multi-cloud-storage.md)

### Quality & Testing
- **Strategy**: [ADR-014](adr/014-qa-strategy.md)
- **Deequ Integration**: [ADR-005](adr/005-quality-and-deequ-adapter.md), [Evidence](evidence/quality-deequ.md)
- **Error Handling**: [ADR-022](adr/022-safe-generic-error-handling.md)
- **Release Criteria**: [Quality Standards](plan/release-criteria.md)

### Infrastructure & Platform
- **Infrastructure**: [ADR-013](adr/013-infrastructure-layer.md), [Evidence](evidence/infrastructure-layer.md)
- **Observability**: [ADR-007](adr/007-observability-and-infra-layer.md), [Plan](plan/observability.md)
- **Audit Logging**: [ADR-023](adr/023-audit-logging.md)
- **Configuration**: [Implementation](evidence/implementation-summary.md)

### Developer Experience
- **Getting Started**: [Guide](getting-started.md)
- **Templates**: [Giter8](../flowforge.g8/)
- **DX Dogfooding**: [Plan](plan/dx-dogfooding.md)
- **Onboarding**: [Guide](onboarding.md)

---

## 🗺️ Documentation Hierarchy

Understanding document authority:

```
1. Evidence (docs/evidence/) ← Current reality, what IS
   ↓
2. Plans (docs/plan/) ← Future work, what WILL BE
   ↓
3. ADRs (docs/adr/) ← Decisions (immutable once accepted)
   ↓
4. Design (docs/design/) ← Active design work
   ↓
5. Archive (docs/archive/) ← Historical only
```

**Golden Rule**: When in doubt, use [CANONICAL_SOURCES.md](CANONICAL_SOURCES.md) to find the authoritative document.

---

## 🔍 Finding What You Need

### Search Strategy

1. **Start Here**: Check this INDEX for category
2. **Check Canonical**: Use [CANONICAL_SOURCES.md](CANONICAL_SOURCES.md) for topic-specific source
3. **Browse ADRs**: See [ADR Index](adr/INDEX.md) for architectural decisions
4. **Check Evidence**: See [Evidence](evidence/) for current implementation status
5. **Review Plans**: See [Plan Index](plan/INDEX.md) for future work

### Common Questions

**Q: How do I create my first pipeline?**
→ [Getting Started](getting-started.md)

**Q: What makes FlowForge unique?**
→ [Why Compile-Time](why-compile-time.md), [How It Fails](how-it-fails.md)

**Q: How do contracts work?**
→ [Contracts Overview](contracts/OVERVIEW.md), [ADR-010](adr/010-contracts-authoring-operating-model.md)

**Q: Cats Effect or ZIO?**
→ [Bring Your Own Effect](effects/bring-your-own-effect.md)

**Q: Is FlowForge v1.0 ready?**
→ [v1.0 Readiness](plan/v1.0-readiness.md), [Release Criteria](plan/release-criteria.md)

**Q: How do I contribute?**
→ [CONTRIBUTING.md](../CONTRIBUTING.md), [Developer Handbook](contributing/HANDBOOK.md)

**Q: What's the roadmap?**
→ [ADR-018 Roadmap](adr/018-roadmap-baseline.md), [Plan Index](plan/INDEX.md)

---

## 📊 Project Status

### Current Focus (2025-10-03)
- ✅ Core compile-time contracts implementation
- 🏗️ v1.0 production readiness
- 🏗️ End-to-end compile-time validation
- 📋 Infrastructure layer completion
- 📋 Observability & monitoring

### Key Metrics
- **Scala Version**: 2.13 (primary), 2.12 (Flink), 3.x (ready, waiting for ecosystem)
- **Effect Systems**: Cats Effect & ZIO (both supported)
- **Engines**: Spark (stable), Flink (2.12 only)
- **Test Coverage**: Core >80%, see [Coverage](contributing/COVERAGE.md)

---

## 🤝 Contributing to Docs

### Documentation Lifecycle
```
Draft → Review → Publish → Maintain → Archive
```

### Creating New Documentation

1. **Choose category**: adr/, evidence/, plan/, design/, etc.
2. **Check canonical sources**: Avoid duplicates, use [CANONICAL_SOURCES.md](CANONICAL_SOURCES.md)
3. **Follow templates**: Use [ADR template](adr/000-template.md) for decisions
4. **Update indexes**: Add to this INDEX and category INDEX
5. **Cross-link**: Link to related docs

### Archiving Documentation

1. Add "Superseded by" banner to old doc
2. Update [Archive Index](archive/README.md)
3. Update [CANONICAL_SOURCES.md](CANONICAL_SOURCES.md)
4. Use `git mv` to preserve history

---

## 📞 Getting Help

- **Docs unclear?**: Open issue with "docs:" label
- **Missing info?**: Check [Evidence](evidence/) for implementation status
- **Want to contribute?**: See [CONTRIBUTING.md](../CONTRIBUTING.md)
- **Questions?**: See [AGENTS.md](../AGENTS.md) first - it's comprehensive!

---

**Last Updated**: 2025-10-03
**Maintainer**: FlowForge Core Team
**Version**: 1.0.0
