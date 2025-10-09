# Compile‑Time Contracts - Common Flowchart

```mermaid
flowchart TD
    A[Developer writes types]
    A -->|Producer record| A1[Out record]
    A -->|Contract type| A2[Contract type]
    A -->|Policy| A3[SchemaPolicy]

    subgraph B[Typed Edges]
      B1[TypedSource[Out]]
      B2[TypedSink[Out]]
      B1 -->|requires| E((Evidence))
      B2 -->|requires| E((Evidence))
    end

    A1 --> B
    A2 --> B
    A3 --> B

    E -->|typeclass| D1[[SchemaConforms]]:::evidence

    classDef evidence fill:#f7faff,stroke:#3b82f6,stroke-width:1.2px

    subgraph C[Derivation Facade]
      direction TB
      C0[Derivation (facade)]
      C0 -->|Scala 2| C1[Backend: Magnolia + Macro]
      C0 -->|Scala 3| C2[Backend: Quotes Macro]
    end

    D1 --> C

    subgraph S2[Scala 2 - Magnolia]
      direction TB
      M1[Shape(Out)] --> M3[Build SchemaAST]
      M2[Shape(Contract)] --> M3
      M3 --> M4[Compare under Policy]
      M4 -->|match| M5((Emit evidence))
      M4 -->|diff| M6{{Abort with diff}}
    end

    subgraph S3[Scala 3 - Quotes]
      direction TB
      G1[Type inspection Out] --> G3[Build SchemaAST]
      G2[Type inspection Contract] --> G3
      G3 --> G4[Compare under Policy]
      G4 -->|match| G5((Emit evidence))
      G4 -->|diff| G6{{Abort with diff}}
    end

    C1 --> S2
    C2 --> S3

    M5 --> E
    G5 --> E

    subgraph R[Runtime]
      direction TB
      R1[No overhead] --> R2[Optional runtime guards]
      R2 --> R3[Quality rules]
    end

    B --> R
```

Notes

- Evidence is materialized at compile time. If shapes don’t conform under the selected policy, compilation aborts with a readable diff that pinpoints fields and paths.
- The Derivation facade lets us swap the backend (Magnolia or Quotes-based macros; Mirrors optional) without changing public APIs on typed edges.

Legend and glossary (read me first)

- Evidence: a compile-time proof the compiler must find. Here it is the typeclass instance SchemaConforms[Out, Contract, P]. If it cannot be built, your code won’t compile.
- SchemaConforms: the typeclass that says “Out conforms to Contract under Policy P”. The macro builds it or aborts.
- Shape: a compile-time description of a type’s structure (field names, types, optional/default). Used to build SchemaAST.
- SchemaAST: a normalized tree for types (Record, Field, Primitive, Option, Array, Map) so we can do deep, recursive comparisons.
- Policy: rules that define what “conforms” means (Exact, Backward, Forward, ExactOrdered, ExactUnordered, ExactByPosition, Full).
- Derivation Facade: small indirection that chooses the backend (Scala 2 Magnolia + macro vs Scala 3 quotes macro) without changing callers.
- Reflection (Scala 2 macro) and Quotes reflection (Scala 3, Mirrors optional): compile-time introspection to read types and build SchemaAST.

Design and patterns in this diagram

- Strategy/Bridge: Derivation facade delegates to a backend (Magnolia vs Mirrors) - easy to swap.
- Typestate/Phantom Builder (outside this diagram, in PipelineBuilder): compile-time states ensure you can only build complete pipelines.
- Typeclass pattern: SchemaConforms is a typeclass; the compiler tries to synthesize an instance.
- ADTs: SchemaPolicy and SchemaAST are algebraic data types for closed, explicit modeling.
- Functional error model: compile-time failure; optional runtime checks use ValidatedNel at the edges.
