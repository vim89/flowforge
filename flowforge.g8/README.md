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

### 3. Optional: Enable flowforge track and lineage
- Set `flowforgeVersion` in `build.sbt` to a published version (or run `sbt publishLocal` from the flowforge repo and use that SNAPSHOT).
- Uncomment the flowforge `libraryDependencies` in `build.sbt`.
- Replace the placeholder in `src/main/scala/com/flowforge/sample/DemoPipeline.scala` with your pipeline code.

Run the demo:
```bash
sbt run
```

Local lineage (optional):
```bash
docker compose -f ops/marquez/docker-compose.yml up -d
# open http://localhost:3000
```

### 4. Advanced demos
- CSV → Parquet with contracts: see `src/main/scala/com/flowforge/sample/advanced/SparkCsvToParquet.scala` (placeholder; add flowforge deps to enable)
- Optional Deequ mapping: see `src/main/scala/com/flowforge/sample/advanced/DeequDemo.scala` (placeholder; add Deequ + set `-Dff.quality.mode=deequ`)
- Compile‑fail demo: `src/test/scala/com/flowforge/sample/advanced/CompileFailSpec.scala` is a placeholder you can adapt to prove compile‑time drift checks

### 5. GitHub actions CI
This template ships with a starter CI at `.github/workflows/ci.yml` that runs scalafmt checks, compile, test, and package on push/PR.

### Notes
- Template avoids `$` in source files to prevent giter8 escaping problems.

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
*Generated with flowforge Giter8 Template | 100% Compile-Time Contracts*
