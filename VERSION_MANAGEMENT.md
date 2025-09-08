# FlowForge Version Management Strategy

## Current Version Strategy

### Development Version
- **Current**: `0.1.0-SNAPSHOT` (development branch `mvp-0.0.1-snapshot`)
- **Next Release**: `0.1.0` (first official release)
- **Target**: `1.0.0` (stable release with 100% compile-time contracts)

### Version Format
Following **Semantic Versioning 2.0.0**:
- `MAJOR.MINOR.PATCH[-QUALIFIER]`
- Pre-release: `-SNAPSHOT`, `-alpha`, `-beta`, `-rc1`

### Release Process

#### 1. Pre-Release (Current State)
```bash
# Development continues on mvp-0.0.1-snapshot branch
# Version in build.sbt: "0.1.0-SNAPSHOT"
```

#### 2. First Release (0.1.0)
```bash
# Manual workflow dispatch
gh workflow run release.yml --field version=0.1.0 --field prerelease=false

# OR push tag
git tag v0.1.0
git push origin v0.1.0
```

#### 3. Future Releases
- `0.2.0` - Additional features, enhancements
- `0.3.0` - More contract features, integrations  
- `1.0.0` - Stable API, production-ready

### Automated Release Workflow

The `.github/workflows/release.yml` handles:

1. **Version Validation**
   - Checks semantic version format
   - Prevents duplicate releases
   - Supports both tag and manual triggers

2. **Pre-Release Testing** 
   - Full test suite with coverage
   - Compile-fail tests validation
   - Code formatting checks

3. **Artifact Building**
   - CLI JAR assembly (`validation-cli`, `contracts-extractor-cli`)
   - Version updates in build.sbt
   - Artifact upload to GitHub releases

4. **Release Creation**
   - Auto-generated release notes
   - Binary artifact attachments
   - Codecov integration

### Branch Strategy

- **`mvp-0.0.1-snapshot`** - Current development (pre-0.1.0)
- **`main`** - Stable releases (post-0.1.0)
- **Feature branches** - `feature/xyz` (merge to development branch)

### Creating Your First Release (0.1.0)

```bash
# Option 1: Manual workflow trigger
# Go to GitHub Actions -> Release -> Run workflow
# Enter version: 0.1.0
# Pre-release: false

# Option 2: Git tag
git checkout mvp-0.0.1-snapshot
git tag v0.1.0
git push origin v0.1.0
```

This will:
- ✅ Run full test suite with coverage
- ✅ Build CLI JARs  
- ✅ Create GitHub release with artifacts
- ✅ Generate release notes highlighting 100% compile-time contracts
- ✅ Upload coverage reports

### Post-0.1.0 Development

After first release:
- Merge `mvp-0.0.1-snapshot` → `main` 
- Continue development on `main` with `-SNAPSHOT` versions
- Use proper semantic versioning for all future releases

### Coverage & Quality Gates

All releases require:
- ✅ All tests passing
- ✅ Code coverage reporting 
- ✅ Compile-fail tests validation
- ✅ Code formatting compliance
- ✅ Artifact generation success

## FlowForge 1.0 Release Candidate (RC) Checklist

### RC1 Prerequisites (0.9.0-RC1)

Before cutting the first Release Candidate:

#### ✅ Core Functionality
- [ ] Lineage auto-emits START/COMPLETE/FAIL events from PipelineBuilder lifecycle
- [ ] Marquez integration documented with docker-compose quickstart
- [ ] Dual-mode quality validation: native Spark (default) + optional Deequ enhancement
- [ ] End-to-end Spark example runs locally in seconds with Delta constraints
- [ ] Complete UsersPipeline.scala demonstrating all v1.0 features

#### ✅ API Surface & Documentation
- [ ] Public API documented in docs/public-api.md with "Proposed 1.0 surface" label
- [ ] Neutral comparison documentation in docs/why/compare.md with comprehensive links
- [ ] All internal packages properly namespaced (*.internal.* for non-public APIs)
- [ ] Deprecated SparkPipelineBuilder marked for removal at 1.0
- [ ] Clean module structure - unused modules removed (quality-deequ-runner, templates/)

#### ✅ Quality Assurance
- [ ] **API Diff Analysis**: No breaking changes in public surface since last RC
- [ ] **Scripted Tests Green**: All scripted tests pass in CI consistently
- [ ] **Examples Runnable**: All examples execute successfully in clean environment
- [ ] Compile-fail tests validate contract enforcement at build time
- [ ] Integration tests prove Delta Lake constraint enforcement
- [ ] Multi-cloud storage recipes documented (S3A/ABFS/GCS via Spark drivers)

#### ✅ Performance & Reliability  
- [ ] Memory safety - no driver OOM through sampling strategies
- [ ] Resource safety - all operations use Resource[F, _] for cleanup  
- [ ] Effect system compatibility (Cats Effect + ZIO) validated
- [ ] Local execution completes in seconds (not minutes)
- [ ] Lineage emission works "out of the box" with Marquez docker-compose

### 1.0.0 Final Release Checklist

Ship 1.0.0 only after:

#### ✅ API Stability
- [ ] **Public API frozen** - no further changes to public surface
- [ ] Binary compatibility guarantees documented and validated
- [ ] All *.internal.* packages clearly marked as non-public
- [ ] Example templates consistent with public API usage

#### ✅ Production Readiness
- [ ] **All smoke tests stable** (Spark/Flink/compile-fail) on CI
- [ ] Performance benchmarks meet local execution targets (<30 seconds)
- [ ] Resource cleanup verified under failure scenarios
- [ ] Multi-cloud storage integration proven via examples

#### ✅ Documentation Completeness
- [ ] docs/public-api.md reflects final 1.0 surface (remove "Proposed" label)
- [ ] Migration guides from 0.x to 1.0 documented
- [ ] All examples demonstrate real-world usage patterns
- [ ] OpenLineage integration documented with multiple backends

#### ✅ Ecosystem Integration
- [ ] Delta Lake constraints working across S3A/ABFS/GCS storage
- [ ] Quality validation modes (native/Deequ) documented and tested
- [ ] Effect system abstraction allows ZIO/Cats Effect interchangeability
- [ ] Template generation produces buildable, runnable projects

### 1.0 Success Criteria

After 1.0 release, the promise must read cleanly:
- **Change the contract** → won't compile (build fails fast)
- **Fix types** → compiles (type safety enforced)  
- **Run locally in seconds** → see DQ + Delta constraints catch regressions
- **Open Marquez** → see lineage light up automatically

### Version Progression Strategy

```
Current: 0.1.0-SNAPSHOT (mvp-0.0.1-snapshot branch)
    ↓
0.9.0-RC1 (first release candidate)
    ↓
0.9.0-RC2 (bug fixes, final polish)
    ↓  
1.0.0 (stable release with API guarantees)
    ↓
1.x.x (feature additions, maintaining binary compatibility)
```