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