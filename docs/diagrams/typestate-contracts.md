# Typestate & Contracts (Mermaid + Code Pointers)

This diagram shows how compile‑time builder typestate and `SchemaConforms` evidence enforce correctness.

```mermaid
classDiagram
  class BuilderState {
    <<sealed trait>>
  }
  class Empty
  class WithContract
  class WithTransform
  class Complete
  BuilderState <|-- Empty
  BuilderState <|-- WithContract
  BuilderState <|-- WithTransform
  BuilderState <|-- Complete

  class PipelineBuilder~S,F,In,Out~ {
    +addTypedSource[C,R,P <: SchemaPolicy](src: TypedSource[R], reader: DataSource => F[C]) : PipelineBuilder~WithContract,F,Unit,C~
    +addTransform[C](Out => F[C]) : PipelineBuilder~WithTransform,F,In,C~
    +addTypedSink[R,P <: SchemaPolicy](sink: TypedSink[R], writer: (Out, DataSink) => F[Unit]) : PipelineBuilder~Complete,F,In,Out~
    +build(): Pipeline~F,In,Out~   // only when S <: Complete
  }

  PipelineBuilder --> BuilderState : typestate S enforces order
```

Code pointers
- addTypedSource requires `SchemaConforms[C, R, P]` evidence at compile time.
  - File: modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala
- addTransform requires previous state `S <:< WithContract`.
- addTypedSink requires previous state `S <:< WithTransform` and `SchemaConforms[Out, R, P]`.
- build requires `S <:< Complete`.

Sequence of a valid build

```mermaid
sequenceDiagram
  participant Dev
  participant B as PipelineBuilder[Empty, F, Unit, Unit]
  Dev->>B: addTypedSource[Producer, Contract, Policy]
  Note right of B: Requires SchemaConforms[Producer, Contract, Policy]
  B-->>Dev: PipelineBuilder[WithContract, F, Unit, Producer]
  Dev->>B: addTransform[Out => F[Out2]]
  B-->>Dev: PipelineBuilder[WithTransform, F, In, Out2]
  Dev->>B: addTypedSink[Contract2, Policy2]
  Note right of B: Requires SchemaConforms[Out2, Contract2, Policy2]
  B-->>Dev: PipelineBuilder[Complete, F, In, Out2]
  Dev->>B: build()
  B-->>Dev: Pipeline[F, In, Out2]
```

This guarantees pipelines “will not even build” if contracts or stage order are incorrect.

