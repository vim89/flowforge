# FlowForge Performance Benchmarks

This module provides comprehensive performance testing and monitoring for FlowForge's `SchemaConformsMacros.scala`. It includes compile-time performance analysis, runtime benchmarking, memory profiling, and regression testing to ensure optimal performance of schema validation operations.

## Table of Contents

- [Performance Testing Guide](#performance-testing-guide)
- [CI Performance Integration](#ci-performance-integration)
- [Macro Performance Best Practices](#macro-performance-best-practices)
- [Benchmarking Methodology](#benchmarking-methodology)

## Performance Testing Guide

### Running Performance Benchmarks Locally

#### JMH Runtime Benchmarks

To run all JMH benchmarks:

```bash
sbt "performance-benchmarks/Jmh/run"
```

To run specific benchmark classes:

```bash
# Schema validation benchmarks
sbt "performance-benchmarks/Jmh/run com.flowforge.performance.SchemaValidationBenchmarks"

# Memory profiling benchmarks
sbt "performance-benchmarks/Jmh/run com.flowforge.performance.MemoryProfiler"
```

To run benchmarks with custom parameters:

```bash
# Run with specific iterations and warmup
sbt "performance-benchmarks/Jmh/run -wi 3 -i 5 -f 1"

# Run with profiling enabled
sbt "performance-benchmarks/Jmh/run -prof gc"
```

#### Compile-Time Performance Analysis

To profile macro compilation performance:

```bash
# Generate flamegraphs for macro expansion
sbt perfProfile

# Run compile-time performance tests
sbt "performance-benchmarks/test:testOnly *CompileTimeProfiler*"
```

#### Memory Usage Analysis

To analyze memory usage patterns:

```bash
# Run memory profiling benchmarks
sbt "performance-benchmarks/Jmh/run -prof gc:churn=true"

# Generate memory usage reports
sbt perfReport
```

### Interpreting JMH Benchmark Results

#### Understanding JMH Output

JMH provides several key metrics:

```
Benchmark                                    Mode  Cnt     Score     Error  Units
SchemaValidationBenchmarks.simpleSchema    thrpt    5  1234.567 ± 12.345  ops/s
SchemaValidationBenchmarks.complexSchema   thrpt    5   456.789 ±  4.567  ops/s
```

- **Mode**: `thrpt` (throughput), `avgt` (average time), `ss` (single shot)
- **Cnt**: Number of measurement iterations
- **Score**: Primary metric value
- **Error**: Confidence interval (±)
- **Units**: `ops/s` (operations per second), `ms/op` (milliseconds per operation)

#### Performance Interpretation Guidelines

**Throughput Benchmarks (ops/s)**:
- Higher values indicate better performance
- Compare relative performance between different schema complexities
- Look for performance cliffs where complexity dramatically reduces throughput

**Average Time Benchmarks (ms/op)**:
- Lower values indicate better performance
- Useful for understanding latency characteristics
- Critical for compile-time performance analysis

**Memory Allocation**:
- Monitor allocation rates and GC pressure
- Look for memory leaks in long-running scenarios
- Analyze allocation patterns for optimization opportunities

### Understanding Scalac-Profiling Flamegraphs

#### Flamegraph Interpretation

Scalac-profiling generates flamegraphs showing compilation time distribution:

1. **Width**: Represents time spent in each phase
2. **Height**: Shows call stack depth
3. **Color**: Different phases of compilation
4. **Hover**: Shows exact timing and method information

#### Key Areas to Monitor

**Macro Expansion Phase**:
- Look for `SchemaConformsMacros` in the flamegraph
- Monitor time spent in `Ast.of()` and `Compare.compareType()`
- Identify bottlenecks in type analysis and AST construction

**Type Checking Phase**:
- Monitor reflection overhead
- Look for repeated type analysis
- Identify opportunities for memoization

**Code Generation Phase**:
- Analyze generated code complexity
- Monitor impact of different schema policies
- Look for optimization opportunities

### Performance Optimization Guidelines

#### Compile-Time Optimization

1. **Minimize Reflection Usage**:
   - Cache type information when possible
   - Avoid repeated `dealias` calls
   - Use efficient type symbol lookups

2. **Optimize AST Construction**:
   - Implement memoization for repeated types
   - Use efficient data structures for field mapping
   - Minimize object allocation in hot paths

3. **Early Termination**:
   - Implement fast-path for obvious type mismatches
   - Short-circuit evaluation for incompatible types
   - Optimize comparison order (primitives first)

#### Runtime Optimization

1. **Generated Code Efficiency**:
   - Minimize boxing/unboxing operations
   - Use efficient collection operations
   - Avoid unnecessary intermediate objects

2. **Memory Management**:
   - Monitor GC pressure
   - Optimize allocation patterns
   - Use appropriate collection sizes

## CI Performance Integration

### How Performance Tests Integrate with CI

#### CI Pipeline Integration

Performance tests are integrated into the CI pipeline through the `performance-tests` job:

```yaml
performance-tests:
  runs-on: ubuntu-22.04
  steps:
    - name: Run Performance Benchmarks
      run: sbt perfBench
    - name: Run Regression Tests
      run: sbt perfRegression
    - name: Generate Performance Report
      run: sbt perfReport
```

#### Automated Performance Monitoring

1. **Baseline Comparison**: Each PR is compared against main branch baseline
2. **Regression Detection**: Automatic failure if performance degrades beyond threshold
3. **Artifact Collection**: Performance reports stored as CI artifacts
4. **Historical Tracking**: Long-term performance trend analysis

### Understanding Performance Regression Reports

#### Report Structure

Performance regression reports include:

```
Performance Regression Report
============================

Compile-Time Performance:
- Simple Schema: 125ms → 135ms (+8.0%) ⚠️
- Complex Schema: 450ms → 520ms (+15.6%) ❌
- Large Schema: 1.2s → 1.1s (-8.3%) ✅

Runtime Performance:
- Validation Throughput: 1234 ops/s → 1198 ops/s (-2.9%) ✅
- Memory Allocation: 45MB → 48MB (+6.7%) ⚠️

Status: REGRESSION DETECTED ❌
```

#### Status Indicators

- ✅ **Improvement or acceptable change** (< 5% regression)
- ⚠️ **Warning** (5-10% regression)
- ❌ **Failure** (> 10% regression)

### Updating Performance Baselines

#### When to Update Baselines

Update baselines when:
- Intentional performance improvements are made
- Architecture changes affect performance characteristics
- New optimization techniques are implemented
- Major dependency updates occur

#### How to Update Baselines

```bash
# Update baselines after performance improvements
sbt updateBaselines

# Update specific benchmark baselines
sbt "updateBaselines SchemaValidationBenchmarks"

# Force baseline update (use with caution)
sbt "updateBaselines --force"
```

### Troubleshooting Performance Issues

#### Common Performance Problems

1. **Macro Expansion Slowdown**:
   - Check for increased reflection usage
   - Look for inefficient type analysis
   - Monitor AST construction complexity

2. **Memory Usage Increase**:
   - Analyze allocation patterns
   - Check for memory leaks
   - Monitor GC pressure

3. **Runtime Performance Degradation**:
   - Review generated code efficiency
   - Check for boxing/unboxing overhead
   - Analyze collection usage patterns

#### Debugging Steps

1. **Run Local Profiling**:
   ```bash
   sbt perfProfile
   # Analyze generated flamegraphs
   ```

2. **Compare with Baseline**:
   ```bash
   sbt perfRegression
   # Review detailed comparison report
   ```

3. **Isolate Performance Issues**:
   ```bash
   # Run specific benchmarks
   sbt "performance-benchmarks/Jmh/run SpecificBenchmark"
   ```

## Macro Performance Best Practices

### Guidelines for Schema Design

#### Optimal Schema Patterns

1. **Prefer Flat Structures**:
   ```scala
   // Good: Flat case class
   case class UserProfile(
     id: Long,
     name: String,
     email: String,
     age: Int
   )
   
   // Avoid: Deeply nested structures when possible
   case class NestedUser(
     personal: PersonalInfo,
     contact: ContactInfo,
     preferences: UserPreferences
   )
   ```

2. **Limit Field Count**:
   - Keep case classes under 50 fields when possible
   - Consider splitting large schemas into smaller components
   - Use composition over inheritance for complex schemas

3. **Optimize Collection Usage**:
   ```scala
   // Good: Simple collections
   case class UserData(tags: List[String])
   
   // Careful: Nested collections increase complexity
   case class ComplexData(nested: List[Map[String, Option[List[Int]]]])
   ```

#### Schema Policy Selection

Choose schema policies based on performance requirements:

1. **Exact Policy**: Fastest compilation, strictest validation
2. **Backward Policy**: Good performance, allows field additions
3. **Forward Policy**: Moderate performance, allows field removals
4. **Full Policy**: Slowest compilation, maximum flexibility

### Common Performance Anti-Patterns

#### Anti-Pattern 1: Excessive Nesting

```scala
// Avoid: Deep nesting increases compilation time exponentially
case class Level1(level2: Level2)
case class Level2(level3: Level3)
case class Level3(level4: Level4)
// ... continues for many levels
```

**Solution**: Flatten structures or use composition patterns.

#### Anti-Pattern 2: Large Case Classes

```scala
// Avoid: Case classes with 100+ fields
case class MassiveRecord(
  field1: String, field2: String, /* ... 100+ fields ... */
)
```

**Solution**: Split into logical components or use sealed trait hierarchies.

#### Anti-Pattern 3: Complex Generic Types

```scala
// Avoid: Overly complex generic structures
case class ComplexGeneric[A, B, C](
  data: Map[A, List[Option[Either[B, C]]]]
)
```

**Solution**: Simplify generic structures or use type aliases.

### Memory Usage Optimization Tips

#### Compilation Memory

1. **Increase Compiler Heap Size**:
   ```scala
   // In build.sbt
   scalacOptions ++= Seq("-J-Xmx4g")
   ```

2. **Enable Parallel Compilation**:
   ```scala
   // In build.sbt
   ThisBuild / parallelExecution := true
   ```

3. **Optimize Macro Caching**:
   - Implement efficient memoization
   - Clear caches appropriately
   - Monitor cache hit rates

#### Runtime Memory

1. **Monitor Allocation Patterns**:
   ```bash
   sbt "performance-benchmarks/Jmh/run -prof gc:churn=true"
   ```

2. **Optimize Generated Code**:
   - Minimize intermediate object creation
   - Use efficient collection operations
   - Avoid unnecessary boxing

## Benchmarking Methodology

### Benchmark Design and Methodology

#### JMH Benchmark Configuration

Our benchmarks use the following configuration:

```scala
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
```

#### Benchmark Categories

1. **Schema Complexity Benchmarks**:
   - Simple schemas (< 10 fields, no nesting)
   - Medium schemas (10-50 fields, minimal nesting)
   - Complex schemas (50+ fields, moderate nesting)
   - Extreme schemas (100+ fields, deep nesting)

2. **Policy Comparison Benchmarks**:
   - Compare performance across Exact, Backward, Forward, Full policies
   - Measure compilation time and runtime performance
   - Analyze memory usage patterns

3. **Scaling Benchmarks**:
   - Test performance scaling with schema size
   - Identify performance cliffs and bottlenecks
   - Measure compilation time growth patterns

### Statistical Significance and Measurement Variance

#### Understanding Measurement Variance

JMH provides confidence intervals to account for measurement variance:

```
Score: 1234.567 ± 12.345 ops/s
```

The ± value represents the 99.9% confidence interval.

#### Statistical Significance

Consider results statistically significant when:
- Confidence intervals don't overlap for different measurements
- Performance difference exceeds 2x the confidence interval
- Multiple benchmark runs show consistent trends

#### Reducing Measurement Variance

1. **Stable Environment**:
   - Use dedicated benchmark machines
   - Disable CPU frequency scaling
   - Minimize background processes

2. **Sufficient Iterations**:
   - Use adequate warmup iterations (3-5)
   - Run sufficient measurement iterations (5-10)
   - Consider multiple forks for critical benchmarks

### Performance Testing Environment Requirements

#### Hardware Requirements

**Minimum Requirements**:
- 4 CPU cores
- 8GB RAM
- SSD storage

**Recommended Requirements**:
- 8+ CPU cores
- 16GB+ RAM
- NVMe SSD storage
- Dedicated benchmark machine

#### Software Requirements

**JVM Configuration**:
```bash
export JAVA_OPTS="-Xmx8g -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions"
```

**SBT Configuration**:
```scala
// In build.sbt
ThisBuild / javaOptions ++= Seq(
  "-Xmx8g",
  "-XX:+UseG1GC",
  "-XX:ReservedCodeCacheSize=256m"
)
```

#### Environment Stability

1. **CPU Frequency Scaling**:
   ```bash
   # Disable CPU frequency scaling on Linux
   sudo cpupower frequency-set --governor performance
   ```

2. **Background Processes**:
   - Close unnecessary applications
   - Disable automatic updates
   - Monitor system resource usage

3. **Network Isolation**:
   - Disable network-intensive applications
   - Use local artifact repositories when possible

### Baseline Establishment and Maintenance

#### Initial Baseline Establishment

1. **Clean Environment Setup**:
   ```bash
   # Clean build
   sbt clean
   
   # Run baseline benchmarks
   sbt perfBench
   
   # Establish baselines
   sbt establishBaselines
   ```

2. **Multiple Measurement Runs**:
   - Run benchmarks 3-5 times
   - Calculate statistical averages
   - Identify and exclude outliers

3. **Documentation**:
   - Record hardware specifications
   - Document JVM configuration
   - Note any environmental factors

#### Baseline Maintenance

1. **Regular Updates**:
   - Update baselines monthly or after major changes
   - Maintain historical baseline data
   - Track performance trends over time

2. **Version-Specific Baselines**:
   - Maintain baselines for each major version
   - Support comparison across versions
   - Enable performance regression analysis

3. **Automated Baseline Management**:
   ```bash
   # Automated baseline updates in CI
   if [[ "$GITHUB_REF" == "refs/heads/main" ]]; then
     sbt updateBaselines
   fi
   ```

## Contributing to Performance Testing

### Adding New Benchmarks

1. **Create Benchmark Class**:
   ```scala
   @State(Scope.Benchmark)
   class NewBenchmark {
     @Benchmark
     def benchmarkMethod(): Unit = {
       // Benchmark implementation
     }
   }
   ```

2. **Add to Test Suite**:
   - Include in performance regression tests
   - Update baseline measurements
   - Document benchmark purpose and methodology

3. **Performance Review**:
   - Ensure benchmarks are representative
   - Verify statistical significance
   - Review measurement methodology

### Performance Optimization Contributions

1. **Measure Before Optimizing**:
   - Run baseline benchmarks
   - Identify actual bottlenecks
   - Document current performance characteristics

2. **Validate Improvements**:
   - Run performance regression tests
   - Verify improvements are statistically significant
   - Test across different schema complexities

3. **Update Documentation**:
   - Document optimization techniques
   - Update best practices
   - Share performance insights

For questions or issues with performance testing, please open an issue in the FlowForge repository with the `performance` label.