# FlowForge Data Quality Integration

FlowForge v1.0 uses **native Spark checks as the default** for data quality validation, with optional Deequ integration for advanced use cases.

## Default: Native Spark Checks

FlowForge v1.0 ships with built-in data quality validation using native Spark operations:

- ✅ **Zero dependencies**: No external libraries required
- ✅ **Fast performance**: Leverages Spark's native optimizations  
- ✅ **Core constraints**: NotNull, Unique, Range, Pattern, Custom SQL compliance
- ✅ **Direct integration**: Works seamlessly with FlowForge contracts

**Native checks cover 80%** of production data quality needs without additional complexity.

## Optional: Deequ Enhancement

For advanced data quality scenarios, FlowForge supports **Amazon Deequ** as an optional enhancement:

- 📊 **Statistical profiling**: Data distribution analysis and anomaly detection
- 🏭 **Production-tested**: Battle-hardened at Amazon scale
- 🔄 **Historical comparison**: Constraint suggestion based on data profiling
- 📈 **Rich metadata**: Detailed constraint evaluation insights

**Coordinates**: `com.amazon.deequ:deequ:2.0.11-spark-3.5`

See [`/docs/operating/using-deequ.md`](../operating/using-deequ.md) for setup instructions.

## FlowForge's Unique Value

FlowForge adds the missing piece that neither native Spark nor Deequ provide alone: **compile-time contract validation**.

While Spark and Deequ catch data quality issues at runtime, FlowForge prevents schema drift and contract violations at build time.

---

*Data quality is everyone's responsibility - FlowForge makes it impossible to ignore.*