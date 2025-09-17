# FlowForge Architecture (Mermaid)

```mermaid
flowchart TD
  subgraph App[User App]
    APP1[Contract Types]
    APP2[Pipeline Construction]
  end
  subgraph Core
    PB[PipelineBuilder]
    P[Pipeline (Kleisli)]
    DA[DataAlgebra]
    ES[EffectSystem[F]]
    FR[FlowforgeResource[F,_]]
  end
  subgraph Engines
    SPK[Spark Engine]
    FLK[Flink Engine]
  end
  subgraph Connectors
    S3[S3]
    GCS[GCS]
    JDBC[JDBC]
    KAF[Kafka]
  end
  subgraph Quality
    NAT[Native Checks]
    DEQ[Deequ Adapter]
  end
  subgraph Lineage
    OL[OpenLineage Emitter]
  end

  App --> PB --> P --> DA
  ES -.-> FR
  P --> NAT
  P --> DEQ
  P --> OL
  DA --> SPK
  DA --> FLK
  DA --> S3
  DA --> GCS
  DA --> JDBC
  DA --> KAF
```
