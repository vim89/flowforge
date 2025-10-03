# FlowForge - How Data Contract Validation Fails

**A complete guide to understanding FlowForge's compile-time contract validation error messages.**

See diagrams in `docs/diagrams/compile-time-contracts/` for the derivation and evidence flow referenced here.

**✅ UPDATED**: Errors are now produced by our superior improved `TypeShape` ADT system with policy-based comparison strategies, providing cleaner, more maintainable contract validation than the previous SchemaAST approach.

This document shows exactly how each schema policy behaves with examples and **verbatim error diffs** from our improved contract system.

## 🎯 Core principle

flowforge's USP: **"Data pipelines will not even build if source or target schema do not match or align!"**

All validation happens at **compile time** with zero runtime overhead.

---

## 📊 Policy behavior matrix

| Policy | Missing Fields | Extra Fields | Type Mismatches | Field Order | Use Case |
|--------|---------------|--------------|-----------------|-------------|----------|
| **`Exact`** | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Must match | Strict compatibility |
| **`ExactUnorderedCI`** | ❌ Reject | ❌ Reject | ❌ Reject | ✅ Flexible | Case-insensitive field names |
| **`ExactOrdered`** | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Must match order | Names + order enforced |
| **`ExactOrderedCI`** | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Must match order | Case-insensitive + order |
| **`ExactByPosition`** | ❌ Reject | ❌ Reject | ❌ Reject | ✅ By position | Types match by index |
| **`Backward`** | ⚠️ Allow if Optional/Default | ✅ Allow | ❌ Reject | ✅ Flexible | Schema evolution |
| **`Forward`** | ✅ Allow | ❌ Reject | ❌ Reject | ✅ Flexible | Flexible compatibility |
| **`Full`** | ✅ Allow | ✅ Allow | ✅ Allow | ✅ Flexible | Development/testing |

---

## 🔴 Error examples by policy type

### `SchemaPolicy.Exact`

**Requirement**: Perfect field match (name, type, order)

#### Missing field error
```scala
case class User(id: Long, name: String, email: String)
case class UserPartial(id: Long, name: String)

val invalid: SchemaConforms[UserPartial, User, SchemaPolicy.Exact] = implicitly
```

**Compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact).
Out: UserPartial vs Contract: User
Missing: email:String
Extra: 
Mismatched: 
See docs/how-it-fails.md#Exact
```

#### Extra field error
```scala  
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val invalid: SchemaConforms[UserExtended, User, SchemaPolicy.Exact] = implicitly
```

**Compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact).
Out: UserExtended vs Contract: User
Missing: 
Extra: age:Int
Mismatched: 
See docs/how-it-fails.md#Exact
```

#### Type mismatch error
```scala
case class User(id: Long, name: String, email: String)  
case class UserWrongType(id: String, name: String, email: String)

val invalid: SchemaConforms[UserWrongType, User, SchemaPolicy.Exact] = implicitly
```

**Compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact).
Out: UserWrongType vs Contract: User
Missing: 
Extra: 
Mismatched: id expected Long, found String
See docs/how-it-fails.md#Exact
```

---

### `SchemaPolicy.ExactUnordered`

**Requirement**: Perfect field match but flexible order

#### Success case - Order flexible
```scala
case class User(id: Long, name: String, email: String)
case class UserReordered(name: String, id: Long, email: String) // Different order OK

val valid: SchemaConforms[UserReordered, User, SchemaPolicy.ExactUnordered] = implicitly // ✅ Works!
```

---

### Additional policies (Scala 2 backports from Scala 3 PoC)

- `SchemaPolicy.ExactOrdered`: Requires same field names and order; reordering fails.
- `SchemaPolicy.ExactUnorderedCI`: Ignores field name case; otherwise identical to `Exact`.
- `SchemaPolicy.ExactOrderedCI`: Case-insensitive names with enforced order.
- `SchemaPolicy.ExactByPosition`: Ignores names; requires types match at each position. Useful when integrating sources with unstable naming but stable positions.

---

### `SchemaPolicy.Backward`

**Requirement**: Contract fields must be in output; extra output fields allowed; missing allowed only if Optional or have defaults

#### Success - Extra fields allowed
```scala
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val valid: SchemaConforms[UserExtended, User, SchemaPolicy.Backward] = implicitly // ✅ Works!
```

#### Failure - Missing required field
```scala
case class User(id: Long, name: String, email: String) // email is required
case class UserPartial(id: Long, name: String)

val invalid: SchemaConforms[UserPartial, User, SchemaPolicy.Backward] = implicitly
```

**Compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Backward).
Out: UserPartial vs Contract: User
Missing: email:String
Extra: 
Mismatched: 
See docs/how-it-fails.md#Backward
```

---

### `SchemaPolicy.Forward`

**Requirement**: Output fields must be in contract; missing contract fields allowed

#### Success - Missing contract fields OK
```scala
case class UserFull(id: Long, name: String, email: String, age: Int)
case class User(id: Long, name: String, email: String)  

val valid: SchemaConforms[User, UserFull, SchemaPolicy.Forward] = implicitly // ✅ Works!
```

#### Failure - Extra output fields not allowed
```scala
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val invalid: SchemaConforms[UserExtended, User, SchemaPolicy.Forward] = implicitly
```

**Compile error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Forward).
Out: UserExtended vs Contract: User
Missing: 
Extra: age:Int
Mismatched: 
See docs/how-it-fails.md#Forward
```

---

### `SchemaPolicy.Full`

**Requirement**: Anything goes (escape hatch)

```scala
case class User(id: Long, name: String, email: String)
case class CompletelyDifferent(foo: String, bar: Int)

val valid: SchemaConforms[CompletelyDifferent, User, SchemaPolicy.Full] = implicitly // ✅ Always works!
```

---

*flowforge Contract validation guide | Complete reference*
