# Typestate Builder - Slide Source (Export to PNG)

```mermaid
flowchart LR
  Empty((Empty)) --> WithContract[[WithContract]]
  WithContract --> WithTransform[[WithTransform]]
  WithTransform --> Complete[[Complete]]

  style Empty fill:#eef,stroke:#446
  style WithContract fill:#eef,stroke:#446
  style WithTransform fill:#eef,stroke:#446
  style Complete fill:#cfe,stroke:#284
```

Annotations
- build() only from Complete
- addSource → addTransform → addSink → build
- Illegal states are unrepresentable; incomplete graphs never build

Export this diagram to PNG and use as the typestate slide.

