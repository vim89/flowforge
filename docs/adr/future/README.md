# Future ADRs - Exploratory Architecture Decisions

This directory contains **exploratory** and **experimental** Architecture Decision Records that are being researched but not yet adopted.

## 🔮 Purpose

Future ADRs serve as:
- **Research documentation**: Capture investigation into new approaches
- **Proposal space**: Explore ideas before committing
- **Learning records**: Document what we learned from experiments
- **Decision pipeline**: Ideas that may become official ADRs

## 🚧 Status

These ADRs are **NOT ACTIVE** decisions. They represent:
- Ongoing research
- Experimental approaches
- Ideas under evaluation
- Potential future directions

## 📋 Current Future ADRs

| ADR | Topic | Status | Notes |
|-----|-------|--------|-------|
| [adr-001-compile-time-contracts-magnolia.md](adr-001-compile-time-contracts-magnolia.md) | Magnolia for compile-time contracts | 🔬 Research | Type derivation exploration |
| [adr-002-non-re-write-pact-with-spark-kafka-flink.md](adr-002-non-re-write-pact-with-spark-kafka-flink.md) | Multi-engine non-rewrite pact | 🔬 Research | Engine abstraction strategy |
| [adr-003-fiber-safe-runtime-defaults.md](adr-003-fiber-safe-runtime-defaults.md) | Fiber-safe runtime defaults | 🔬 Research | Concurrency safety |
| [adr-004-caprese-pure-udfs-non-escaping-capabilities.md](adr-004-caprese-pure-udfs-non-escaping-capabilities.md) | Caprese for pure UDFs | 🔬 Research | Capability safety |
| [adr-005-kyo-effect-fences-with-cats-effect-interop.md](adr-005-kyo-effect-fences-with-cats-effect-interop.md) | Kyo effect fences | 🔬 Research | Effect system experimentation |
| [adr-006-dq-lineage-gx-deequ-openlineage.md](adr-006-dq-lineage-gx-deequ-openlineage.md) | Data quality & lineage integration | 🔬 Research | Observability stack |
| [poc/kyo-caprese-pocs.md](poc/kyo-caprese-pocs.md) | Kyo + Caprese POCs | 🧪 POC | See proof of concept results |
| [poc/kyo-caprese-poc-adr-lld-impl.md](poc/kyo-caprese-poc-adr-lld-impl.md) | Kyo Caprese implementation | 🧪 POC | Low-level design |
| [poc/kyo-gcs-s3-poc-impl.md](poc/kyo-gcs-s3-poc-impl.md) | Kyo with cloud storage | 🧪 POC | Connector research |

## 🎯 Promotion Criteria

A future ADR graduates to main ADRs when:

1. **✅ Research Complete**: Investigation finished with clear findings
2. **✅ Decision Made**: Team agrees to adopt the approach
3. **✅ Evidence Exists**: POC or prototype validates the idea
4. **✅ Benefits Clear**: Advantages outweigh costs and risks
5. **✅ Migration Path**: Know how to transition from current state

## 📝 Adding a Future ADR

1. **Create in future/**: Place exploratory ADRs here
2. **Use `[EXPLORATORY]` status**: Mark as research/experimental
3. **Document findings**: Capture learnings, not just proposals
4. **Link to POCs**: Reference any proof-of-concept code
5. **Update this README**: Add entry to the table above

## 🔄 ADR Lifecycle

```
Idea → Future ADR [Research] → POC [Experiment] → ADR [Adopt] → Evidence [Implement]
                                     ↓
                              Archive [Reject]
```

## 🧪 POC Directory

The `poc/` subdirectory contains:
- Proof-of-concept ADRs
- Implementation experiments
- Low-level design docs for experimental features

## ⚠️ Important Notes

- **Don't reference in production**: These aren't approved decisions
- **Expect changes**: Future ADRs may be modified or rejected
- **Learn from failures**: Rejected ADRs are valuable - document why
- **Time-box research**: Set deadline for decision/rejection

## 🔗 Related

- [Main ADRs](../) - Approved architectural decisions
- [Evidence](../../evidence/) - Current implementation
- [Plans](../../plan/) - Planned work
- [Archive](../../archive/) - Historical context

---

**Status Legend**:
- 🔬 **Research**: Active investigation
- 🧪 **POC**: Proof of concept in progress
- ✅ **Ready**: Ready to promote to main ADRs
- ❌ **Rejected**: Investigated and decided against
- 🔄 **Superseded**: Replaced by better approach
