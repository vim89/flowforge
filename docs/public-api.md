# FlowForge Public API (v1.0)

## Core Public APIs
- **Core**: `com.flowforge.core.*` - Main pipeline builder and execution system
- **Contracts**: `com.flowforge.core.contracts.*` - Schema validation and policy enforcement  
- **Types**: `com.flowforge.core.types.*` - Type-safe data structures and builders
- **Main Builder**: `com.flowforge.core.PipelineBuilder` - 100% compile-time contract enforcement

## Key Features
- **100% Compile-Time Contracts**: Pipelines won't build if schema don't match
- **Phantom-State Builder**: Type system prevents incomplete pipelines  
- **Schema Policy Enforcement**: Exact, Backward, Forward compatibility policies
- **Effect-Safe**: Works with any `F[_]: EffectSystem` (IO, Task, etc.)

## Internal APIs
- **Internal**: `com.flowforge.core.internal.*` - Implementation details, not for public use