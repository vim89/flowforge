# Demo Runbook — Red → Green Migration (Concept‑Only)

## Goal
Show compile‑time failure under Strict policy, then relax to a migration policy and get green.

## Steps
1) Define two shapes that drift (e.g., Out missing a required field from Contract).
2) Request evidence under Strict policy → compile‑time error with path‑aware diff.
3) Switch to a migration policy (Backward or Forward) → compiles.
4) Show a compile‑fail test and a CI policy matrix snippet.

## Example (pseudocode)
```scala
// Contract expects: id: Long, email: String
case class Contract(id: Long, email: String)
// Producer missing email
case class Out(id: Long)

// Strict policy → compile‑time error (Missing attributes: email:String)
implicitly[Conforms[Out, Contract, Strict]]

// Relax policy for migration → compiles
implicitly[Conforms[Out, Contract, Backward]]
```

Notes
- Keep the error text visible onscreen (screenshot backup).
- Narrate why Backward/Forward is safe and how to sunset back to Strict.

