# Coverage Enforcement Strategy - Flag-Based Approach

**Status**: 🟡 Development Mode (Report Only)
**Target**: 🟢 Production Mode (Enforcement Active) for v1.0.0
**Last Updated**: 2025-10-05

---

## Table of Contents

1. [Overview](#overview)
2. [Current State (Development Mode)](#current-state-development-mode)
3. [Flag-Based Enforcement](#flag-based-enforcement)
4. [Coverage Thresholds](#coverage-thresholds)
5. [Activation Instructions](#activation-instructions)
6. [Testing Locally](#testing-locally)
7. [CI/CD Integration](#cicd-integration)
8. [Codecov Integration](#codecov-integration)

---

## Overview

FlowForge uses a **two-phase coverage enforcement strategy**:

1. **Phase 1 (Current - Development)**: Coverage is measured and reported, but **does not fail the build**
2. **Phase 2 (v1.0+)**: Coverage enforcement is **activated**, failing builds that don't meet thresholds

This approach allows us to:
- ✅ Track coverage trends during development
- ✅ Identify low-coverage modules early
- ✅ Improve coverage incrementally without blocking development
- ✅ Enforce strict quality gates when production-ready

---

## Current State (Development Mode)

### SBT (build.sbt)

```scala
// Coverage thresholds are DEFINED but NOT ENFORCED
val enforceCoverageThreshold = sys.env.get("SCOVERAGE_ENFORCE_THRESHOLD").contains("true")

ThisBuild / coverageMinimumStmtTotal := 75
ThisBuild / coverageMinimumBranchTotal := 70
ThisBuild / coverageFailOnMinimum := enforceCoverageThreshold  // ❌ Currently false
```

**Result**: Running `sbt coverage test coverageReport` will:
- ✅ Generate coverage reports
- ✅ Show coverage percentages
- ❌ NOT fail the build if coverage is below threshold

### Codecov (codecov.yml)

```yaml
# All status checks are informational=true (won't block PRs)
coverage:
  status:
    patch:
      default:
        informational: true  # ❌ Report only
    project:
      core:
        informational: true  # ❌ Report only
```

**Result**: Pull requests will:
- ✅ Show coverage reports in comments
- ✅ Display per-module coverage
- ✅ Show coverage trends (increasing/decreasing)
- ❌ NOT block merge if coverage is below threshold

---

## Flag-Based Enforcement

### Environment Variable

**Variable**: `SCOVERAGE_ENFORCE_THRESHOLD`
**Values**: `true` | `false` (default)

### How It Works

```scala
// build.sbt
val enforceCoverageThreshold = sys.env.get("SCOVERAGE_ENFORCE_THRESHOLD").contains("true")

ThisBuild / coverageFailOnMinimum := enforceCoverageThreshold
```

**When `SCOVERAGE_ENFORCE_THRESHOLD=true`**:
- ✅ Build FAILS if coverage < threshold
- ✅ Per-module thresholds enforced (core 90%, contracts 90%, others 75%)
- ✅ Branch coverage enforced (70%+)

**When `SCOVERAGE_ENFORCE_THRESHOLD` is unset or false** (default):
- ✅ Coverage is measured and reported
- ❌ Build does NOT fail on low coverage
- ✅ Development can continue unblocked

---

## Coverage Thresholds

### Global Thresholds (All Modules)

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| Statement Coverage | 75% | Industry baseline (Microsoft/Google/Facebook: 70-80%) |
| Branch Coverage | 70% | Ensure critical paths are tested |

### Per-Module Strict Thresholds

| Module | Statement | Branch | Rationale |
|--------|-----------|--------|-----------|
| **core** | 90% | 85% | Foundational code - must be bulletproof |
| **contracts** | 90% | 85% | KILLER FEATURE - compile-time guarantees |
| **connectors** | 75% | 70% | Production-critical but allows for integration complexity |
| **infrastructure** | 75% | 70% | Support code, tested via integration tests |
| **examples** | 0% (disabled) | N/A | Demonstration code, not shipped |

### Codecov Per-Module Targets

| Module | Target | Threshold | Current Status |
|--------|--------|-----------|----------------|
| Core | 90% | 1% drop allowed | 🟡 Informational |
| Contracts | 90% | 1% drop allowed | 🟡 Informational |
| Connectors | 80% | 2% drop allowed | 🟡 Informational |
| Infrastructure | 75% | 2% drop allowed | 🟡 Informational |
| **Patch (New Code)** | 80% | 5% wiggle room | 🟡 Informational |

---

## Activation Instructions

### Phase 1 → Phase 2 Checklist

When ready to enforce coverage (typically at v1.0.0), follow these steps:

#### Step 1: Verify Current Coverage

```bash
# Run coverage locally
sbt clean coverage test coverageReport coverageAggregate

# Check if all modules meet thresholds
sbt "show coverageMinimumStmtTotal"
sbt "show coverageFailOnMinimum"
```

#### Step 2: Update codecov.yml

```diff
# codecov.yml
coverage:
  status:
    patch:
      default:
-       informational: true  # DEVELOPMENT: Report only
+       informational: false # PRODUCTION: Enforce
-       if_ci_failed: success
+       if_ci_failed: error
    project:
      core:
-       informational: true  # DEVELOPMENT: Report only
+       informational: false # PRODUCTION: Enforce
      contracts:
-       informational: true
+       informational: false
      connectors:
-       informational: true
+       informational: false
      infrastructure:
-       informational: true
+       informational: false
```

#### Step 3: Update CI/CD Workflows

```diff
# .github/workflows/coverage.yml
- name: Run tests with coverage (fallback)
  run: |
+   export SCOVERAGE_ENFORCE_THRESHOLD=true
    sbt -batch clean coverage \
      "core/test" "infrastructure/test" "connectors/test" "contracts/test" \
      coverageReport coverageAggregate
```

#### Step 4: Update Documentation

```diff
# README.md or docs/quality/release-criteria.md
-**Status**: 🟡 Development Mode (Report Only)
+**Status**: 🟢 Production Mode (Enforcement Active)
```

#### Step 5: Test Enforcement Locally

```bash
# Test with enforcement enabled
SCOVERAGE_ENFORCE_THRESHOLD=true sbt clean coverage test coverageReport

# Expected behavior:
# - If coverage >= thresholds: ✅ Build succeeds
# - If coverage < thresholds:  ❌ Build fails with error:
#   [error] Coverage is below minimum [actual% < threshold%]
```

#### Step 6: Commit and Document

```bash
git add codecov.yml .github/workflows/coverage.yml docs/quality/COVERAGE_ENFORCEMENT.md
git commit -m "feat(quality): activate coverage enforcement for v1.0

BREAKING CHANGE: Coverage enforcement is now active. Builds will fail if:
- Overall coverage < 75%
- Core/Contracts coverage < 90%
- New code (patch) coverage < 80%
"
```

---

## Testing Locally

### Development Mode (Current)

```bash
# Standard coverage run - reports but doesn't fail
sbt clean coverage test coverageReport

# View reports
open target/scala-2.13/scoverage-report/index.html

# Aggregate multi-module reports
sbt coverageAggregate
open target/scala-2.13/scoverage-report/index.html
```

**Expected**: Coverage reports generated, build succeeds regardless of coverage

### Production Mode (Future - v1.0+)

```bash
# Run with enforcement enabled
SCOVERAGE_ENFORCE_THRESHOLD=true sbt clean coverage test coverageReport

# Test failure scenario (simulate low coverage)
# Edit a test file to reduce coverage, then:
SCOVERAGE_ENFORCE_THRESHOLD=true sbt coverage test coverageReport
```

**Expected**:
- ✅ If coverage >= 75%: Build succeeds
- ❌ If coverage < 75%: Build fails with error message

### Per-Module Coverage Check

```bash
# Check specific module
sbt "core/coverage" "core/test" "core/coverageReport"

# With enforcement
SCOVERAGE_ENFORCE_THRESHOLD=true sbt "core/coverage" "core/test" "core/coverageReport"
```

---

## CI/CD Integration

### Current Workflow (.github/workflows/coverage.yml)

```yaml
- name: Run tests with coverage (fallback)
  run: |
    sbt -batch clean coverage \
      "core/test" "infrastructure/test" "connectors/test" "contracts/test" \
      coverageReport coverageAggregate
```

**Behavior**: Runs coverage, uploads to Codecov, **does not fail on low coverage**

### Future Workflow (v1.0+)

```yaml
- name: Run tests with coverage (ENFORCED)
  env:
    SCOVERAGE_ENFORCE_THRESHOLD: true  # ✅ Enable enforcement
  run: |
    sbt -batch clean coverage \
      "core/test" "infrastructure/test" "connectors/test" "contracts/test" \
      coverageReport coverageAggregate
```

**Behavior**: Runs coverage, uploads to Codecov, **FAILS if coverage < threshold**

---

## Codecov Integration

### Flag-Based Per-Module Uploads

Codecov uses **flags** to track coverage per module:

```yaml
# coverage.yml workflow
- name: Upload coverage (core)
  uses: codecov/codecov-action@v5
  with:
    files: modules/core/target/scala-*/scoverage-report/scoverage.xml
    flags: core  # ✅ Track core module separately

- name: Upload coverage (contracts)
  uses: codecov/codecov-action@v5
  with:
    files: modules/contracts/target/scala-*/scoverage-report/scoverage.xml
    flags: contracts  # ✅ Track contracts module separately
```

### Codecov Badge Configuration

Badges in README.md show per-module coverage:

```markdown
<!-- Overall coverage -->
[![codecov](https://codecov.io/gh/vim89/flowforge/graph/badge.svg)](https://codecov.io/gh/vim89/flowforge)

<!-- Per-module coverage -->
[![Core](https://img.shields.io/codecov/c/github/vim89/flowforge?flag=core&label=core&logo=codecov)](https://app.codecov.io/gh/vim89/flowforge/flags/core)
[![Contracts](https://img.shields.io/codecov/c/github/vim89/flowforge?flag=contracts&label=contracts&logo=codecov)](https://app.codecov.io/gh/vim89/flowforge/flags/contracts)
```

---

## Gradual Enforcement Strategy

### Development Phase (Now → v0.9.0)

**Goal**: Build coverage culture without blocking development

- ✅ Coverage measured on every PR
- ✅ Badges show trends
- ✅ PR comments highlight low-coverage areas
- ✅ Developers can see which modules need attention
- ❌ No build failures

**Actions**:
1. Monitor coverage trends
2. Improve low-coverage modules incrementally
3. Add tests for critical paths
4. Document testing patterns

### Pre-Release Phase (v0.9.0 → v1.0.0)

**Goal**: Meet all thresholds before enforcement

- ✅ All modules meet minimum thresholds
- ✅ Core and contracts at 90%+
- ✅ Integration tests cover critical paths
- ✅ Enforcement tested in feature branches

**Actions**:
1. Review coverage reports weekly
2. Create issues for low-coverage modules
3. Test enforcement with `SCOVERAGE_ENFORCE_THRESHOLD=true`
4. Update docs and migration guides

### Production Phase (v1.0.0+)

**Goal**: Maintain high quality bar

- ✅ Enforcement active in CI/CD
- ✅ PRs blocked if coverage drops
- ✅ All new code requires 80%+ coverage
- ✅ Regressions caught immediately

**Actions**:
1. Activate enforcement (follow checklist above)
2. Monitor CI failures and adjust if needed
3. Provide coverage improvement guidelines
4. Celebrate coverage milestones

---

## Troubleshooting

### Build Passes Locally But Should Fail

**Problem**: Coverage is below threshold but build succeeds

**Solution**: Check if `SCOVERAGE_ENFORCE_THRESHOLD` is set:

```bash
# Verify environment variable
echo $SCOVERAGE_ENFORCE_THRESHOLD

# Should be empty or "false" in development mode
# Set to "true" to test enforcement

SCOVERAGE_ENFORCE_THRESHOLD=true sbt coverage test coverageReport
```

### Codecov Shows Low Coverage But SBT Shows High

**Problem**: Mismatch between local scoverage and Codecov

**Solution**: Check coverage upload configuration:

```bash
# Verify coverage files exist
find . -name "scoverage.xml" -type f

# Check Codecov upload
# In CI logs, look for:
# "Uploaded coverage report successfully"
```

### Module Coverage Below Threshold

**Problem**: Specific module (e.g., core) below 90%

**Solution**:

```bash
# 1. Check current coverage
sbt "core/coverage" "core/test" "core/coverageReport"
open modules/core/target/scala-2.13/scoverage-report/index.html

# 2. Find low-coverage files
# Look for red/yellow highlighted files in HTML report

# 3. Add tests for uncovered code
# Focus on:
# - Public API methods
# - Critical business logic
# - Error handling paths

# 4. Re-run coverage
sbt "core/coverage" "core/test" "core/coverageReport"
```

---

## References

- **SBT Scoverage Plugin**: https://github.com/scoverage/sbt-scoverage
- **Codecov Documentation**: https://docs.codecov.com/docs/codecov-yaml
- **Industry Benchmarks**:
  - Microsoft/Google/Facebook: 70-80% baseline
  - High-reliability systems: 90%+
  - Data engineering: 75-85% recommended

---

**Maintained by**: FlowForge Core Team
**Next Review**: v0.9.0 Pre-Release
**Enforcement Target**: v1.0.0 Release
