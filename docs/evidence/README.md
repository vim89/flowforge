# Evidence Directory

This directory contains **current reality documentation** - what IS, not what we plan or aspire to build.

## 🎯 Purpose

Evidence documents serve as:
- **Ground truth**: Accurate reflection of current implementation
- **Status tracking**: What's done, what's partial, what's missing
- **Reality check**: Prevent docs from drifting into aspiration
- **Progress measurement**: Track implementation against plans

## 📏 The Golden Rule

> **Evidence documents describe REALITY, not ASPIRATION.**

If it says something exists here, it must actually be implemented and working in the codebase.

## 📝 What Goes Here

### ✅ Include
- Current implementation status
- Working features with file/line references
- Test coverage metrics
- Performance benchmarks (actual numbers)
- Known limitations
- Tech debt and gaps

### ❌ Exclude
- Future plans (→ `docs/plan/`)
- Architectural decisions (→ `docs/adr/`)
- Design proposals (→ `docs/design/`)
- Historical context (→ `docs/archive/`)
- Aspirational claims

## 📚 Key Evidence Documents

| Document | Purpose | Update Frequency |
|----------|---------|------------------|
| [unvarnished-review.md](unvarnished-review.md) | Brutally honest project assessment | After major milestones |
| [compile-time-contract-implementation.md](compile-time-contract-implementation.md) | Contract system status | With each contract feature |
| [implementation-summary.md](implementation-summary.md) | Overall implementation status | Weekly |
| [scala3-alignment.md](scala3-alignment.md) | Scala 3 readiness status | As ecosystem evolves |

## 🔄 Evidence Lifecycle

```
Plan → Implement → Test → Evidence → Archive (when superseded)
```

1. **Plan created**: Future work documented in `docs/plan/`
2. **Implementation**: Code written, tests pass
3. **Evidence updated**: Document current reality
4. **Plan archived**: Move completed plan to archive
5. **Evidence maintained**: Keep updated as implementation evolves

## ✏️ Creating Evidence Documents

### Template Structure

```markdown
# [Feature Name] - Implementation Evidence

**Status**: ✅ Complete | 🏗️ Partial | ❌ Not Started
**Last Updated**: YYYY-MM-DD
**Coverage**: X%

## What Works

- Feature A: Implemented in `path/to/file.scala:123`
- Feature B: Tests in `path/to/test.scala:45`

## What's Partial

- Feature C: Core logic done, missing edge cases
- Feature D: Works for Spark, Flink support pending

## What's Missing

- Feature E: Planned but not started
- Feature F: Blocked by dependency X

## Test Coverage

- Unit tests: X% (target: Y%)
- Integration tests: X scenarios
- Compile-fail tests: X proofs

## Known Issues

- Issue #123: Description
- Tech debt: Description

## References

- ADR: [ADR-XXX](../adr/XXX-name.md)
- Plan: [Plan](../plan/feature-plan.md)
- Code: `modules/core/src/...`
```

### Best Practices

1. **Be specific**: Include file paths and line numbers
2. **Quantify**: Use metrics (%, counts, times)
3. **Be honest**: Document gaps and limitations
4. **Link code**: Reference actual implementation
5. **Update regularly**: Evidence should never be stale

## 🔍 Verification

Before publishing evidence:

1. **Code exists**: Verify all referenced code is in repo
2. **Tests pass**: Run tests for claimed features
3. **Metrics accurate**: Recompute coverage/performance numbers
4. **Links work**: Check all file paths and URLs
5. **Status current**: Ensure status badges reflect reality

## 🚨 Red Flags

Watch for these signs of aspirational creep:

- ⚠️ "Will be" or "Plans to" language (→ move to plans/)
- ⚠️ Features referenced without file paths
- ⚠️ Claims without test evidence
- ⚠️ Metrics without timestamps
- ⚠️ "Mostly" or "Nearly" complete (be specific!)

## 📊 Evidence vs Plans vs ADRs

| Document Type | Purpose | When | Updates |
|--------------|---------|------|---------|
| **Evidence** | What IS | After implementation | As code changes |
| **Plans** | What WILL BE | Before implementation | Until completed |
| **ADRs** | Why we decided | During decision | Immutable (supersede if needed) |

## 🔗 Related Documentation

- [Plans](../plan/) - What we're building next
- [ADRs](../adr/) - Why we made decisions
- [Canonical Sources](../CANONICAL_SOURCES.md) - Topic authority map
- [Archive](../archive/) - Historical evidence

## 🤝 Contributing

When updating evidence:

1. **Run tests first**: Verify claims before documenting
2. **Use git blame**: Find actual implementation dates
3. **Link issues**: Reference GitHub issues for gaps
4. **Update indexes**: Keep [CANONICAL_SOURCES.md](../CANONICAL_SOURCES.md) current
5. **Be brutally honest**: Truth serves the project better than optimism

---

**Remember**: Evidence documents are our reality check. Keep them accurate, current, and honest.
