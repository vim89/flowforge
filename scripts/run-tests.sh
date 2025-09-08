#!/bin/bash
# Comprehensive test runner - uses existing build.sbt

set -e

echo "🧪 Running FlowForge test suite"

# Unit tests
echo "⚡ Running unit tests"
sbt test

# Integration tests (if configured in existing build.sbt)
echo "🔗 Running integration tests"
sbt it:test

echo "✅ All tests completed!"
