# CI/CD Complete Overhaul Plan

**Status:** COMPREHENSIVE REDESIGN REQUIRED
**Date:** 2025-10-05
**Severity:** CRITICAL - Current CI/CD is 47% incomplete and fundamentally broken

---

## Executive Summary

Current CI/CD has **critical functional and non-functional gaps**:
- **Only 8/17 modules tested** (47% coverage)
- **No release distribution** (Maven, Docker)
- **Wrong job execution order** (docs before release)
- **No security on PRs** (only weekly)
- **Guaranteed failures** (g8 templates, missing secrets)

---

## Phase 1: CRITICAL FIXES (Immediate - Week 1)

### 1.1 Fix Guaranteed Failures
**Priority: P0 - Blocking**

- [ ] **release.yml g8 template validation**
  - Replace `/tmp` with `${{ runner.temp }}`
  - Fix: `cd ${{ runner.temp }}` (lines 125, 160)

- [ ] **coverage.yml CODECOV_TOKEN**
  - Add secret to repository settings OR
  - Make codecov upload optional with `if: env.CODECOV_TOKEN != ''`

- [ ] **nightly.yml cross-Scala builds**
  - Remove unsupported Scala 2.12/3.3 from matrix OR
  - Add `continue-on-error: true` to cross-compile job

- [ ] **security.yml dependency-check**
  - Add `sbt-dependency-check` to `project/plugins.sbt` OR
  - Make check conditional: `if grep -q "sbt-dependency-check" project/plugins.sbt`

### 1.2 Add Missing Module Tests
**Priority: P0 - Functional Requirement**

Add to `ci.yml` build matrix:
```yaml
- module: jdbc-quality
  commands: >
    scalafmtCheckAll connectorsJdbc/test qualityDeequ/test

- module: clis
  commands: >
    scalafmtCheckAll validationCli/test contractsExtractorCli/test maintenanceCli/test

- module: sdk-experimental
  commands: >
    scalafmtCheckAll contractsSdk/test experimental/test
```

Examples remain untested (demo code).

### 1.3 Fix Job Dependencies
**Priority: P0 - Correctness**

**CI workflow:**
```yaml
jobs:
  quality:  # NEW: runs first
    steps:
      - scalafmt
      - scalafix

  build:
    needs: [quality]  # wait for quality
    # ... matrix tests

  integration:
    needs: [build]  # wait for unit tests
```

**Release workflow:**
```yaml
jobs:
  validate-release:

  pre-release-tests:
    needs: [validate-release]

  build-artifacts:
    needs: [pre-release-tests]

  create-release:
    needs: [build-artifacts]

  publish-docs:  # AFTER release
    needs: [create-release]
```

---

## Phase 2: FUNCTIONAL COMPLETENESS (Week 2)

### 2.1 Release Distribution
**Priority: P1 - Product Requirement**

- [ ] **Maven Central publish**
  ```yaml
  - name: Publish to Sonatype
    run: sbt publishSigned sonatypeBundleRelease
    env:
      SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
      SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
      PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
  ```

- [ ] **Docker images**
  ```yaml
  - name: Build CLI Docker images
    run: |
      docker build -t ghcr.io/${{ github.repository }}/validation-cli:${{ needs.validate-release.outputs.version }} -f validation-cli.Dockerfile .
      docker build -t ghcr.io/${{ github.repository }}/contracts-extractor-cli:${{ needs.validate-release.outputs.version }} -f contracts-extractor-cli.Dockerfile .
      docker build -t ghcr.io/${{ github.repository }}/maintenance-cli:${{ needs.validate-release.outputs.version }} -f maintenance-cli.Dockerfile .

  - name: Push to GHCR
    run: |
      echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
      docker push --all-tags ghcr.io/${{ github.repository }}/validation-cli
      docker push --all-tags ghcr.io/${{ github.repository }}/contracts-extractor-cli
      docker push --all-tags ghcr.io/${{ github.repository }}/maintenance-cli
  ```

- [ ] **Build maintenanceCli JAR**
  ```yaml
  - name: Build ALL CLI artifacts
    run: |
      sbt "validation-cli/assembly"
      sbt "contracts-extractor-cli/assembly"
      sbt "maintenance-cli/assembly"  # ADDED
  ```

### 2.2 Artifact Security
**Priority: P1 - Compliance**

- [ ] **Checksums and signatures**
  ```yaml
  - name: Generate checksums
    run: |
      cd release-artifacts
      sha256sum *.jar > SHA256SUMS
      sha512sum *.jar > SHA512SUMS

  - name: Sign artifacts
    run: |
      gpg --batch --detach-sign --armor release-artifacts/*.jar
    env:
      GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
  ```

- [ ] **SBOM generation**
  ```yaml
  - name: Generate SBOM
    uses: anchore/sbom-action@v0
    with:
      path: ./
      artifact-name: sbom.spdx.json
  ```

---

## Phase 3: NON-FUNCTIONAL REQUIREMENTS (Week 3)

### 3.1 Performance Optimization

- [ ] **Eliminate duplicate test runs**
  ```yaml
  # coverage.yml
  jobs:
    coverage:
      steps:
        - name: Download test results from CI
          uses: actions/download-artifact@v4
          with:
            name: test-results
            path: target/test-reports

        - name: Generate coverage from existing tests
          run: sbt coverageReport coverageAggregate
  ```

- [ ] **Optimize nightly**
  - Remove redundant Java 21 (keep 17 only)
  - Remove Scala 2.12/3.3 (unsupported)
  - Run integration tests only on Java 17

### 3.2 Security Hardening

- [ ] **Security scans on every PR**
  ```yaml
  # ci.yml
  on:
    pull_request:
      branches: [main]

  jobs:
    security:
      steps:
        - name: CodeQL Analysis
          uses: github/codeql-action/analyze@v3

        - name: Dependency Check
          run: sbt dependencyCheck

        - name: Secret Scan
          uses: trufflesecurity/trufflehog@main
          with:
            extra_args: --only-verified=false
  ```

- [ ] **SAST on PRs**
  ```yaml
  - name: Semgrep SAST
    uses: returntocorp/semgrep-action@v1
  ```

### 3.3 Reliability

- [ ] **Integration tests on main**
  ```yaml
  # ci.yml
  spark-it:
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Spark Integration Tests
        run: sbt -DwithSparkIT=true "enginesSpark/testOnly *SparkDeltaSCD2IT"
  ```

- [ ] **Retry flaky tests**
  ```yaml
  - name: Run tests with retry
    uses: nick-fields/retry-action@v2
    with:
      timeout_minutes: 30
      max_attempts: 3
      command: sbt test
  ```

- [ ] **Fail fast on quality issues**
  ```yaml
  build:
    needs: [quality]  # Don't run tests if format/lint fails
  ```

### 3.4 Observability

- [ ] **Failure notifications**
  ```yaml
  - name: Notify on failure
    if: failure()
    uses: slackapi/slack-github-action@v1
    with:
      payload: |
        {
          "text": "CI failed on ${{ github.repository }} - ${{ github.ref }}"
        }
    env:
      SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK }}
  ```

- [ ] **Deployment verification**
  ```yaml
  # After release
  - name: Verify release artifacts
    run: |
      curl -f https://github.com/${{ github.repository }}/releases/download/v${{ needs.validate-release.outputs.version }}/validation-cli-assembly.jar
  ```

---

## Phase 4: MISSING WORKFLOWS (Week 4)

### 4.1 PR Validation Workflow

**File:** `.github/workflows/pr.yml`
```yaml
name: PR Validation

on:
  pull_request:
    branches: [main]

jobs:
  validate:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v5
      - name: Check PR title
        run: |
          if ! echo "${{ github.event.pull_request.title }}" | grep -qE "^(feat|fix|docs|test|refactor|perf|chore):"; then
            echo "PR title must start with type: feat/fix/docs/test/refactor/perf/chore"
            exit 1
          fi

      - name: Check for breaking changes
        run: |
          if git diff origin/main --name-only | grep -q "modules/contracts/.*Contract.scala"; then
            echo "⚠️  Contract changes detected - verify backwards compatibility"
          fi
```

### 4.2 Dependency Updates

**File:** `.github/dependabot.yml`
```yaml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"

  - package-ecosystem: "docker"
    directory: "/"
    schedule:
      interval: "weekly"
```

### 4.3 Stale PR/Issue Management

**File:** `.github/workflows/stale.yml`
```yaml
name: Stale

on:
  schedule:
    - cron: '0 0 * * *'

jobs:
  stale:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/stale@v9
        with:
          stale-pr-message: 'This PR is stale - closing in 7 days'
          days-before-stale: 60
          days-before-close: 7
```

### 4.4 Changelog Generation

**File:** `.github/workflows/changelog.yml`
```yaml
name: Changelog

on:
  push:
    branches: [main]

jobs:
  changelog:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - name: Generate changelog
        uses: orhun/git-cliff-action@v2
        with:
          config: cliff.toml
          args: --verbose
        env:
          OUTPUT: CHANGELOG.md
```

---

## Phase 5: CONTINUOUS IMPROVEMENT (Ongoing)

### 5.1 Metrics & Monitoring

- [ ] **Workflow duration tracking**
- [ ] **Test flakiness detection**
- [ ] **Coverage trend analysis**

### 5.2 Developer Experience

- [ ] **Workflow documentation** in `.github/workflows/README.md`
- [ ] **Local CI emulation** with `act`
- [ ] **Pre-commit hooks** for local quality gates

---

## Implementation Checklist

### Week 1: Critical Fixes
- [ ] Fix g8 template /tmp usage
- [ ] Add CODECOV_TOKEN or make optional
- [ ] Fix nightly cross-Scala builds
- [ ] Add dependency-check plugin
- [ ] Add 8 missing module tests
- [ ] Fix CI job dependencies
- [ ] Fix release job order

### Week 2: Functional Completeness
- [ ] Maven Central publish
- [ ] Docker image build/push
- [ ] Build maintenanceCli
- [ ] Generate checksums/signatures
- [ ] Generate SBOM

### Week 3: Non-Functional
- [ ] Eliminate duplicate tests
- [ ] Security scans on PR
- [ ] Integration tests on main
- [ ] Add retry logic
- [ ] Add notifications
- [ ] Add deployment verification

### Week 4: Missing Workflows
- [ ] PR validation workflow
- [ ] Dependabot config
- [ ] Stale PR management
- [ ] Changelog generation

---

## Success Metrics

- **100% module coverage** (17/17 modules tested)
- **Release artifacts** published to Maven Central + GHCR
- **Security scans** on every PR
- **<10min CI time** on PRs
- **Zero guaranteed failures**
- **All jobs in correct dependency order**

---

## References

- GitHub Actions best practices: https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions
- Codecov flags: https://docs.codecov.com/docs/flags
- Maven publish: https://github.com/sbt/sbt-pgp
- SBOM generation: https://github.com/anchore/sbom-action
