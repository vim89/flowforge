# Field vs Element Optionality (Compile-Time Contracts)

```mermaid
flowchart TD
  A[Case class field: Option[A]] -->|FieldShape.isOptional = true| B[FieldShape(name, shape(A), hasDefault, isOptional=true)]
  C[List of Optional: List[Option[A]]] -->|Element optionality| D[SequenceShape(OptionalShape(shape(A)))]

  B --> E[StructShape([... FieldShape ...])]
  D --> F[List[Optional[A]]]
```

- Field optionality (case class parameter of type `Option[A]`):
  - Represented on the field node as `FieldShape.isOptional = true`.
  - Inner type is kept as `shape(A)` (no `OptionalShape` inside the field).
- Element optionality (e.g., `List[Option[A]]`, `Map[K, Option[V]]`):
  - Represented as `OptionalShape(shape(A))` nested within `SequenceShape`/`MapShape`.

Why it matters
- Under `Exact`/`ExactUnordered`, `List[Option[A]]` differs from `List[A]` at compile time.
- This prevents silently dropping optionality in nested positions.

Related files
- Macro shape builder: `modules/core/.../contracts/internal/ContractMacros.scala`
- Shapes ADT: `modules/core/.../contracts/internal/SchemaAST.scala`
- Examples: `docs/how-it-fails.md` (nested optionality) and compile-fail suite.

