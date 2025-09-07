# FlowForge Data Quality Integration

FlowForge takes a **composition over invention** approach to data quality - integrating with proven libraries like Deequ rather than building from scratch.

## Philosophy: Compose, Don't Re-invent

**Why not build our own DQ engine?**
- Engines + proven libraries enforce at runtime better than custom solutions
- Deequ (Amazon) has years of production hardening at scale  
- Delta/Iceberg constraints provide table-level enforcement
- FlowForge adds the missing piece: **compile-time contract validation**

## Integration with FlowForge Contracts

See `modules/quality-deequ/src/main/scala/com/flowforge/quality/deequ/ContractToDeltaExample.scala` for a complete implementation example showing contract → Deequ → Delta constraint mapping.

---

*Data quality is everyone's responsibility - FlowForge makes it impossible to ignore.*