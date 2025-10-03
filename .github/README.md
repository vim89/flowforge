# GitHub Actions Workflows

This directory contains CI/CD workflows for FlowForge.

## 🔄 Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| [ci.yml](workflows/ci.yml) | PR, Push to main | Compile, test, quality checks | ~5-10min |
| [coverage.yml](workflows/coverage.yml) | PR, Push to main | Test coverage reporting | ~5min |
| [docs-lint.yml](workflows/docs-lint.yml) | PR, Docs changes | Documentation linting | ~1min |
| [doclint.yml](workflows/doclint.yml) | PR | Structured docs validation | ~1min |
| [link-check.yml](workflows/link-check.yml) | PR, Push | Validate all markdown links | ~2min |
| [nightly.yml](workflows/nightly.yml) | Scheduled (nightly) | Extended tests, benchmarks | ~15-30min |
| [release.yml](workflows/release.yml) | Tag push (v*) | Build and publish release | ~10-15min |
| [release-please.yml](workflows/release-please.yml) | Push to main | Auto-generate release PRs | ~1min |
| [scaladoc.yml](workflows/scaladoc.yml) | Push to main, release | Generate and publish API docs | ~5min |

## 📋 Workflow Details

### ci.yml - Continuous Integration
**Triggers**: Pull requests, pushes to main and feature branches

**Steps**:
1. Checkout code
2. Setup Java (11, 17, 21)
3. Compile all modules
4. Run tests (unit, integration)
5. Run scalafix & scalafmt checks
6. Run compile-fail tests (proof of compile-time guarantees!)
7. Generate coverage report

**Quality Gates**:
- ✅ All tests must pass
- ✅ Code must be formatted (scalafmt)
- ✅ No scalafix violations
- ✅ Compile-fail tests must fail correctly

### coverage.yml - Test Coverage
**Triggers**: Pull requests, pushes to main

**Steps**:
1. Run tests with coverage enabled
2. Generate coverage report
3. Upload to Codecov
4. Comment coverage delta on PR

**Thresholds**:
- Core modules: >80%
- Infrastructure: >70%
- Experimental: >60%

### docs-lint.yml & doclint.yml - Documentation Validation
**Triggers**: PR with doc changes

**Steps**:
1. Run docs lint script (`scripts/lint-docs.sh`)
2. Validate ADR structure
3. Check cross-references
4. Verify required sections exist

**Checks**:
- CONTRIBUTING.md completeness
- Handbook structure
- ADR format compliance

### link-check.yml - Broken Link Detection
**Triggers**: Pull requests, pushes

**Steps**:
1. Use lychee to check all markdown links
2. Verify local links and anchors
3. Offline mode (no external checks)

**Scope**:
- All docs/**/*.md
- README.md, AGENTS.md
- Local links only (external links flaky)

### nightly.yml - Extended Testing
**Triggers**: Scheduled (1:00 AM UTC)

**Steps**:
1. Run extended test suite
2. Performance benchmarks
3. Cross-Scala-version compatibility
4. Integration tests with external services

**Output**:
- Benchmark trends
- Performance regression detection
- Compatibility matrix

### release.yml - Release Process
**Triggers**: Git tag push (v*)

**Steps**:
1. Validate version tag
2. Run full test suite
3. Build artifacts for all Scala versions
4. Generate Scaladoc
5. Publish to Sonatype/Maven Central
6. Create GitHub release with notes
7. Update documentation site

**Artifacts**:
- JARs for Scala 2.12, 2.13
- Sources JAR
- Scaladoc JAR
- Release notes

### release-please.yml - Automated Releases
**Triggers**: Push to main

**Steps**:
1. Analyze commits for conventional commits
2. Calculate next version
3. Generate changelog
4. Create release PR

**Versioning**:
- feat: → minor version bump
- fix: → patch version bump
- BREAKING CHANGE: → major version bump

### scaladoc.yml - API Documentation
**Triggers**: Push to main, release tags

**Steps**:
1. Generate Scaladoc for all modules
2. Aggregate docs
3. Publish to GitHub Pages

**Output**:
- https://vim89.github.io/flowforge/api/

## 🛠️ Local Development

### Running Checks Locally

```bash
# Compile and test (matches CI)
sbt clean compile test

# Format code
sbt scalafmtAll

# Check formatting
sbt scalafmtCheckAll

# Apply scalafix
sbt "scalafixAll"

# Check scalafix
sbt "scalafix --check"

# Run docs lint
./scripts/lint-docs.sh

# Check links
docker run --rm -v $(pwd):/app lycheeverse/lychee --offline "docs/**/*.md"
```

### Testing Workflows Locally

Use [act](https://github.com/nektos/act) to run workflows locally:

```bash
# Install act
brew install act

# Run CI workflow
act pull_request -W .github/workflows/ci.yml

# Run docs lint
act pull_request -W .github/workflows/docs-lint.yml
```

## 🚨 Troubleshooting

### CI Failures

**"Formatting check failed"**
- Run: `sbt scalafmtAll`
- Commit formatted code

**"Scalafix violations"**
- Run: `sbt "scalafixAll"`
- Review and fix violations

**"Compile-fail test passed (should fail!)"**
- Verify test in `modules/compile-fail-tests/`
- Ensure test code should NOT compile

**"Docs lint failed"**
- Run: `./scripts/lint-docs.sh`
- Fix reported issues

**"Link check failed"**
- Find broken link in output
- Fix or update link
- For external links, consider offline mode

### Workflow Permissions

Workflows require these secrets:
- `GITHUB_TOKEN`: Auto-provided
- `CODECOV_TOKEN`: For coverage reporting
- `SONATYPE_USERNAME`: For publishing
- `SONATYPE_PASSWORD`: For publishing
- `GPG_PRIVATE_KEY`: For artifact signing

## 📝 Adding New Workflows

1. Create workflow file in `.github/workflows/`
2. Define triggers and jobs
3. Test locally with `act`
4. Document in this README
5. Update PR template if needed

## 🔗 Related

- [Contributing Guide](../CONTRIBUTING.md)
- [Release Process](../docs/operating/releases.md)
- [CI/CD Best Practices](../docs/quality/ci-cd.md)

---

**Last Updated**: 2025-10-03
**Workflow Version**: 2.x
