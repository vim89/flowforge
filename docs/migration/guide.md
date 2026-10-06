# FlowForge - Scala 3 Migration Guide

Plan for moving FlowForge's compile-time contracts from Scala 2.13 to Scala 3.

> Status: part plan, part record. `core` now cross-builds Scala 2.13.16 and 3.3.3, with the Scala 3
> derivation in `modules/core/src/main/scala-3`, and CI runs `core/testQuick` on 3.3.3. Every other
> module is 2.13.16 only. Snippets below that are marked proposed are still proposals. Readiness for
> v1.0 is tracked in [v1.0 readiness](../plan/v1.0-readiness.md).

## Starting point

- Today: Scala 2.13 with the TypeShape ADT and policy-based comparison, derived by Magnolia macros.
- Blocker: the Spark Scala 3 artifacts the engines need do not exist, so a cross-build cannot be
  switched on for the modules that matter.

## What is already in place

The 2.13 implementation the migration starts from:

```scala
// CURRENT: Superior TypeShape ADT (replaced old SchemaAST)
sealed trait TypeShape
object TypeShape {
  final case class PrimitiveShape(name: String) extends TypeShape
  final case class SequenceShape(elem: TypeShape) extends TypeShape
  final case class MapShape(key: PrimitiveShape, value: TypeShape) extends TypeShape
  final case class FieldShape(name: String, shape: TypeShape, hasDefault: Boolean, isOptional: Boolean) extends TypeShape
  final case class StructShape(fields: List[FieldShape]) extends TypeShape
}

// Policy-based comparison strategies (improved)
def compareShapes(path: String, out: TypeShape, contract: TypeShape, policy: PolicyType): (Missing, Extra, Mismatches)

// Clean macro implementation with proper error messages
implicit def materialize[Out, Contract, P <: SchemaPolicy]: SchemaConforms[Out, Contract, P] =
  macro internal.ContractMacros.conformsImpl[Out, Contract, P]
```

### What this buys the migration

1. The shape of a type is an immutable ADT, so the Scala 3 port replaces only how a shape is
   derived, not how two shapes are compared.
2. Policy comparison is plain data in, plain data out, so it is shared source across both versions.
3. Error message text is produced from the comparison result, so the message can stay identical.

### Cross-build settings

`core` carries these settings today. The snippet is the shape of them, not a quote of `build.sbt`:

```scala
// Cross-build configuration (build.sbt)
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.3")

// Version-specific dependencies ready
libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq("com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10")
    case Some((3, _)) => Seq() // Built-in Mirrors
  }
}
```

---

## 🏗️ Cross-build configuration

The full set of settings the cross-build uses. `core` has them; no other module does.

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

`ci.yml` has no separate cross-build workflow. Its `test` matrix carries one Scala 3 entry, which
runs `core/testQuick` on Linux.

```yaml
# Proposed .github/workflows/cross-build.yml
strategy:
  matrix:
    scala: ["2.13.16", "3.3.3"]
    
steps:
  - name: Test Scala ${{ matrix.scala }}
    run: sbt ++${{ matrix.scala }} test
```

---

## 📅 Migration timeline

### Phase 1: 2.13 implementation (done)
- TypeShape ADT and policy-based comparison, derived by Magnolia macros
- Policy behaviour covered by the tests in `modules/compile-fail-tests`

### Phase 2: cross-build infrastructure (done for core)
- Scala 3 is in `core`'s `crossScalaVersions`, with version-specific source directories
- The Scala 3 derivation uses quotes reflection, and CI tests `core` on 3.3.3

### Phase 3: activation (not started)
- Cross-publish core and contracts, then the engines once Spark ships Scala 3 artifacts

### 🔮 Phase 4: Future Enhancement
- 📦 Union types for flexible contract definitions
- 📦 Match types for advanced type-level patterns
- 📦 Enhanced metaprogramming capabilities

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

## Current status

- `core` builds and tests on both 2.13.16 and 3.3.3. Every other module is 2.13.16, except
  `experimental`, which is Scala 3 only, opt-in and not published.
- The contract system works on both: shape derivation, policy comparison and the compile error
  message are in place, with the policy cases covered in `modules/compile-fail-tests`.
- Nothing is cross-published yet, so the Scala 3 build is something you get from source.
- Readiness statements live in one place: [v1.0 readiness](../plan/v1.0-readiness.md).
