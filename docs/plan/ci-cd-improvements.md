# CI/CD Improvements & Best Practices - Comprehensive Plan

**Status:** ✅ PHASES 1-5 COMPLETE | 📋 ADVANCED PRACTICES ROADMAP
**Date:** 2025-10-05
**Last Updated:** 2025-10-05

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Implementation Status](#implementation-status)
3. [Advanced Best Practices (Must-Have)](#advanced-best-practices-must-have)
4. [Advanced Best Practices (Should-Have)](#advanced-best-practices-should-have)
5. [Scala/SBT Specific Optimizations](#scalasbt-specific-optimizations)
6. [Apache Spark Testing Strategy](#apache-spark-testing-strategy)
7. [Data Engineering Practices](#data-engineering-practices)
8. [Performance & Benchmarking](#performance--benchmarking)
9. [Security & Compliance](#security--compliance)
10. [Release Engineering](#release-engineering)
11. [Implementation Roadmap](#implementation-roadmap)

---

## Executive Summary

FlowForge has successfully completed **Phases 1-5** of the original CI/CD overhaul, achieving:
- ✅ 100% module coverage (17/17 modules tested)
- ✅ Maven Central + Docker + GitHub Releases distribution
- ✅ Security scans on every PR
- ✅ Zero duplicate test runs (coverage reuses CI artifacts)
- ✅ ~60% reduction in unnecessary workflow executions

This document outlines **advanced best practices** for OSS Scala/Spark data engineering projects based on industry research (2024-2025).

---

## Implementation Status

### ✅ Phase 1: Critical Fixes (COMPLETE)
- [x] Fix g8 template /tmp usage → `${{ runner.temp }}`
- [x] Make CODECOV_TOKEN optional
- [x] Fix nightly cross-Scala builds (removed 2.12/3.3)
- [x] Add dependency-check conditional
- [x] Add 8 missing module tests (100% coverage: 17/17)
- [x] Fix job dependencies (quality → build → integration)

### ✅ Phase 2: Functional Completeness (COMPLETE)
- [x] Maven Central publish (sbt publishSigned sonatypeBundleRelease)
- [x] Docker images (3 CLIs: validation, contracts-extractor, maintenance)
- [x] Build maintenanceCli JAR
- [x] Generate checksums (SHA256/SHA512)
- [x] GPG artifact signing
- [x] SBOM generation (anchore/sbom-action)

### ✅ Phase 3: Non-Functional Requirements (COMPLETE)
- [x] **Phase 3.1**: Eliminate duplicate tests (coverage reuses CI artifacts)
- [x] **Phase 3.2**: Security scans on every PR (CodeQL, TruffleHog, Semgrep)
- [x] **Phase 3.3**: Integration test retry logic (nick-fields/retry-action@v3)
- [x] **Phase 3.4**: Deployment verification

### ✅ Phase 4: Missing Workflows (COMPLETE)
- [x] PR validation workflow (conventional commits, breaking change detection)
- [x] Dependabot config (GitHub Actions updates)
- [x] Stale PR/issue management
- [x] Changelog generation (git-cliff with conventional commits)

### ✅ Phase 5: Workflow Optimization (COMPLETE)
- [x] Path filters (CI runs only on code changes, docs-lint only on docs)
- [x] Explicit timeouts on all jobs (5-45min)
- [x] Remove redundant security.yml weekly schedule
- [x] ~60% reduction in workflow executions

---

## Advanced Best Practices (Must-Have)

### 1. **Semantic Versioning & Automated Releases** 🎯 HIGH PRIORITY

**Current State**: Manual release.yml workflow
**Industry Standard**: Automated semantic-release based on conventional commits

#### Recommendation: Integrate semantic-release

**Benefits**:
- Automatically determines next semantic version from commits
- Generates changelogs from conventional commits
- Publishes releases to Maven Central + GitHub
- Zero human error in versioning

**Implementation**:

```yaml
# .github/workflows/semantic-release.yml
name: Semantic Release

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  release:
    runs-on: ubuntu-22.04
    timeout-minutes: 30
    permissions:
      contents: write
      issues: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0
          persist-credentials: false

      - uses: cycjimmy/semantic-release-action@v4
        with:
          semantic_version: 23
          branches: |
            [
              'main',
              {
                name: 'beta',
                prerelease: true
              }
            ]
          plugins: |
            [
              "@semantic-release/commit-analyzer",
              "@semantic-release/release-notes-generator",
              "@semantic-release/changelog",
              "@semantic-release/github",
              "@semantic-release/git"
            ]
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
```

**Required**:
- Install semantic-release
- Configure `.releaserc.json`
- Enforce conventional commits (already have pr-validation.yml)
- Update version in build.sbt automatically

**Migration Path**:
1. Add semantic-release alongside existing release.yml
2. Test on beta branch
3. Migrate main branch after validation
4. Deprecate manual release.yml

**References**:
- https://github.com/semantic-release/semantic-release
- https://semver.org/
- https://www.conventionalcommits.org/

---

### 2. **sbt-github-actions Plugin** 🔧 HIGH PRIORITY

**Current State**: Manually maintained workflow YAMLs
**Industry Standard**: Generate workflows from build.sbt

#### Recommendation: Use sbt-github-actions

**Benefits**:
- Workflows generated from `crossScalaVersions`
- Automatic matrix builds for Scala versions
- Built-in Coursier caching
- Reduces YAML maintenance burden

**Implementation**:

```scala
// project/plugins.sbt
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.23.0")

// build.sbt
ThisBuild / githubWorkflowBuildPreamble := Seq(
  WorkflowStep.Sbt(
    List("scalafmtCheckAll"),
    name = Some("Check formatting")
  )
)

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    List("coverage", "test", "coverageReport"),
    name = Some("Run tests with coverage")
  )
)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    name = Some("Publish artifacts"),
    env = Map(
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}"
    )
  )
)

ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.temurin("17"),
  JavaSpec.temurin("21")
)
```

**Generate workflows**:
```bash
sbt githubWorkflowGenerate
```

**Migration Path**:
1. Install sbt-github-actions
2. Configure in build.sbt
3. Generate workflows and compare with existing
4. Migrate incrementally (CI first, then release)

**References**:
- https://github.com/sbt/sbt-github-actions
- https://www.scala-sbt.org/1.x/docs/GitHub-Actions-with-sbt.html

---

### 3. **Coverage Thresholds & Quality Gates** 📊 HIGH PRIORITY

**Current State**: Coverage reports to Codecov, no enforcement
**Industry Standard**: 70-90% minimum coverage with PR status checks

#### Recommendation: Enforce coverage thresholds

**Implementation**:

```scala
// build.sbt
ThisBuild / coverageMinimumStmtTotal := 75
ThisBuild / coverageMinimumBranchTotal := 70
ThisBuild / coverageFailOnMinimum := true

// Per-module thresholds
lazy val core = project
  .settings(
    coverageMinimumStmtTotal := 90,  // Core must have 90%
    coverageMinimumBranchTotal := 85
  )

lazy val examples = project
  .settings(
    coverageEnabled := false  // Exclude examples from coverage
  )
```

**Codecov Configuration** (`codecov.yml`):

```yaml
coverage:
  status:
    project:
      default:
        target: 75%
        threshold: 2%  # Allow 2% drop
    patch:
      default:
        target: 80%  # New code must be 80% covered

flags:
  core:
    paths:
      - modules/core/
    target: 90%
  contracts:
    paths:
      - modules/contracts/
    target: 85%
```

**CI Integration**:

```yaml
# .github/workflows/coverage.yml
- name: Check coverage thresholds
  run: |
    sbt clean coverage test coverageReport
    sbt coverageAggregate

    # Fail if below threshold
    COVERAGE=$(grep -oP 'Total coverage: \K[0-9.]+' target/scala-*/scoverage-report/index.html || echo "0")
    if (( $(echo "$COVERAGE < 75" | bc -l) )); then
      echo "::error::Coverage $COVERAGE% is below threshold 75%"
      exit 1
    fi
```

**Industry Benchmarks**:
- Microsoft, Google, Facebook: 70-80% minimum
- High reliability domains: 90%+ average
- Data engineering: 75-85% recommended

**References**:
- https://github.com/scoverage/sbt-scoverage
- https://docs.codecov.com/docs/commit-status

---

### 4. **Contract Testing & Schema Validation** 🔒 CRITICAL FOR DATA ENGINEERING

**Current State**: Compile-time contracts (excellent!)
**Enhancement**: Add runtime contract validation + monitoring

#### Recommendation: Schema registry integration

**Why**: FlowForge's compile-time contracts are already excellent. Enhance with:

1. **Schema Registry** (Confluent/AWS Glue):
   ```scala
   // Publish schemas on release
   lazy val publishSchemas = taskKey[Unit]("Publish schemas to registry")
   publishSchemas := {
     val schemas = (Compile / resourceDirectory).value / "schemas"
     // Upload avsc/proto files to registry
   }
   ```

2. **Runtime Validation** (production monitoring):
   ```scala
   // Add to data pipelines
   object ContractMonitoring {
     def validateAtRuntime[T: SchemaDerivation](df: DataFrame): Either[ValidationError, DataFrame] = {
       val schema = SchemaDerivation[T].schema
       val violations = df.schema.diff(schema)
       if (violations.isEmpty) Right(df)
       else Left(ValidationError(violations))
     }
   }
   ```

3. **Integration Tests with Contract Violations**:
   ```scala
   test("Pipeline rejects invalid schemas") {
     val invalidDf = spark.read.parquet("test-data/invalid-schema.parquet")

     intercept[ContractViolation] {
       pipeline.run(invalidDf)
     }
   }
   ```

**CI Integration**:

```yaml
# .github/workflows/contract-validation.yml
- name: Validate contracts against registry
  run: |
    sbt contracts/test
    sbt publishSchemas  # Dry-run validation
```

**References**:
- https://docs.confluent.io/platform/current/schema-registry/index.html
- https://www.getdbt.com/blog/building-a-data-quality-framework-with-dbt-and-dbt-cloud

---

### 5. **Multi-Stage Docker Builds** 🐳 MEDIUM PRIORITY

**Current State**: Basic Dockerfiles
**Industry Standard**: Multi-stage builds with JVM optimization

#### Recommendation: Optimize Docker images

**Implementation**:

```dockerfile
# Dockerfile.validation-cli (multi-stage)
# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.9_9_1.9.7_2.13.12 AS builder

WORKDIR /build
COPY . .
RUN sbt "validationCli/assembly"

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy AS runtime

# JVM optimization
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom"

# Create non-root user
RUN groupadd -r flowforge && useradd -r -g flowforge flowforge

WORKDIR /app
COPY --from=builder /build/modules/validation-cli/target/scala-*/validation-cli-assembly-*.jar app.jar

# Use non-root
USER flowforge

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Benefits**:
- Image size reduction: 518MB → 128MB (typical)
- Container-aware JVM (UseContainerSupport)
- Security: non-root user
- Build cache optimization

**CI Integration**:

```yaml
# .github/workflows/release.yml
- name: Build Docker images
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ./Dockerfile.validation-cli
    platforms: linux/amd64,linux/arm64
    cache-from: type=gha
    cache-to: type=gha,mode=max
    tags: |
      ghcr.io/${{ github.repository }}/validation-cli:${{ needs.validate-release.outputs.version }}
      ghcr.io/${{ github.repository }}/validation-cli:latest
```

**References**:
- https://docs.docker.com/build/building/multi-stage/
- https://snyk.io/blog/best-practices-to-build-java-containers-with-docker/

---

## Advanced Best Practices (Should-Have)

### 6. **Performance Benchmarking with JMH** 📈 MEDIUM PRIORITY

**Current State**: Nightly benchmarks (basic)
**Enhancement**: JMH benchmarks with regression detection

#### Recommendation: sbt-jmh + GitHub Actions integration

**Implementation**:

```scala
// project/plugins.sbt
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")

// modules/core/build.sbt
enablePlugins(JmhPlugin)

// modules/core/src/main/scala/benchmarks/SchemaDerivationBenchmark.scala
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class SchemaDerivationBenchmark {

  @Benchmark
  def deriveSchema(): Schema = {
    SchemaDerivation[LargeContract].derive()
  }
}
```

**CI Integration with Regression Detection**:

```yaml
# .github/workflows/benchmarks.yml
name: Performance Benchmarks

on:
  pull_request:
    paths:
      - 'modules/core/**'
      - 'modules/contracts/**'
  push:
    branches: [main]

jobs:
  benchmark:
    runs-on: ubuntu-22.04
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v5

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - name: Run benchmarks
        run: sbt "core/Jmh/run -rf json -rff benchmark-results.json"

      - name: Download baseline (main branch)
        if: github.event_name == 'pull_request'
        uses: actions/download-artifact@v4
        with:
          name: benchmark-baseline
          path: baseline/

      - name: Compare with baseline
        if: github.event_name == 'pull_request'
        uses: kitlangton/jmh-benchmark-action@v1
        with:
          current: benchmark-results.json
          baseline: baseline/benchmark-results.json
          threshold: 10  # Fail if >10% regression

      - name: Upload baseline (main branch)
        if: github.event_name == 'push' && github.ref == 'refs/heads/main'
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-baseline
          path: benchmark-results.json
          retention-days: 30
```

**References**:
- https://github.com/sbt/sbt-jmh
- https://github.com/kitlangton/jmh-benchmark-action
- https://openjdk.org/projects/code-tools/jmh/

---

### 7. **Data Quality Testing Framework** 🎯 HIGH PRIORITY FOR DATA ENGINEERING

**Current State**: Quality-deequ module exists
**Enhancement**: Comprehensive data quality CI/CD

#### Recommendation: Integrate dbt-expectations pattern for Spark

**Implementation**:

```scala
// modules/quality/src/main/scala/DataQualityChecks.scala
trait DataQualityCheck {
  def validate(df: DataFrame): Either[QualityViolation, DataFrame]
}

object DataQualityChecks {
  // Uniqueness
  def expectUniqueValues(column: String): DataQualityCheck = ???

  // Completeness
  def expectNoNulls(column: String): DataQualityCheck = ???

  // Timeliness
  def expectRecentData(column: String, withinHours: Int): DataQualityCheck = ???

  // Validity
  def expectValuesInRange(column: String, min: Double, max: Double): DataQualityCheck = ???

  // Consistency
  def expectReferentialIntegrity(fkColumn: String, pkTable: String, pkColumn: String): DataQualityCheck = ???
}

// Usage in pipelines
val qualityChecks = Seq(
  DataQualityChecks.expectUniqueValues("id"),
  DataQualityChecks.expectNoNulls("email"),
  DataQualityChecks.expectRecentData("created_at", withinHours = 24),
  DataQualityChecks.expectValuesInRange("age", 0, 150)
)

val validatedDf = qualityChecks.foldLeft(Right(df): Either[QualityViolation, DataFrame]) {
  case (Right(currentDf), check) => check.validate(currentDf)
  case (left @ Left(_), _) => left
}
```

**CI Integration**:

```yaml
# .github/workflows/data-quality.yml
- name: Run data quality tests
  run: |
    sbt "quality/test"

    # Generate quality report
    sbt "quality/run --generate-report"

    # Upload to artifact
    - uses: actions/upload-artifact@v4
      with:
        name: data-quality-report
        path: target/quality-report.html
```

**References**:
- https://github.com/awslabs/deequ
- https://www.datafold.com/blog/dbt-expectations

---

### 8. **Spark Testing Best Practices** ⚡ CRITICAL FOR SPARK PROJECTS

**Current State**: Basic Spark integration tests
**Enhancement**: Comprehensive Spark testing strategy

#### Recommendation: 3-tier testing approach

**1. Unit Tests (Fast, Isolated)**:

```scala
// modules/engines-spark/src/test/scala/SparkUnitTest.scala
trait SparkUnitTest extends AnyFunSuite with BeforeAndAfterAll {

  lazy val spark: SparkSession = SparkSession.builder()
    .appName("unit-test")
    .master("local[2]")
    .config("spark.sql.shuffle.partitions", "2")
    .config("spark.ui.enabled", "false")
    .getOrCreate()

  override def afterAll(): Unit = {
    spark.stop()
    super.afterAll()
  }
}

class UserTransformationTest extends SparkUnitTest {
  test("transform should filter inactive users") {
    import spark.implicits._

    val input = Seq(
      User("1", "active"),
      User("2", "inactive")
    ).toDF()

    val result = UserTransformation.transform(input)

    assert(result.count() == 1)
    assert(result.first().status == "active")
  }
}
```

**2. Integration Tests (Real Spark, Real Data)**:

```scala
// Use Testcontainers for external dependencies
class SparkJdbcIntegrationTest extends SparkUnitTest {

  val postgresContainer = PostgreSQLContainer()

  override def beforeAll(): Unit = {
    super.beforeAll()
    postgresContainer.start()
  }

  test("read from postgres, transform, write to parquet") {
    val jdbcDf = spark.read
      .format("jdbc")
      .option("url", postgresContainer.jdbcUrl)
      .option("dbtable", "users")
      .load()

    val transformed = UserTransformation.transform(jdbcDf)

    transformed.write.parquet("target/test-output/users.parquet")

    val result = spark.read.parquet("target/test-output/users.parquet")
    assert(result.count() > 0)
  }
}
```

**3. End-to-End Tests (Production-like)**:

```scala
class SparkE2ETest extends SparkUnitTest {

  test("full pipeline: ingest → transform → aggregate → write") {
    val pipeline = Pipeline.builder()
      .source(JdbcSource(...))
      .transform(UserTransformation)
      .transform(AggregationTransformation)
      .sink(ParquetSink(...))
      .build()

    pipeline.run()

    // Validate output
    val output = spark.read.parquet("target/e2e-output/")
    assert(output.schema == expectedSchema)
    assert(output.count() == expectedCount)
  }
}
```

**CI Configuration**:

```yaml
# .github/workflows/ci.yml
spark-unit-tests:
  name: Spark Unit Tests
  runs-on: ubuntu-22.04
  timeout-minutes: 15
  steps:
    - run: sbt "enginesSpark/test"

spark-integration-tests:
  name: Spark Integration Tests
  runs-on: ubuntu-22.04
  timeout-minutes: 30
  if: github.ref == 'refs/heads/main' || contains(github.event.pull_request.labels.*.name, 'run-integration-tests')
  services:
    postgres:
      image: postgres:15
      env:
        POSTGRES_PASSWORD: test
  steps:
    - run: sbt -DwithSparkIT=true "enginesSpark/testOnly *IntegrationTest"
```

**References**:
- https://spark.apache.org/developer-tools.html
- https://eugene-lopatkin.medium.com/apache-spark-integration-testing-32d9aa9860be

---

## Scala/SBT Specific Optimizations

### 9. **sbt Build Performance** 🚀

**Current State**: Standard sbt configuration
**Enhancement**: Optimize build times

**Implementation**:

```scala
// build.sbt
ThisBuild / turbo := true  // Enable turbo mode (sbt 1.4+)
ThisBuild / usePipelining := true  // Pipelined compilation

// Parallel execution
Global / concurrentRestrictions := Seq(
  Tags.limitAll(Math.max(1, Runtime.getRuntime.availableProcessors() - 1))
)

// Dependency resolution cache
ThisBuild / updateOptions := updateOptions.value.withCachedResolution(true)

// Scala compiler options for faster compilation
ThisBuild / scalacOptions ++= Seq(
  "-Xmaxerrs", "5",  // Fail fast
  "-Xmaxwarns", "5"
)
```

**CI Caching Strategy**:

```yaml
# .github/workflows/ci.yml
- name: Cache SBT and Coursier
  uses: actions/cache@v4
  with:
    path: |
      ~/.sbt
      ~/.ivy2/cache
      ~/.cache/coursier
      target
      project/target
    key: ${{ runner.os }}-sbt-${{ hashFiles('**/build.sbt', '**/project/**/*.scala', '**/project/build.properties') }}
    restore-keys: |
      ${{ runner.os }}-sbt-
```

---

### 10. **Cross-Building Strategy** 🔀

**Current State**: Single Scala version (2.13)
**Enhancement**: Support Scala 2.13 + 3.x

**Implementation**:

```scala
// build.sbt
ThisBuild / crossScalaVersions := Seq("2.13.12", "3.3.1")

// Use sbt-projectmatrix for parallel cross-building
lazy val coreJVM213 = core.jvm("2.13.12")
lazy val coreJVM3 = core.jvm("3.3.1")

// Conditional compilation for Scala 3
lazy val core = crossProject(JVMPlatform)
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) =>
          Seq("org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0")
        case Some((3, _)) =>
          Seq()  // Scala 3 has built-in compatibility
      }
    }
  )
```

**CI Matrix**:

```yaml
# .github/workflows/ci.yml
build:
  strategy:
    matrix:
      scala: ['2.13.12', '3.3.1']
      java: ['17', '21']
  steps:
    - run: sbt "++${{ matrix.scala }} test"
```

**References**:
- https://www.scala-sbt.org/1.x/docs/Cross-Build.html
- https://github.com/sbt/sbt-projectmatrix

---

## Security & Compliance

### 11. **Dependency Scanning Strategy** 🔐

**Current State**: Dependabot (Actions only) + manual dependency-check
**Enhancement**: Multi-tool approach

#### Recommendation: Dependabot + Renovate + Snyk

**Why multi-tool**:
- **Dependabot**: Free, GitHub-native, basic vulnerability alerts
- **Renovate**: Advanced SBT support, auto-merge minor updates, grouping
- **Snyk**: Deep vulnerability intelligence, license compliance, container scanning

**Implementation**:

**1. Keep Dependabot** (`.github/dependabot.yml`):
```yaml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    groups:
      gh-actions:
        patterns: ["*"]
```

**2. Add Renovate** (`.github/renovate.json`):
```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": [
    "config:recommended",
    ":dependencyDashboard",
    ":semanticCommits",
    ":preserveSemverRanges"
  ],
  "schedule": ["before 6am on monday"],
  "packageRules": [
    {
      "groupName": "Scala core",
      "matchPackagePatterns": ["^org\\.scala-lang"],
      "schedule": ["before 6am on the first day of the month"]
    },
    {
      "groupName": "Apache Spark",
      "matchPackagePatterns": ["^org\\.apache\\.spark"],
      "schedule": ["before 6am on the first day of the month"]
    },
    {
      "matchUpdateTypes": ["patch"],
      "automerge": true
    },
    {
      "matchManagers": ["github-actions"],
      "pinDigests": true
    }
  ],
  "sbt": {
    "fileMatch": ["(^|/)build\\.sbt$", "(^|/)project/.*\\.scala$"]
  }
}
```

**3. Add Snyk** (for deep vulnerability scanning):

```yaml
# .github/workflows/security.yml
- name: Run Snyk to check for vulnerabilities
  uses: snyk/actions/scala@master
  continue-on-error: true
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
  with:
    args: --all-projects --severity-threshold=high
```

**References**:
- https://codepad.co/blog/renovate-vs-dependabot-dependency-and-vulnerability-management/
- https://www.aikido.dev/blog/top-open-source-dependency-scanners

---

### 12. **SLSA Build Provenance** 📜 ADVANCED

**Current State**: Basic artifact signing
**Enhancement**: SLSA Level 3 provenance

**Why**: Supply chain security, verifiable builds

**Implementation**:

```yaml
# .github/workflows/release.yml
- name: Generate SLSA provenance
  uses: slsa-framework/slsa-github-generator/.github/workflows/generator_generic_slsa3.yml@v1.9.0
  with:
    base64-subjects: "${{ needs.build.outputs.hashes }}"
    provenance-name: "flowforge.intoto.jsonl"
```

**References**:
- https://slsa.dev/
- https://github.com/slsa-framework/slsa-github-generator

---

## Implementation Roadmap

### Phase 6: Automation & Developer Experience (2-3 weeks)

**Must-Have**:
- [ ] Integrate `sbt-github-actions` for workflow generation
- [ ] Add semantic-release for automated versioning
- [ ] Enforce coverage thresholds (75% minimum)
- [ ] Add multi-stage Docker builds

**Should-Have**:
- [ ] Migrate to Renovate (keep Dependabot for Actions)
- [ ] Add contract validation tests
- [ ] Improve sbt build performance (turbo mode, caching)

### Phase 7: Testing & Quality (2-3 weeks)

**Must-Have**:
- [ ] Comprehensive Spark testing strategy (unit/integration/e2e)
- [ ] Data quality framework integration
- [ ] JMH benchmarks with regression detection

**Should-Have**:
- [ ] Cross-building for Scala 2.13 + 3.x
- [ ] Testcontainers for integration tests
- [ ] Performance regression CI gates

### Phase 8: Advanced Security & Compliance (1-2 weeks)

**Must-Have**:
- [ ] Snyk integration for deep vulnerability scanning
- [ ] License compliance checks
- [ ] Container security scanning

**Should-Have**:
- [ ] SLSA provenance generation
- [ ] SBOM distribution (already have generation)
- [ ] Dependency vulnerability dashboard

### Phase 9: Release Engineering (1 week)

**Must-Have**:
- [ ] Semantic-release migration
- [ ] Automated changelog generation (already have git-cliff)
- [ ] Release artifact verification

**Should-Have**:
- [ ] Multi-platform Docker images (amd64 + arm64)
- [ ] Maven Central migration to Central Portal (new endpoint)
- [ ] Release notes automation

---

## Success Metrics

### Current (After Phase 5)
- ✅ 100% module coverage
- ✅ ~60% reduction in workflow executions
- ✅ Security scans on every PR
- ✅ Zero duplicate test runs

### Target (After Phases 6-9)

**Build Performance**:
- Average CI time: <12min (current: ~15min)
- Test execution: <8min (current: ~10min)
- Docker build: <5min (current: ~8min)

**Quality**:
- Code coverage: 75%+ enforced (current: reported but not enforced)
- Data quality tests: 100% of production datasets
- Performance regression: <5% tolerance

**Security**:
- Vulnerability fix time: <7 days (current: manual)
- License compliance: 100% validated
- SLSA provenance: Level 3

**Developer Experience**:
- PR feedback time: <10min (quality checks)
- Release frequency: Weekly (current: monthly)
- Automated releases: 100% (current: manual)

---

## References & Resources

### Official Documentation
- [SBT GitHub Actions](https://www.scala-sbt.org/1.x/docs/GitHub-Actions-with-sbt.html)
- [Scoverage Documentation](https://github.com/scoverage/scalac-scoverage-plugin)
- [Apache Spark Testing](https://spark.apache.org/developer-tools.html)
- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)

### Best Practices
- [GitHub Actions Best Practices 2025](https://suzuki-shunsuke.github.io/slides/github-actions-best-practice-2025)
- [Spark Testing Best Practices](https://dev.to/adevintaspain/spark-unit-integration-and-end-to-end-tests-f52)
- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [dbt Data Quality](https://www.getdbt.com/blog/building-a-data-quality-framework-with-dbt-and-dbt-cloud)

### Tools & Plugins
- [sbt-github-actions](https://github.com/sbt/sbt-github-actions)
- [sbt-jmh](https://github.com/sbt/sbt-jmh)
- [semantic-release](https://github.com/semantic-release/semantic-release)
- [Renovate](https://github.com/renovatebot/renovate)
- [Snyk](https://snyk.io/)

---

**Last Updated**: 2025-10-05
**Maintainer**: FlowForge Core Team
**Status**: Living Document - Updated as new best practices emerge
