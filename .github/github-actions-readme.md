# GitHub Actions Workflows

This directory contains CI/CD workflows for FlowForge.

## 🔄 Active Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| [ci.yml](workflows/ci.yml) | PR, Push to main | Build, test all modules, quality checks, security scans | ~10-15min |
| [coverage.yml](workflows/coverage.yml) | After CI on main, Weekly, Manual | Aggregate coverage from CI, upload to Codecov | ~2-5min |
| [docs-lint.yml](workflows/docs-lint.yml) | PR, Push | Documentation structure validation | ~2min |
| [link-check.yml](workflows/link-check.yml) | PR, Push | Validate markdown links (offline mode) | ~2min |
| [nightly.yml](workflows/nightly.yml) | Daily 3AM UTC | Cross-Scala builds, integration tests | ~20-30min |
| [release.yml](workflows/release.yml) | Manual dispatch | Build artifacts, Maven publish, Docker images, GitHub release, Scaladoc | ~15-20min |
| [security.yml](workflows/security.yml) | Push, PR, Weekly | CodeQL, dependency-check, TruffleHog secret scan | ~5-10min |
| [pr-validation.yml](workflows/pr-validation.yml) | PR events | Validate PR title format, detect breaking changes, check PR size | ~1min |
| [stale.yml](workflows/stale.yml) | Daily | Mark and close stale PRs/issues | ~1min |
| [changelog.yml](workflows/changelog.yml) | Push to main | Auto-generate CHANGELOG.md from conventional commits | ~1min |

## 📋 Workflow Details

### ci.yml - Continuous Integration
**Triggers**: Pull requests, pushes to main

**Jobs**:
- **quality**: Scalafmt + Scalafix checks (runs first)
- **build**: Matrix of 6 module groups with coverage enabled
  - core (core + compile-fail-tests)
  - connectors (connectors + connectors-gcs)
  - engines (engines-spark + engines-flink)
  - jdbc-quality (connectors-jdbc + quality-deequ)
  - clis (validation-cli + contracts-extractor-cli + maintenance-cli)
  - sdk-experimental (contracts-sdk + experimental)
- **security**: CodeQL (Java/Scala), TruffleHog, Semgrep (PR only)
- **spark-it**: Integration tests with retry logic (main branch only)

**Coverage**: Tests run with coverage instrumentation, artifacts uploaded for reuse by coverage.yml

### coverage.yml - Coverage Aggregation
**Triggers**: After successful CI run on main, weekly schedule, manual dispatch

**Strategy**:
- Downloads coverage artifacts from CI run (eliminates duplicate test execution)
- Aggregates coverage reports
- Uploads to Codecov with per-module flags (core, contracts, connectors, infrastructure)
- Fallback: Runs fresh tests if CI artifacts unavailable

### release.yml - Release Distribution
**Triggers**: Manual workflow_dispatch with version input

**Jobs**:
1. **validate-release**: Version validation, tag creation
2. **pre-release-tests**: G8 template validation
3. **build-artifacts**: Build CLI JARs, generate checksums (SHA256/SHA512), GPG signatures, SBOM
4. **create-release**: GitHub release with artifacts
5. **publish-docker**: Build and push 3 CLI Docker images to GHCR
6. **publish-docs**: Generate Scaladoc, deploy to GitHub Pages, verify deployment

**Artifacts**:
- Maven Central: Core libraries
- GHCR: validation-cli, contracts-extractor-cli, maintenance-cli Docker images
- GitHub Releases: JAR files with checksums and signatures
- GitHub Pages: Scaladoc API documentation

### security.yml - Security Scanning
**Triggers**: Push to main, PRs, weekly schedule

**Scans**:
- CodeQL (GitHub Actions workflows + Java/Scala code)
- dependency-check (SBT dependencies)
- TruffleHog (Secret scanning)

### pr-validation.yml - PR Quality Gates
**Triggers**: PR opened, edited, synchronized, reopened

**Checks**:
- PR title follows conventional commit format (feat|fix|docs|test|refactor|perf|chore|ci|build|style)
- Detects breaking changes in contract files
- Warns on large PRs (>1000 lines)

### nightly.yml - Extended Testing
**Triggers**: Daily at 3AM UTC

**Jobs**:
- Cross-Scala builds (2.13 only)
- Integration tests
- G8 template validation
- Extended test suites

### changelog.yml - Changelog Generation
**Triggers**: Push to main

**Process**:
- Uses git-cliff with conventional commits
- Auto-generates CHANGELOG.md
- Commits and pushes if changed

### stale.yml - Issue/PR Maintenance
**Triggers**: Daily

**Policy**:
- PRs: Stale after 60 days, close after 7 days
- Issues: Stale after 90 days, close after 14 days
- Exempt labels: keep-open, in-progress, blocked, bug, enhancement, good-first-issue

## 🔧 Composite Actions

### .github/actions/sbt
Reusable SBT execution with Java setup and caching.

**Inputs**:
- `java-version`: JDK version (default: 17)
- `distribution`: JDK distribution (default: temurin)
- `commands`: SBT commands to execute

**Used by**: ci.yml build matrix

### .github/actions/contract-materialize
Contract materialization helper action.

## 📦 Dependency Management

- **Renovate** ([renovate.json](renovate.json)): Manages SBT dependencies, Scala versions
- **Dependabot** ([dependabot.yml](dependabot.yml)): Manages GitHub Actions versions

## 🎯 Performance Optimizations

1. **Coverage reuse**: CI runs tests with coverage, coverage.yml aggregates without re-running
2. **Parallel builds**: 6 module groups run in parallel
3. **Conditional security**: Security scans only on PRs (CodeQL/TruffleHog/Semgrep)
4. **Smart caching**: Coursier + SBT caches across jobs
5. **Fail fast**: Quality checks run before tests

## 🔐 Security Features

- **PR scans**: CodeQL, TruffleHog, Semgrep on every PR
- **Weekly deep scan**: Full dependency-check
- **GPG signatures**: All release artifacts signed
- **SBOM**: Software Bill of Materials for releases
- **Pinned actions**: All actions pinned to commit SHAs (via Renovate)

## 📊 Success Metrics

- ✅ 100% module coverage (17/17 modules tested)
- ✅ Release artifacts: Maven Central + GHCR + GitHub Releases
- ✅ Security scans on every PR
- ✅ <15min CI time on PRs
- ✅ Zero duplicate test runs (coverage reuses CI artifacts)
- ✅ All jobs in correct dependency order

## 🚀 Quick Reference

### Run workflows locally
```bash
# Install act
brew install act

# Run CI
act pull_request --workflows .github/workflows/ci.yml

# Run security scan
act push --workflows .github/workflows/security.yml
```

### Trigger release
```bash
# Via GitHub CLI
gh workflow run release.yml -f version=0.1.0 -f prerelease=false

# Or via GitHub UI
# Actions → Release → Run workflow
```

### Check workflow status
```bash
# Latest runs
gh run list --limit 5

# Watch specific run
gh run watch <run-id>
```

## 📝 References

- [GitHub Actions Docs](https://docs.github.com/en/actions)
- [Security Hardening](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [CI/CD Improvement Plan](../docs/plan/ci-cd-improvements.md)
