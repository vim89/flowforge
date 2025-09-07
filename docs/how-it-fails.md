# FlowForge - How Contract Validation Fails

**A complete guide to understanding FlowForge's compile-time contract validation error messages.**

This document shows exactly how each schema policy behaves with examples and **verbatim error diffs** as specified in the End-to-End Compile-time plan.

## 🎯 Core Principle

FlowForge's USP: **"Data pipelines will not even build if source or target schema do not match or align!"**

All validation happens at **compile time** with zero runtime overhead.

---

## 📊 Policy Behavior Matrix

| Policy | Missing Fields | Extra Fields | Type Mismatches | Field Order | Use Case |
|--------|---------------|--------------|-----------------|-------------|----------|
| **`Exact`** | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Must match | Strict compatibility |
| **`ExactUnordered`** | ❌ Reject | ❌ Reject | ❌ Reject | ✅ Flexible | Field order flexible |
| **`Backward`** | ⚠️ Allow if Optional/Default | ✅ Allow | ❌ Reject | ✅ Flexible | Schema evolution |
| **`Forward`** | ✅ Allow | ❌ Reject | ❌ Reject | ✅ Flexible | Flexible compatibility |
| **`Full`** | ✅ Allow | ✅ Allow | ✅ Allow | ✅ Flexible | Development/testing |

---

## 🔴 Error Examples by Policy Type

### `SchemaPolicy.Exact`

**Requirement**: Perfect field match (name, type, order)

#### Missing Field Error
```scala
case class User(id: Long, name: String, email: String)
case class UserPartial(id: Long, name: String)

val invalid: SchemaConforms[UserPartial, User, SchemaPolicy.Exact] = implicitly
```

**Compile Error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact).
Out: UserPartial vs Contract: User
Missing: email:String
Extra: 
Mismatched: 
See docs/how-it-fails.md#Exact
```

#### Extra Field Error
```scala  
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val invalid: SchemaConforms[UserExtended, User, SchemaPolicy.Exact] = implicitly
```

**Compile Error:**
```
FlowForge: Contract drift (policy: SchemaPolicy.Exact).
Out: UserExtended vs Contract: User
Missing: 
Extra: age:Int
Mismatched: 
See docs/how-it-fails.md#Exact
```

#### Type Mismatch Error
```scala
case class User(id: Long, name: String, email: String)  
case class UserWrongType(id: String, name: String, email: String)

val invalid: SchemaConforms[UserWrongType, User, SchemaPolicy.Exact] = implicitly
```

**Compile Error:**
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

#### Success Case - Order Flexible
```scala
case class User(id: Long, name: String, email: String)
case class UserReordered(name: String, id: Long, email: String) // Different order OK

val valid: SchemaConforms[UserReordered, User, SchemaPolicy.ExactUnordered] = implicitly // ✅ Works!
```

---

### `SchemaPolicy.Backward`

**Requirement**: Contract fields must be in output; extra output fields allowed; missing allowed only if Optional or have defaults

#### Success - Extra Fields Allowed
```scala
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val valid: SchemaConforms[UserExtended, User, SchemaPolicy.Backward] = implicitly // ✅ Works!
```

#### Failure - Missing Required Field
```scala
case class User(id: Long, name: String, email: String) // email is required
case class UserPartial(id: Long, name: String)

val invalid: SchemaConforms[UserPartial, User, SchemaPolicy.Backward] = implicitly
```

**Compile Error:**
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

#### Success - Missing Contract Fields OK
```scala
case class UserFull(id: Long, name: String, email: String, age: Int)
case class User(id: Long, name: String, email: String)  

val valid: SchemaConforms[User, UserFull, SchemaPolicy.Forward] = implicitly // ✅ Works!
```

#### Failure - Extra Output Fields Not Allowed
```scala
case class User(id: Long, name: String, email: String)
case class UserExtended(id: Long, name: String, email: String, age: Int)

val invalid: SchemaConforms[UserExtended, User, SchemaPolicy.Forward] = implicitly
```

**Compile Error:**
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

*Generated: 2025-09-07 | FlowForge Contract Validation Guide | Complete Reference*
