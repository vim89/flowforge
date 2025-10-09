# flowforge pipeline template

A [Giter8](http://www.foundweekends.org/giter8/) template for creating flowforge data engineering pipelines with **100% compile-time contract validation**.

## Quick start (< 60 seconds)

### 1. Generate Project
```bash
sbt new file:///absolute/path/to/flowforge/flowforge.g8
# Answer prompts for name, organization, etc.
cd your-pipeline-name
```

### 2. Verify success 
```bash
sbt compile
# ✅ Should succeed - all contracts align
```

### 3. Enable flowforge & run the real pipeline
- Set `flowforgeVersion` in `build.sbt` to a published version, or from the flowforge repo run `sbt publishLocal` and use that local SNAPSHOT.
- Run the pipeline:
```bash
sbt "runMain com.flowforge.app.PipelineApp"
```

Local lineage (optional):
```bash
docker compose -f ops/marquez/docker-compose.yml up -d
# open http://localhost:3000
```

### 4. What’s included
- Real runnable Spark pipeline: `com.flowforge.app.PipelineApp` (CSV → DQ → Parquet + JDBC audit)
- DataQuality demo using FlowForge native checks (and Deequ if available): `DataQualitySpec`
- Compile‑fail tests for policies using `assertTypeError`: `PolicyCompileFailSpec`
- JDBC audit logging (H2) to demonstrate effectful side effects around pipeline
- EffectSystem demo: `EffectsDemo` + `EffectsDemoSpec` shows parallelism (parTraverse) and resource safety (bracket)

### 5. GitHub actions CI
This template ships with a starter CI at `.github/workflows/ci.yml`.
- `lint` job always runs (format checks).
- `build` job runs when repo variable `RUN_FULL=true` (set Repo → Settings → Variables). Use this once your flowforge artifacts are resolvable.

### 6. Switch to Deequ (optional)
Set `-Dff.quality.mode=deequ` to use Deequ for DQ; otherwise native Spark checks are used.

## What you just proved

🎯 **flowforge's Core USP**: "Data pipelines will not even build if source or target schema do not match or align!"

- ✅ **Contract drift caught at compile time** - not runtime
- ✅ **Clear error messages** - know exactly what's wrong
- ✅ **Zero performance impact** - all validation happens during build
- ✅ **Type-safe composition** - impossible to build broken pipelines

## Next steps

1. **Customize contracts** in `Contract.scala` for your data
2. **Add transformations** in `Pipeline.scala` 
3. **Configure sources/sinks** for your infrastructure
4. **Run with confidence** - if it compiles, contracts are aligned!

## Schema policies

flowforge supports 5 schema evolution policies:

- **`Exact`**: Perfect match required (name, type, order)
- **`ExactUnordered`**: Perfect match (name, type) but flexible order
- **`Backward`**: New fields allowed if Optional or have defaults
- **`Forward`**: Missing fields allowed in output
- **`Full`**: Anything goes (escape hatch for development)

## Support

- [flowforge Documentation](./docs/)
- [Examples](./modules/examples/)
- [GitHub Issues](https://github.com/flowforge/flowforge/issues)

---
*Generated with flowforge giter8 template | 100% compile-time contracts*
