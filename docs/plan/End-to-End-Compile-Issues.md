Issues faced when implementing as per plan `docs/plan/End-to-End-Compile-time.md`

You’re hitting two *separate* issues. Here’s exactly how to fix both.

---

## 1) Shape derivation error (“needs `join`”)

With **Magnolia 1.x on Scala 2**, your typeclass companion must implement `join` (for case classes) and `split` (for sealed traits) on `magnolia1.Derivation`. Without those, `Magnolia.gen[T]` won’t work. ([GitHub][1], [jdriven.com][2])

**Minimal, correct Shape for Scala 2.13 + Magnolia 1.x:**

```scala
import magnolia1._
import scala.language.experimental.macros

final case class Field(name: String, tpe: String, hasDefault: Boolean, isOptional: Boolean)
trait Shape[T] { def fields: List[Field] }

object Shape extends Derivation[Shape] {
  type Typeclass[T] = Shape[T]

  def join[T](ctx: CaseClass[Typeclass, T]): Typeclass[T] =
    new Shape[T] {
      val fields: List[Field] =
        ctx.parameters.toList.map { p =>
          val full = p.typeName.full // Magnolia 1 (Scala 2) exposes typeName, not typeInfo
          Field(
            name       = p.label,
            tpe        = full,
            hasDefault = p.default.isDefined,
            isOptional = full.startsWith("scala.Option[")
          )
        }
    }

  // Keep v1.0 scope to products only; abort on sums for now
  def split[T](ctx: SealedTrait[Typeclass, T]): Typeclass[T] =
    ctx.abort(s"Shape supports case classes only for now (got sum type ${ctx.typeName.full})")

  implicit def gen[T]: Shape[T] = macro Magnolia.gen[T]
}
```

Key details:

* Import `magnolia1._`, not the Scala-3 API.
* Use `Param.typeName.full` (not `typeInfo`). ([javadoc.io][3])

---

## 2) Macro error (“issues with `eval`”)

`c.eval` only works if the expression is **fully static/pure** at compile time. If your `Shape` value or its `fields` method isn’t a stable, evaluable expression, `c.eval` will blow up. The safer path is to **avoid `c.eval` entirely** and compute field info from types using Scala reflection inside the macro. ([Scala Documentation][4], [Stack Overflow][5])

### Recommended fix (drop `c.eval`)

Inside `SchemaConformsMacros.materializeImpl`, replace your `evalFields(...)` that calls `c.eval` with a pure reflection helper:

```scala
import c.universe._

def fieldsOf(tpe: c.Type): List[(String, String, Boolean, Boolean)] = {
  val cls = tpe.typeSymbol.asClass
  if (!cls.isCaseClass)
    c.abort(c.enclosingPosition, s"Contracts must be case classes; got $tpe")

  val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
  val params = ctor.paramLists.headOption.getOrElse(Nil)

  params.map { p =>
    val term = p.asTerm
    val pt   = term.typeSignatureIn(tpe).dealias
    val full = pt.toString // or a custom pretty-printer for FQNs
    val hasDefault = term.isParamWithDefault
    val isOpt = pt.typeConstructor =:= typeOf[Option[_]].typeConstructor
    (term.name.decodedName.toString, full, hasDefault, isOpt)
  }
}

val outF = fieldsOf(weakTypeOf[Out])
val conF = fieldsOf(weakTypeOf[Contract])
```

Notes:

* `isParamWithDefault` and `isCaseAccessor`/constructor params are available via Scala reflection. ([kenji yoshida][6], [Scala][7])
* Use `pt.dealias` and either `toString` or your own fully-qualified name renderer for stable type printing.

Then keep your **policy checks** as you already wrote (Exact, ExactUnordered, Backward, Forward). No `c.eval` needed.

### If you insist on keeping `c.eval`

Make all of these true, or it will still fail:

* `Shape[T].fields` is a **`val`** (not a `def`) so it’s a constant value.
* The tree you evaluate is from a **stable path** (no local captures).
* You construct `c.Expr[List[Field]](q"${shapeExpr.tree}.fields")` exactly, where `shapeExpr` is an implicit you just **summoned**.
  Even then, `c.eval` is brittle across build tools/REPL, so reflection is the robust choice. ([Scala Documentation][4])

---

### Quick checklist to get you unblocked

1. In `Shape.scala`:

    * `object Shape extends Derivation[Shape] { def join; def split; implicit def gen = Magnolia.gen }` (as above). ([GitHub][1])
    * Use `p.typeName.full`. ([javadoc.io][3])

2. In `SchemaConformsMacros.scala`:

    * **Remove** `c.eval(...)`.
    * Add `fieldsOf(weakTypeOf[T])` using reflection (`isParamWithDefault`, `Option[_]` detection). ([kenji yoshida][6])
    * Run policy checks on `(name, type)` pairs; `abort` with concise diffs.

This pair of changes resolves the “need `join`” error **and** the macro `eval` blow-ups, while keeping you fully on Scala 2.13 today and future-proof for a Scala 3 backend later (where you’ll replace reflection with Mirrors and `compiletime.error`). ([Scala Documentation][8])

If you paste your current `Shape.scala` and the failing lines from the macro, I’ll tailor the exact diff to your tree.

[1]: https://github.com/softwaremill/magnolia "softwaremill/magnolia: Easy, fast, transparent generic ..."
[2]: https://jdriven.com/blog/2022/11/Generic-Derivation-Comparison "Generic Derivation - a comparison"
[3]: https://javadoc.io/doc/com.propensive/magnolia_2.13/0.17.0/magnolia/CaseClass.html "CaseClass - magnolia_2.13 0.17.0 javadoc"
[4]: https://docs.scala-lang.org/overviews/macros/overview.html "Def Macros"
[5]: https://stackoverflow.com/questions/56935330/scala-what-can-code-in-context-eval-reference "macros - Scala: what can code in Context.eval reference?"
[6]: https://xuwei-k.github.io/scala-compiler-sxr/scala-compiler-2.10.0/scala/reflect/api/Symbols.scala.html "scala/reflect/api/Symbols.scala"
[7]: https://www.scala-lang.org/api/2.12.8/scala-reflect/scala/reflect/api/Symbols%24TermSymbol.html "scala.reflect.api.Symbols.TermSymbol"
[8]: https://docs.scala-lang.org/scala3/reference/contextual/derivation.html "Type Class Derivation"
