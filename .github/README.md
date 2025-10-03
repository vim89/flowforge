# GitHub actions workflows

This directory contains CI/CD workflows for FlowForge.

## 🔄 Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| [ci.yml](workflows/ci.yml) | PR, Push to main | Compile, test, quality checks (scalafix/scalafmt) | ~5-10min |
| [coverage.yml](workflows/coverage.yml) | PR, Push to main | Per-module coverage thresholds | ~5min |
| [docs-lint.yml](workflows/docs-lint.yml) | PR, Push | Documentation structure & Scaladoc | ~2min |
| [link-check.yml](workflows/link-check.yml) | PR, Push | Validate markdown links (offline mode) | ~2min |
| [nightly.yml](workflows/nightly.yml) | Scheduled (3AM UTC) | Full test suite, cross-Scala, benchmarks | ~15-30min |
| [release.yml](workflows/release.yml) | Tag push (v*) | Dynamic release notes, artifacts, Scaladoc | ~10-15min |
| [security.yml](workflows/security.yml) | Push, PR, Weekly | CodeQL, dependency scan, secret scan | ~5-10min |

## 📋 Workflow details

### ci.yml - Continuous Integration
**Triggers**: Pull requests, pushes to main

**Matrix Strategy**:
- 3 parallel jobs: core, connectors, engines
- fail-fast: false (all jobs run, but failures still fail CI)

**Steps**:
1. Checkout code
2. Setup Java 17 with sbt cache
3. Run scalafmt & scalafix checks (enforces ALL quality gates via .scalafix.conf)
4. Run module-specific tests

**Quality Gates (via Scalafix)**:
- ✅ No asInstanceOf/Any (except whitelisted files)
- ✅ No println/print in production code
- ✅ No try/Try/unsafeRunSync (ADR-022 safety)
- ✅ Code formatted (scalafmt)
- ✅ Compile-fail tests validate contract enforcement

**Additional Jobs**:
- Scaladoc coverage enforcement (100% for core/contracts)
- Spark integration tests (on main push, PR label, or manual)

### coverage.yml - Test coverage
**Triggers**: Pull requests, pushes to main

**Per-Module Thresholds** (enforced via sbt settings):
- core: 80% stmt, 75% branch (fail on miss)
- infrastructure: 70% stmt, 65% branch (fail on miss)
- connectors: 70% stmt (fail on miss)
- enginesSpark: 60% stmt (warn only)
- qualityDeequ: 60% stmt (warn only)

**Fixes applied**:
- ✅ Java 17 (consistent with CI)
- ✅ Per-module thresholds (not blanket 75%)
- ✅ Uses sbt cache (faster builds)

### docs-lint.yml - Documentation validation (merged)
**Triggers**: Pull requests, pushes

**Merged workflows**:
- ✅ Consolidated docs-lint.yml + doclint.yml
- Single job now runs both structure lint & Scaladoc check

**Checks**:
1. Documentation structure (scripts/lint-docs.sh)
   - CONTRIBUTING.md completeness
   - Handbook structure & ADR references
   - Coverage map integrity
2. Scaladoc coverage (scripts/doclint.sh)
   - 100% enforcement for core & contracts modules
   - Uploads report as artifact

### link-check.yml - Broken link detection
**Triggers**: Pull requests, pushes

**Fixes applied**:
- ✅ Step ID mismatch fixed (id: lychee added)
- ✅ Proper exit on failure (fail: true parameter)
- ✅ Markdown output format (easier to read)

**Scope**:
- All docs/**/*.md, README.md, AGENTS.md
- Offline mode only (local links & anchors)
- Accepts 200/204/301/302 status codes

### nightly.yml - Extended testing (enhanced)
**Triggers**: Scheduled (3:00 AM UTC), manual dispatch

**5 Parallel jobs**:
1. **Full test suite** (Java 17 & 21 matrix)
   - All module tests with coverage
   - Compile-fail tests
   - Uploads coverage to Codecov
2. **Integration tests**
   - Spark/Delta workflows
   - Flink engine tests
3. **Cross-Scala builds** (2.12, 2.13, 3.3)
   - Validates multi-version compatibility
4. **Performance benchmarks**
   - JMH benchmarks (if module exists)
   - Uploads results as artifacts
5. **Template validation**
   - Tests g8 template generation & compilation

### release.yml - Release process
**Triggers**: Git tag push (v*), manual dispatch

**4 Sequential jobs**:
1. **Validate release** - Version format, no duplicates
2. **Pre-release tests** - Full suite, compile-fail, g8 template
3. **Build artifacts** - validation-cli, contracts-extractor-cli JARs
4. **Publish** - Scaladoc to GitHub Pages, create release

**Improvements applied**:
- ✅ Dynamic release notes from git history (not hardcoded)
- ✅ Uses git tags for versioning (sbt-dynver, no sed hacks)
- ✅ Rollback instructions on failure
- ✅ Validates g8 template with contract drift demo

**Artifacts**:
- CLI tool JARs (assembly)
- Scaladoc (per-module + unified)
- Dynamic release notes with commit log

### security.yml - Security scanning
**Triggers**: Push to main, PRs, Weekly (Mondays 6AM)

**3 Jobs**:
1. **CodeQL Analysis**
   - Java/Scala static analysis
   - Security & quality queries
   - Uploads findings to Security tab
2. **Dependency vulnerability scan**
   - Checks for known CVEs in dependencies
   - Uses sbt-dependency-check (if configured)
   - Uploads HTML report
3. **Secret scanning (TruffleHog)**
   - Scans commits for leaked secrets
   - Verified secrets only (reduces false positives)

**New Files**:
- `.github/workflows/security.yml`
- `.github/renovate.json` (dependency automation)

## 🛠️ Local development

### Running checks locally

```bash
# Compile and test (matches CI)
sbt clean compile test

# Format code
sbt scalafmtAll

# Check ALL quality gates (same as CI)
sbt scalafmtCheckAll "scalafixAll --check"

# Run docs structure lint
./scripts/lint-docs.sh

# Run Scaladoc coverage check
./scripts/doclint.sh

# Check links
docker run --rm -v $(pwd):/app lycheeverse/lychee --offline --accept 200,204,301,302 "docs/**/*.md"

# Run coverage with per-module thresholds
sbt clean coverage test coverageReport coverageAggregate
```

### Testing workflows locally

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

### CI failures

**"Scalafix violations"**
- Run: `sbt "scalafixAll --check"` to see violations
- Common issues:
  - `asInstanceOf` usage (use pattern matching or add to whitelist in `.scalafix.conf`)
  - `println` in production code (use Logger instead)
  - `try/catch` or `scala.util.Try` (use Safety.safely or EffectSystem.attempt)
  - `unsafeRunSync` (use IOApp or Resource.use at edges)

**"Formatting check failed"**
- Run: `sbt scalafmtAll`
- Commit formatted code

**"Compile-fail test passed (should fail!)"**
- Verify test in `modules/compile-fail-tests/`
- Ensure test code should NOT compile

**"Coverage threshold not met"**
- Check per-module thresholds in coverage.yml
- Core needs 80% stmt coverage, infrastructure 70%, etc.

**"Docs lint failed"**
- Run: `./scripts/lint-docs.sh`
- Check CONTRIBUTING.md, Handbook sections, ADR references

**"Link check failed"**
- Run lychee locally (see commands above)
- Fix broken internal links or anchors

### Workflow permissions

Workflows require these secrets:
- `GITHUB_TOKEN`: Auto-provided (used for releases, CodeQL, secret scanning)
- `CODECOV_TOKEN`: For coverage reporting (coverage.yml, nightly.yml)

**Note**: Renovate runs via GitHub App (no token needed in repo)

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

## 📊 Summary of CI/CD improvements

**P0 Fixes (Break trust)**:
- ✅ Removed `continue-on-error: true` (replaced with `fail-fast: false`)
- ✅ Integration tests now run on main pushes + PR label
- ✅ Link checker step ID mismatch fixed

**P1 Improvements (Waste resources)**:
- ✅ Scalafix checks deduplicated (single comprehensive check)
- ✅ Per-module coverage thresholds (not blanket 75%)
- ✅ Merged docs-lint.yml + doclint.yml (single workflow)
- ✅ Shell quality gates → Scalafix rules (.scalafix.conf) - **FULLY ENFORCED**

**Scalafix Enforcement Strategy**:
- **Configuration**: ✅ Comprehensive rules in `.scalafix.conf` (no println, no throw, no null, no unsafeRunSync, etc.)
- **Production Code**: ✅ 100% compliant - zero violations
- **Example Code**: Excluded from checks (demo code, intentionally uses println)
- **Test Code**: Violations documented as technical debt, not enforced
- **CI Enforcement**: ✅ Enabled for all production code (examples excluded via grep filter)
- **Whitelist Checks**: asInstanceOf and scala.util.Try whitelist enforcement in shell scripts (ci.yml doc-lint job)
- **Result**: Production code is 100% compliant with all quality gates

**P2 Enhancements (Tech debt)**:
- ✅ Dynamic release notes from git history
- ✅ Enhanced nightly.yml (5 jobs: tests, integration, cross-Scala, benchmarks, template)
- ✅ Java version matrix (17, 21) in nightly builds
- ✅ Security scanning (CodeQL, dependency check, TruffleHog)
- ✅ Renovate dependency automation configured

**Other Fixes**:
- ✅ Removed hardcoded test names in matrix
- ✅ Path filtering updated (removed mvp-0.0.1-snapshot branch)
- ✅ Coverage.yml Java 17 (consistent with CI)
- ✅ Release.yml uses git tags (no sed hacks)
- ✅ Rollback instructions added to release workflow

---

**Last Updated**: 2025-10-03
**Workflow Version**: 3.0 (fully renovated)
