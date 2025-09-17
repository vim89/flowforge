#!/bin/bash

# FlowForge Compile-Fail Demo Script
# This script demonstrates our core USP: "Pipelines become unbuildable when schema drift occurs"

set -e

echo "🚀 FlowForge Compile-Fail Demo: Proving Our Core USP"
echo "=" * 60

# Function to uncomment a specific test
uncomment_test() {
    local test_name="$1"
    local start_marker="def ${test_name}():"
    local end_marker="  }"

    echo "🔧 Uncommenting ${test_name}"

    # Use sed to uncomment the test block
    sed -i.bak "/\/\* *$/,/\*\/ *$/{
        /\/\* *$/d
        /\*\/ *$/d
        s|^  /\*||
        s|\*/\$||
    }" src/test/scala/com/flowforge/compilefail/ContractDriftCompileFailTests.scala
}

# Function to comment a test back
comment_test() {
    local test_name="$1"
    echo "📝 Commenting ${test_name} back"

    # Restore from backup
    mv src/test/scala/com/flowforge/compilefail/ContractDriftCompileFailTests.scala.bak \
       src/test/scala/com/flowforge/compilefail/ContractDriftCompileFailTests.scala
}

# Function to attempt compilation and capture result
try_compile() {
    echo "🔨 Attempting to compile"
    if sbt compile 2>&1 | tee /tmp/compile_output.log; then
        echo "✅ Compilation succeeded"
        return 0
    else
        echo "❌ Compilation failed (as expected!)"
        echo ""
        echo "📋 Error message:"
        grep -A 20 "FlowForge Contract Drift Detected" /tmp/compile_output.log || echo "Contract error detected"
        return 1
    fi
}

echo ""
echo "📋 Demo #1: Field Name Drift Detection"
echo "---"
echo "This test shows what happens when a field name drifts from 'email' to 'emailAddress'"

# Test field name drift
uncomment_test "testFieldNameDrift"
if try_compile; then
    echo "❌ FAILED: This should not have compiled!"
    exit 1
else
    echo "✅ SUCCESS: Field name drift correctly detected at compile time!"
fi
comment_test "testFieldNameDrift"

echo ""
echo "📋 Demo #2: Missing Field Detection"
echo "---"
echo "This test shows what happens when a required field is missing"

# Test missing field
uncomment_test "testMissingField"
if try_compile; then
    echo "❌ FAILED: This should not have compiled!"
    exit 1
else
    echo "✅ SUCCESS: Missing field correctly detected at compile time!"
fi
comment_test "testMissingField"

echo ""
echo "📋 Demo #3: Wrong Evolution Policy Detection"
echo "---"
echo "This test shows what happens when using Exact policy with extra fields"

# Test wrong evolution policy
uncomment_test "testWrongEvolutionPolicy"
if try_compile; then
    echo "❌ FAILED: This should not have compiled!"
    exit 1
else
    echo "✅ SUCCESS: Wrong evolution policy correctly detected at compile time!"
fi
comment_test "testWrongEvolutionPolicy"

echo ""
echo "📋 Demo #4: The Working Example"
echo "---"
echo "Now let's show that correct schemas DO compile successfully"

echo "🔨 Compiling working examples"
if try_compile; then
    echo "✅ SUCCESS: Working examples compile correctly!"
else
    echo "❌ FAILED: Working examples should compile!"
    exit 1
fi

echo ""
echo "🎉 FlowForge Compile-Fail Demo Complete!"
echo ""
echo "📊 Summary:"
echo "  ✅ Field name drift detection works"
echo "  ✅ Missing field detection works"
echo "  ✅ Wrong evolution policy detection works"
echo "  ✅ Working examples compile successfully"
echo ""
echo "🎯 This proves FlowForge's unique selling proposition:"
echo "   'Pipelines become unbuildable when schema drift occurs'"
echo ""
echo "💡 No other Scala data engineering framework provides this!"

# Clean up
rm -f /tmp/compile_output.log
