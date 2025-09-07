# FlowForge Pipeline Template

A [Giter8](http://www.foundweekends.org/giter8/) template for creating FlowForge data engineering pipelines with **100% compile-time contract validation**.

## Quick Start (< 60 seconds)

### 1. Generate Project
```bash
sbt new flowforge/flowforge.g8
# Answer prompts for name, organization, etc.
cd your-pipeline-name
```

### 2. Verify Success 
```bash
sbt compile
# ✅ Should succeed - all contracts align
```

### 3. Simulate Contract Drift
Edit `src/main/scala/Contract.scala` and change:
```scala
id: Long,  // Change this to: id: String,
```

### 4. See Compile-Time Prevention
```bash
sbt compile
# ❌ Should fail with clear error:
# "FlowForge: Contract drift (policy: SchemaPolicy.Exact)
#  Out: UserEvent vs Contract: UserEvent  
#  Mismatched: id expected Long, found String"
```

### 5. Fix and Verify
Revert the change:
```scala
id: Long,  // Back to original
```

```bash
sbt compile  
# ✅ Should succeed again
```

## What You Just Proved

🎯 **FlowForge's Core USP**: "Data pipelines will not even build if source or target schema do not match or align!"

- ✅ **Contract drift caught at compile time** - not runtime
- ✅ **Clear error messages** - know exactly what's wrong
- ✅ **Zero performance impact** - all validation happens during build
- ✅ **Type-safe composition** - impossible to build broken pipelines

## Next Steps

1. **Customize contracts** in `Contract.scala` for your data
2. **Add transformations** in `Pipeline.scala` 
3. **Configure sources/sinks** for your infrastructure
4. **Run with confidence** - if it compiles, contracts are aligned!

## Schema Policies

FlowForge supports 5 schema evolution policies:

- **`Exact`**: Perfect match required (name, type, order)
- **`ExactUnordered`**: Perfect match (name, type) but flexible order
- **`Backward`**: New fields allowed if Optional or have defaults
- **`Forward`**: Missing fields allowed in output
- **`Full`**: Anything goes (escape hatch for development)

## Support

- [FlowForge Documentation](./docs/)
- [Examples](./modules/examples/)  
- [GitHub Issues](https://github.com/flowforge/flowforge/issues)

---
*Generated with FlowForge Giter8 Template | 100% Compile-Time Contracts*