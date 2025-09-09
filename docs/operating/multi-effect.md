# Selecting an Effect System

FlowForge supports both Cats Effect and ZIO. The choice is entirely a build-time decision:

1. **Add the dependency** for the desired effect library.
2. **Import the instance** from `com.flowforge.core.instances.EffectInstances`.
3. The compiler resolves the appropriate implicit `EffectSystem` for the chosen effect type.

```scala
import com.flowforge.core.instances.EffectInstances._

// Uses Cats Effect
val cats: EffectSystem[IO] = EffectSystem[IO]

// Uses ZIO
val zio: EffectSystem[Task] = EffectSystem[Task]
```

Switching between implementations requires only changing the effect type and dependency. Pipeline code remains unchanged.
