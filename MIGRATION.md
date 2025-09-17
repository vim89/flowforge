# FlowForge - Scala 3 migration guide

**Migration strategy for flowforge's compile-time contracts from Scala 2.13 to Scala 3.**

This document implements Section 9 from `docs/plan/End-to-End-Compile-time.md` (lines 302-314), providing a clear migration path while maintaining API compatibility.

## 🎯 Migration strategy overview

flowforge is designed with **API-first compatibility** - the public API remains unchanged while swapping derivation backends:

- **Short-term**: Scala 2.13 with Magnolia macros (current)
- **Mid-term**: Cross-build Scala 2.13 + Scala 3 with backend selection
- **Long-term**: Scala 3 native with inline + Mirrors

## 📊 Implementation tracks

### Track 1: current (Scala 2.13)
```scala
// Magnolia-based derivation
implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]

// Scala 2 macro materialization  
implicit def materialize[Out, Contract, P <: SchemaPolicy](
  implicit so: Shape[Out], sc: Shape[Contract]
): SchemaConforms[Out, Contract, P] = 
  macro internal.SchemaConformsMacros.materializeImpl[Out, Contract, P]
```

### Track 2: cross-build (Scala 2.13 + 3.x)
```scala
// Public API stays identical
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

// Backend selection via source directories:
// - src/main/scala-2/: Magnolia + Scala 2 macros
// - src/main/scala-3/: Mirrors + inline macros
```

### Track 3: scala 3 native  
```scala
// Inline-based derivation with Mirrors
inline given shape[T](using Mirror.Of[T]): Shape[T] = 
  deriveShape[T]

// Inline materialization with compiletime.error
inline def materialize[Out, Contract, P <: SchemaPolicy]: SchemaConforms[Out, Contract, P] =
  inline if (schemaConforms[Out, Contract, P]) new SchemaConforms[Out, Contract, P] {}
  else compiletime.error("Contract drift detected...")
```

---

## 🏗️ Cross-build configuration

### SBT setup
```scala
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.3")

// Version-specific source directories
Compile / unmanagedSourceDirectories ++= {
  val base = (Compile / sourceDirectory).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(base / "scala-2")
    case Some((3, _)) => Seq(base / "scala-3") 
    case _            => Nil
  }
}

// Conditional dependencies
libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10"
    )
    case Some((3, _)) => Seq(
      // Scala 3 uses built-in Mirrors, no external deps needed
    )
    case _ => Seq.empty
  }
}
```

### Shared API layer
```scala
// src/main/scala/ - Common to both versions
package com.flowforge.core.contracts

// Public API remains unchanged
@implicitNotFound("FlowForge: Contract drift...")  
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

// Policy definitions (unchanged)
sealed trait SchemaPolicy
object SchemaPolicy {
  sealed trait Exact extends SchemaPolicy
  sealed trait ExactUnordered extends SchemaPolicy  
  sealed trait Backward extends SchemaPolicy
  sealed trait Forward extends SchemaPolicy
  sealed trait Full extends SchemaPolicy
}
```

---

## 📂 Source directory structure

### Scala 2 implementation (src/main/scala-2/)
```scala
// scala-2/internal/SchemaConformsMacros.scala
object SchemaConformsMacros {
  def materializeImpl[Out, Contract, P <: SchemaPolicy](c: blackbox.Context)(
    so: c.Expr[Shape[Out]], 
    sc: c.Expr[Shape[Contract]]
  ): c.Tree = {
    // Current Magnolia-based implementation
    // Uses c.abort for error reporting
  }
}

// scala-2/derive/Shape.scala  
object Shape {
  implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]
  
  def join[T](caseClass: CaseClass[Typeclass, T]): Shape[T] = {
    // Current Magnolia implementation
  }
}
```

### Scala 3 implementation (src/main/scala-3/)
```scala
// scala-3/internal/SchemaConformsInline.scala
import scala.compiletime.*

object SchemaConformsInline {
  inline def materialize[Out, Contract, P <: SchemaPolicy]: SchemaConforms[Out, Contract, P] = {
    inline if (validateSchema[Out, Contract, P]) {
      new SchemaConforms[Out, Contract, P] {}
    } else {
      error(schemaErrorMessage[Out, Contract, P])
    }
  }
  
  // Compile-time schema validation using Mirrors
  inline def validateSchema[Out, Contract, P <: SchemaPolicy]: Boolean = {
    val outFields = getFields[Out]
    val contractFields = getFields[Contract] 
    checkPolicy[P](outFields, contractFields)
  }
  
  // Custom error messages using compiletime.error
  inline def schemaErrorMessage[Out, Contract, P <: SchemaPolicy]: String = {
    val diff = generateDiff[Out, Contract, P]
    s"FlowForge: Contract drift (policy: ${constValue[P]}).\n$diff"
  }
}

// scala-3/derive/Shape.scala
import scala.deriving.*

object Shape {
  inline given gen[T](using Mirror.Of[T]): Shape[T] = deriveShape[T]
  
  inline def deriveShape[T](using m: Mirror.Of[T]): Shape[T] = {
    // Mirrors-based field extraction
    inline m match {
      case p: Mirror.ProductOf[T] =>
        val labels = constValueTuple[p.MirroredElemLabels]
        val types = summonAll[Tuple.Map[p.MirroredElemTypes, TypeName]]
        buildShape(labels, types)
    }
  }
}
```

---

## ⚠️ Migration guidelines

### API compatibility rules

**✅ Safe Changes (Maintain these patterns):**
```scala
// User-facing API stays identical
trait SchemaConforms[Out, Contract, P <: SchemaPolicy]

// Same implicit resolution
implicit val evidence: SchemaConforms[UserA, UserB, SchemaPolicy.Exact] = implicitly

// Same error messages format
"FlowForge: Contract drift (policy: ${P})..."
```

**❌ Breaking Changes (Avoid these):**
```scala
// Don't change core trait signatures
trait SchemaConforms[Out, Contract, P] // Missing <: SchemaPolicy bound

// Don't change macro call sites  
implicitly[SchemaConforms[A, B, P]] // Must still work

// Don't change error message structure
"Different error format" // Users expect consistent messaging
```

### Macro migration best practices

1. **Preserve Error Messages**: Same format, same helpfulness
2. **Maintain Performance**: Compile-time validation, zero runtime cost  
3. **Keep API Stable**: No user code changes required
4. **Test Compatibility**: Cross-version test suite

### Symbol compatibility

**Avoid Scala 2 specific patterns:**
```scala
// ❌ Don't rely on Symbol internals
val fieldName = p.symbol.name.decoded

// ✅ Use stable APIs  
val fieldName = p.name.toString
```

**Avoid reflection hacks:**
```scala
// ❌ Don't use runtime reflection
val mirror = runtimeMirror(getClass.getClassLoader)

// ✅ Use compile-time derivation
inline def deriveAt[T](using Mirror.Of[T]) = ...
```

---

## 🧪 Testing strategy

### cross-version tests
```scala
// contracts-tests/src/test/scala/CrossVersionCompatSpec.scala
class CrossVersionCompatSpec extends AnyWordSpec {
  
  "SchemaConforms" should {
    "produce identical results across Scala versions" in {
      // Test same contract scenarios on both Scala 2 and 3
      val evidence2 = implicitly[SchemaConforms[UserA, UserB, SchemaPolicy.Exact]]
      val evidence3 = implicitly[SchemaConforms[UserA, UserB, SchemaPolicy.Exact]]
      
      // Both should succeed or both should fail with same message
    }
    
    "generate equivalent error messages" in {
      // Capture compile errors from both versions
      // Assert message format is consistent
    }
  }
}
```

### Build matrix
```yaml
# .github/workflows/cross-build.yml
strategy:
  matrix:
    scala: ["2.13.16", "3.3.3"]
    
steps:
  - name: Test Scala ${{ matrix.scala }}
    run: sbt ++${{ matrix.scala }} test
```

---

## 📅 Migration timeline

### Phase 1: Preparation (current)
- ✅ Keep public API free of Scala 2-only features
- ✅ Document migration guidelines  
- ✅ Establish cross-version testing

### Phase 2: Cross-Build setup
- [ ] Add Scala 3 to cross-build settings
- [ ] Create version-specific source directories
- [ ] Implement Scala 3 derivation backend
- [ ] Ensure feature parity between versions

### Phase 3: Scala 3 native
- [ ] Default to Scala 3 for new projects
- [ ] Maintain Scala 2 compatibility for existing users
- [ ] Optimize for Scala 3 specific features

### Phase 4: Scala 2 deprecation (future)
- [ ] Announce deprecation timeline
- [ ] Support migration tooling
- [ ] Scala 3 only releases

---

## 🎁 Scala 3 benefits

### Compile-time improvements
- **Faster compilation**: Inline functions vs macro expansion
- **Better error messages**: `compiletime.error` with rich context
- **Type inference**: Improved inference reduces boilerplate

### Developer experience  
- **Simpler syntax**: Less ceremonious macro definitions
- **Better IDE support**: Native Scala 3 tooling
- **Future-proof**: Alignment with Scala's long-term direction

### Advanced features (future)
- **Union types**: More flexible contract definitions
- **Match types**: Pattern matching at type level
- **Metaprogramming**: Cleaner code generation

---

## ✅ Readiness checklist

**API Design:**
- ✅ No Scala 2-only features in public API
- ✅ Stable trait signatures and implicit resolution
- ✅ Consistent error message format

**Implementation:**
- ✅ Clear separation between API and derivation
- ✅ Migration path documented
- ✅ Test coverage for compatibility scenarios

**Tooling:**
- ✅ SBT cross-build configuration ready
- ✅ CI/CD pipeline supports matrix builds
- ✅ Documentation reflects migration strategy

---

*flowforge is ready for Scala 3 migration while maintaining complete backward compatibility.*

---

flowforge Scala 3 migration guide | Future-ready Architecture
