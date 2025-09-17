# FlowForge Compile-Fail Tests: Proof of Our Core USP

This module contains the **killer proof** of FlowForge's unique selling proposition:

> **"Pipelines become unbuildable when schema drift occurs"**

## What Makes This Special

No other Scala data engineering framework provides compile-time contract enforcement like this:

- **dbt**: Runtime/CI-time contract validation ❌
- **Dagster**: Runtime asset checks ❌  
- **Frameless**: Type-safe operations but no contracts ❌
- **Great Expectations**: Runtime data quality ❌

**FlowForge**: Compile-time contract drift detection ✅

## How to Use These Tests

### 1. Interactive Compile-Fail Testing

```bash
# Navigate to the module
cd modules/compile-fail-tests

# Open the test file
vim src/test/scala/com/flowforge/compilefail/ContractDriftCompileFailTests.scala

# Uncomment any of the failing test methods
# Try to compile - observe the beautiful error messages
sbt compile

# Comment the test back out
# Compilation succeeds again
```

### 2. Run the mdoc Demo

```bash
# Generate the interactive documentation
sbt compileFailTests/mdoc

# View the generated docs with compile failures
open target/mdoc/CompileFailureDemo.md
```

### 3. Screenshots for Marketing

The error messages are beautifully formatted:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                     🚨 FlowForge Contract Drift Detected! 🚨                ║
║                                                                              ║
║  Pipeline output type 'DriftedUserRecord' does not match contract           ║
║  'UserContract' under evolution policy 'Exact'.                             ║
║                                                                              ║
║  ❌ This pipeline CANNOT be built due to schema incompatibility.             ║
║                                                                              ║
║  🔧 Common fixes:                                                            ║
║    1. Update case class fields to match contract schema                      ║
║    2. Use BackwardCompatible policy if adding fields is intentional          ║
║    3. Update the contract if schema changes are correct                      ║
║                                                                              ║
║  📖 See: docs/contracts/SCHEMA_EVOLUTION.md                                  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## Test Cases Covered

### ❌ Compile-Fail Tests (Prove Our USP)

1. **Building without a sink** → Compilation failure
2. **Schema mismatch (field name drift)** → Compilation failure  
3. **Missing required fields** → Compilation failure
4. **Wrong evolution policy** → Compilation failure
5. **Unwitnessed schema evolution** → Compilation failure

### ✅ Working Examples (Show the Fix)

1. **Perfect schema match** → Compiles successfully
2. **Correct evolution policy** → Compiles successfully

## Marketing Value

This module provides:

1. **Concrete proof** of our compile-time guarantees
2. **Screenshots** of beautiful error messages
3. **Before/after examples** showing fixes
4. **Developer experience** demonstrations

## Integration with CI

Add to your CI pipeline:

```yaml
# Test that our compile-fail examples actually fail
- name: Verify compile-fail tests
  run: |
    # These should fail to compile
    sbt "compileFailTests/Test/compile" && exit 1 || echo "✅ Compile-fail tests working"
    
    # These should succeed
    sbt "examples/compile" || exit 1
```

## The "Tiny Repo with Screenshots" Demo

Following the brutal truth doc recommendation:

1. Clone FlowForge
2. Navigate to `modules/compile-fail-tests` 
3. Uncomment a failing test
4. Run `sbt compile`
5. See beautiful error message
6. Fix the schema
7. Compilation succeeds

**This is our differentiator** - no other framework in the Scala ecosystem can do this.