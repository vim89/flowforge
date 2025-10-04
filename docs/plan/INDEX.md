# Plan Documents Index

This directory contains **active plans** and **future work** for FlowForge. Plans describe what WILL BE implemented, and become evidence documents when completed.

## Status Legend

- 🎯 **Active** - Currently being implemented
- 📋 **Planned** - Ready for implementation, waiting for resources
- 🔮 **Future** - Long-term goals, not yet scheduled
- ✅ **Completed** - Moved to evidence or archived

---

## 🎯 Active Plans (v1.0 Focus)

| Plan | Status | Owner | Target | Description |
|------|--------|-------|--------|-------------|
| [v1.0-readiness.md](v1.0-readiness.md) | 🎯 Active | Team | v1.0.0 | Production readiness checklist and gaps |
| [v100-plan.md](v100-plan.md) | 🎯 Active | Team | v1.0.0 | Comprehensive v1.0 implementation plan |
| [v10-plan.md](v10-plan.md) | 🎯 Active | Team | v1.0.0 | Core v1.0 roadmap (primary reference) |
| [End-to-End-Compile-time.md](End-to-End-Compile-time.md) | 🎯 Active | Core | v1.0.0 | End-to-end compile-time contracts implementation |
| [100-percent-compile-time-contracts.md](100-percent-compile-time-contracts.md) | 🎯 Active | Contracts | v1.0.0 | Achieving 100% compile-time contract coverage |

## 📋 Planned Work (Post v1.0)

### Infrastructure & Operations
| Plan | Status | Description |
|------|--------|-------------|
| [infrastructure-layer.md](infrastructure-layer.md) | 📋 Planned | Infrastructure layer implementation |
| [observability.md](observability.md) | 📋 Planned | Observability and monitoring setup |
| [audit-logging-sprint-plan.md](audit-logging-sprint-plan.md) | 📋 Planned | Audit logging implementation sprint |

### Quality & Testing
| Plan | Status | Description |
|------|--------|-------------|
| [qa-strategy.md](qa-strategy.md) | 📋 Planned | Quality assurance strategy |
| [quality-deequ.md](quality-deequ.md) | 📋 Planned | Deequ integration for data quality |
| [quality-mvp-extension.md](quality-mvp-extension.md) | 📋 Planned | Quality MVP extensions |

### Connectors & Engines
| Plan | Status | Description |
|------|--------|-------------|
| [connectors-gcs.md](connectors-gcs.md) | 📋 Planned | Google Cloud Storage connector |
| [engines-spark.md](engines-spark.md) | 📋 Planned | Spark engine enhancements |
| [partitions-and-table-ops.md](partitions-and-table-ops.md) | 📋 Planned | Partition management and table operations |

### Developer Experience
| Plan | Status | Description |
|------|--------|-------------|
| [dx-dogfooding.md](dx-dogfooding.md) | 📋 Planned | Developer experience dogfooding |
| [templates-alignment.md](templates-alignment.md) | 📋 Planned | Giter8 templates alignment |

### Contracts & Build
| Plan | Status | Description |
|------|--------|-------------|
| [contracts-operating-model.md](contracts-operating-model.md) | 📋 Planned | Contracts authoring and operating model |
| [compile-build-gates.md](compile-build-gates.md) | 📋 Planned | Compile-time build gates |
| [ci-contracts-submit.md](ci-contracts-submit.md) | 📋 Planned | CI contracts submission workflow |

## 🔮 Future Work (Research & Exploration)

| Plan | Status | Description |
|------|--------|-------------|
| [End-to-End-Compile-Issues.md](End-to-End-Compile-Issues.md) | 🔮 Future | Known compile-time issues and solutions |
| [end-to-end-plan.md](end-to-end-plan.md) | 🔮 Future | End-to-end pipeline patterns |
| [effect-per-module-and-purity.md](effect-per-module-and-purity.md) | 🔮 Future | Effect system per-module strategy |
| [typed-example.md](typed-example.md) | 🔮 Future | Typed contract pipeline examples |

## 🛠️ Refactoring & Code Quality

| Plan | Status | Description |
|------|--------|-------------|
| [refactor-coding-pattern.md](refactor-coding-pattern.md) | 📋 Planned | Coding pattern refactoring |
| [refactor-idiomatic-scala2.md](refactor-idiomatic-scala2.md) | 📋 Planned | Idiomatic Scala 2 refactoring |

## 🗂️ Planning Meta-Documents

| Plan | Description |
|------|-------------|
| [UMBRELLA.md](UMBRELLA.md) | Umbrella plan coordinating all other plans |

---

## 📁 Archived Plans

Historical plan iterations are archived in `archive/iterations/`:

- [v1.0-iteration-2.md](archive/iterations/v1.0-iteration-2.md) - v1.0 planning iteration 2
- [v1.0-iteration-3.md](archive/iterations/v1.0-iteration-3.md) - v1.0 planning iteration 3
- [v1.0-iteration-4.md](archive/iterations/v1.0-iteration-4.md) - v1.0 planning iteration 4
- [v1.0-iteration-5.md](archive/iterations/v1.0-iteration-5.md) - v1.0 planning iteration 5
- [v1.0-iteration-6.md](archive/iterations/v1.0-iteration-6.md) - v1.0 planning iteration 6
- [compile-time-2.md](archive/iterations/compile-time-2.md) - Compile-time contracts iteration 2
- [compile-time-3.md](archive/iterations/compile-time-3.md) - Compile-time contracts iteration 3
- [compile-time-4.md](archive/iterations/compile-time-4.md) - Compile-time contracts iteration 4

---

## 📖 Using This Index

### For Contributors
1. Check active plans for current work
2. Review planned work to avoid duplication
3. Create new plans in this directory with descriptive names
4. Update this index when adding new plans

### Plan Lifecycle
```
Draft → Planned → Active → Completed → Archived/Evidence
```

1. **Draft**: New plan being written
2. **Planned**: Plan approved, waiting for implementation
3. **Active**: Currently being implemented
4. **Completed**: Implementation done, move to `docs/evidence/`
5. **Archived**: Historical, move to `docs/plan/archive/`

### Finding Related Documents
- **Implementation status**: Check `docs/evidence/` for completed work
- **Architectural decisions**: See `docs/adr/INDEX.md`
- **Current reality**: See `docs/evidence/unvarnished-review.md`
- **Historical plans**: See `docs/plan/archive/`

### Priority Order for v1.0
1. v1.0-readiness.md - Release blocking items
2. v100-plan.md - Core implementation plan
3. End-to-End-Compile-time.md - Killer feature completion
4. 100-percent-compile-time-contracts.md - Contract coverage

---

## 🤝 Contributing New Plans

When creating a new plan:

1. Use descriptive kebab-case filename: `feature-name.md`
2. Include status badge at top: `Status: 🎯 Active | 📋 Planned | 🔮 Future`
3. Define clear acceptance criteria
4. Link to related ADRs and evidence
5. Add entry to this INDEX.md
6. Update UMBRELLA.md if this is a major initiative

---

**Last Updated**: 2025-10-03
**Maintainer**: FlowForge Core Team
