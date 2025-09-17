# FlowForge 5-Minute Demo: Compile-Time Contract Guarantees

This is the **killer proof** of FlowForge's unique selling proposition in the Scala data engineering ecosystem.

## The USP: "Pipelines become unbuildable when schema drift occurs"

**No other Scala framework does this:**
- **dbt**: Runtime contract validation ❌
- **Dagster**: Runtime asset checks ❌
- **Frameless**: Type-safe operations but no contracts ❌
- **Great Expectations**: Runtime data quality ❌

**FlowForge**: Compile-time contract enforcement ✅

## Step 1: The Failing Example

Create a file `DemoContractDrift.scala` with schema drift:

```scala
import cats.effect.{ IO, IOApp }
import com.flowforge.core.types._
import com.flowforge.core.instances.EffectInstances.catsEffectSystemInstance

object DemoContractDrift extends IOApp.Simple {

  // CONTRACT: expects 'email' field
  case class UserContract(
    id: String,
    email: String,    // ← Contract expects 'email'
    age: Int
  )

  // PIPELINE OUTPUT: produces 'emailAddress' field 
  case class UserRecord(
    id: String,
    emailAddress: String,  // ❌ DRIFT: 'emailAddress' != 'email'
    age: Int
  )

  def run: IO[Unit] = {
    val pipeline = PipelineBuilder2[IO]("demo")
      .addTransform[UserRecord](_ => 
        IO.pure(UserRecord("1", "test@example.com", 25)))
      .buildWithExactContract[UserContract]  // ❌ FAILS HERE!
      
    pipeline.execute(()).void
  }
}
```

**Try to compile:**
```bash
scalac DemoContractDrift.scala
```

**Result: Beautiful compilation error:**
```
╔══════════════════════════════════════════════════════════════════════════════╗
║                     🚨 FlowForge Contract Drift Detected! 🚨                ║
║                                                                              ║
║  Pipeline output type 'UserRecord' does not match contract 'UserContract'   ║
║  under evolution policy 'Exact'.                                             ║
║                                                                              ║
║  ❌ This pipeline CANNOT be built due to schema incompatibility.             ║
║                                                                              ║
║  🔧 Common fixes:                                                            ║
║    1. Update case class fields to match contract schema                      ║
║    2. Use BackwardCompatible policy if adding fields is intentional          ║
║    3. Update the contract if schema changes are correct                      ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## Step 2: The One-Line Fix

Change line 18 from:
```scala
emailAddress: String,  // ❌ Wrong field name
```
to:
```scala
email: String,         // ✅ Correct field name  
```

**Recompile:**
```bash
scalac DemoContractDrift.scala
```

**Result: ✅ Compilation succeeds!**

## Step 3: The "Wow" Moment

This demonstrates that FlowForge:

1. **Prevents** schema drift from reaching production
2. **Catches** errors at compile time, not runtime  
3. **Guides** developers with beautiful error messages
4. **Requires** only a one-line fix to resolve

**This is our competitive advantage** - no other Scala data engineering framework provides compile-time contract guarantees with phantom types and type witnesses.

## Marketing Screenshots

The error message is **marketing gold**:
- Beautiful visual formatting ✅
- Clear problem identification ✅  
- Actionable fix suggestions ✅
- Zero runtime overhead ✅

## Integration with CI

```yaml
name: Contract Drift Prevention
on: [push, pull_request]
jobs:
  prevent-schema-drift:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Setup Scala
      uses: olafurpg/setup-scala@v10
    - name: Compile (will fail on schema drift)
      run: sbt compile
    # ✅ Pipeline only deploys if contracts match
```

## The 5-Minute Path

1. Clone FlowForge
2. Navigate to `modules/compile-fail-tests/`
3. Look at `HelloContractDrift.scala` 
4. Change 'email' to 'emailAddress'
5. Try to compile - see error
6. Change it back - compilation succeeds

**This is the "tiny repo with screenshots" proof** mentioned in the brutal truth document.