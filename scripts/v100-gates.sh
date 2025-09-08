#!/bin/bash

set -e

echo "🚀 FlowForge v1.0.0 Quality Gates"
echo "================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Gate 1: Check for placeholders
echo "🔍 Gate 1: Checking for placeholders in main sources..."

ELLIPSES=$(find modules -name "*.scala" -path "*/src/main/*" -exec grep -l "\\.\\.\\.\\|…" {} \; 2>/dev/null || true)
if [ -n "$ELLIPSES" ]; then
  echo -e "${RED}❌ FAIL: Found ellipses placeholders in main sources:${NC}"
  echo "$ELLIPSES"
  exit 1
fi

QUESTIONS=$(find modules -name "*.scala" -path "*/src/main/*" -exec grep -l "???" {} \; 2>/dev/null || true)
if [ -n "$QUESTIONS" ]; then
  echo -e "${RED}❌ FAIL: Found ??? placeholders in main sources:${NC}"
  echo "$QUESTIONS"
  exit 1
fi

echo -e "${GREEN}✅ PASS: No placeholders found${NC}"
echo ""

# Gate 2: Check for IO leaks
echo "🔍 Gate 2: Checking for IO leaks in main sources..."

IO_LEAKS=$(find modules -name "*.scala" -path "*/src/main/*" \
           -not -path "*/examples/*" \
           -not -path "*/*-cli/*" \
           -not -name "EffectInstances.scala" \
           -not -name "EffectSystem.scala" \
           -exec grep -l "import.*cats\.effect\.IO\|: IO\[" {} \; 2>/dev/null || true)

if [ -n "$IO_LEAKS" ]; then
  echo -e "${RED}❌ FAIL: Found cats.effect.IO leaks in main sources:${NC}"
  echo "$IO_LEAKS"
  echo ""
  echo -e "${YELLOW}Per FlowForge v1.0.0 architecture:${NC}"
  echo "- Core/leaf modules must stay F-polymorphic"
  echo "- Only CLIs and examples may use concrete IO"
  exit 1
fi

echo -e "${GREEN}✅ PASS: No IO leaks found${NC}"
echo ""

# Gate 3: Check Delta constraint claims
echo "🔍 Gate 3: Checking Delta constraint claims..."

UNIQUE_CLAIMS=$(find modules -name "*.scala" -path "*/src/main/*" -exec grep -l "ADD CONSTRAINT.*UNIQUE\|supports.*UNIQUE.*constraint" {} \; 2>/dev/null | grep -v "does not support\|NOT SUPPORTED" || true)
if [ -n "$UNIQUE_CLAIMS" ]; then
  echo -e "${RED}❌ FAIL: Found incorrect UNIQUE constraint claims:${NC}"
  echo "$UNIQUE_CLAIMS"
  echo ""
  echo -e "${YELLOW}Delta Lake only supports NOT NULL and CHECK constraints.${NC}"
  exit 1
fi

echo -e "${GREEN}✅ PASS: Delta constraints correctly documented${NC}"
echo ""

# Gate 4: Check Spark version
echo "🔍 Gate 4: Checking Spark version..."

SPARK_VERSION=$(grep -o 'val spark.*= "[0-9]\.[0-9]\.[0-9]"' project/Dependencies.scala | grep -o '[0-9]\.[0-9]\.[0-9]' || true)
if [ "$SPARK_VERSION" != "3.5.6" ]; then
  echo -e "${RED}❌ FAIL: Incorrect Spark version: $SPARK_VERSION${NC}"
  echo -e "${YELLOW}Expected: 3.5.6 (latest 3.5 LTS per v1.0.0 plan)${NC}"
  exit 1
fi

echo -e "${GREEN}✅ PASS: Spark 3.5.6 correctly configured${NC}"
echo ""

# Optional: Quick build check if requested
if [ "$1" = "--with-build" ]; then
  echo "🔧 Gate 5: Quick build check..."
  sbt compile > /dev/null 2>&1
  echo -e "${GREEN}✅ PASS: Compilation successful${NC}"
  echo ""
fi

echo -e "${GREEN}🎉 ============================================${NC}"
echo -e "${GREEN}✅ FlowForge v1.0.0 Quality Gates: ALL PASSED${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "FlowForge is ready for v1.0.0 release! 🚀"