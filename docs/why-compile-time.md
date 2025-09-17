# Why compile-time contracts?
- dbt contracts validate at build/engine/CI time; FlowForge validates at **Scala compile time**.
- Dagster asset checks validate at runtime/CI; FlowForge refuses to build illegal pipelines.
- Result: drift is caught before packaging or deploy.

- See also: common flowchart and UML in `docs/diagrams/compile-time-contracts/`.
