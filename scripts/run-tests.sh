#!/bin/bash
# Comprehensive test runner

set -euo pipefail

echo "🧪 Running FlowForge test suite..."

# Quick tests
echo "⚡ Running quick tests..."
sbt testQuick

# Full test suite
echo "🔍 Running full test suite..."
sbt testAll

# Integration tests
echo "🔗 Running integration tests..."
sbt it:test

# Performance benchmarks
echo "📊 Running benchmarks..."
sbt benchmarks/Jmh/run

echo "✅ All tests completed!"
