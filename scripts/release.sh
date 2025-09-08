#!/bin/bash
# Release automation script

set -euo pipefail

VERSION=${1:-}
if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 0.2.0"
    exit 1
fi

echo "🚀 Preparing FlowForge release $VERSION"

# Run full test suite
echo "🧪 Running full test suite"
sbt fullCheck

# Update version
echo "📝 Updating version to $VERSION"
echo "ThisBuild / version := \"$VERSION\"" > version.sbt

# Create git tag
echo "🏷️ Creating git tag"
git add version.sbt
git commit -m "Release $VERSION"
git tag "v$VERSION"

# Push to trigger release
echo "📤 Pushing release"
git push origin main
git push origin "v$VERSION"

echo "✅ Release $VERSION initiated!"
echo "📋 Check GitHub Actions for release progress"
