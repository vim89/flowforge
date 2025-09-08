# FlowForge Quality-Deequ Module

## Overview

The `quality-deequ` module provides data quality validation with dual-mode support:

1. **Native Spark checks** (default) - No extra dependencies required
2. **Amazon Deequ VerificationSuite** (optional) - When Deequ is available on classpath

## Architecture

```scala
DeequAdapter.runChecks(spark, dataset, constraints)
```

The adapter automatically:
- Detects if Deequ is available using reflection
- Falls back to native Spark implementations when Deequ is not available
- Provides consistent QualityResult interface regardless of backend

## Usage Modes

### Mode 1: Native Spark (Default)

```scala
// No extra dependencies required
val result = DeequAdapter.runChecks(spark, dataset, constraints)
```

Native mode provides:
- ✅ Full constraint support (NotNull, Unique, Range, Pattern, Compliance)
- ✅ Accurate violation counts and affected records
- ✅ Zero external dependencies
- ✅ Scala 2.13 compatibility

### Mode 2: Deequ Enhancement (Optional)

To enable Deequ mode, add Deequ to your runtime classpath and set system property:

```bash
# Add Deequ dependency at runtime
sbt "set libraryDependencies += \"com.amazon.deequ\" % \"deequ\" % \"2.0.11-spark-3.5\""

# Enable Deequ mode
-Dff.quality.mode=deequ
```

Deequ mode provides:
- ✅ Industry-standard data quality framework
- ⚠️ Limited to Scala 2.12 (Deequ constraint)
- ⚠️ Fallback to native mode on any Deequ errors

## Constraint Support

All FlowForge quality constraints are supported in both modes:

| Constraint | Native Spark | Deequ |
|-----------|-------------|-------|
| `NotNull` | ✅ | ✅ |
| `Unique` | ✅ | ✅ |
| `Range` | ✅ | ✅ |
| `Pattern` | ✅ | ✅ |
| `Compliance` | ✅ | ✅ |

## Implementation Details

### Native Spark Implementation

Uses Spark DataFrame operations for validation:
- `NotNull`: `df.filter(col(field).isNull).count()`
- `Unique`: Compare total vs distinct counts
- `Range`: `value.geq(min) && value.leq(max)`
- `Pattern`: `col.rlike(regex)`
- `Compliance`: `df.filter(not(expr(predicate)))`

### Deequ Integration

Uses reflection to call Deequ VerificationSuite when available:
- Dynamic class loading: `Class.forName("com.amazon.deequ.VerificationSuite")`
- Reflection-based Check building
- Graceful fallback to native mode on any reflection failures

## Error Handling

The module follows CLAUDE.md functional programming principles:
- Uses `Either[Throwable, Result]` for error handling
- No try-catch-finally blocks
- Automatic fallback from Deequ to native mode
- Consistent QualityResult interface

## Performance

### Native Mode
- Direct Spark DataFrame operations
- Minimal overhead
- Parallel execution via Spark's query planner

### Deequ Mode
- Reflection overhead during initialization
- Deequ's optimized constraint evaluation
- Automatic caching and optimization

## Migration Path

This dual-mode design enables:
1. **Immediate adoption**: Start with native mode (zero dependencies)
2. **Gradual enhancement**: Add Deequ when needed for advanced features
3. **Production safety**: Automatic fallback prevents runtime failures
4. **Scala compatibility**: Native mode works with any Scala version

## Examples

See `ContractToDeltaExample.scala` for complete usage examples with Delta Lake integration.