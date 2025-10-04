# Repository guidelines

## Project structure & Module organization

- Multi‑module SBT repo: sources in `modules/*`, tests under `src/test/scala` mirroring packages.
- Key modules: `core` (algebras, types, builders), `contracts` (typed contracts), `engines-spark`, `connectors`,
  `quality`, `infrastructure`; CLIs: `validation-cli`, `contracts-extractor-cli`.
- Docs: ADRs `docs/adr/*` (decisions), Plans/Evidence in `docs/plan` and `docs/evidence`, Developer Handbook in
  `docs/contributing`.

## Build, test, and development commands

- `sbt compile` — compile all modules; `sbt test` — run all tests (non‑parallel).
- Formatting: `sbt fmt` / `sbt fmtCheck`; Linting: `sbt fix` / `sbt fixCheck`.
- Focused runs: `sbt core/test`, `sbt engines-spark/compile`.
- CLIs: `sbt validation-cli/run` and `sbt contracts-extractor-cli/run`.

## Coding style & naming conventions

- Scala 2.13; idiomatic FP: immutability, pure functions, explicit effects, use for-comprehensions, Monads instead of
  try-catch-finally,
- Keep Spark transforms pure (return `Dataset[...]`), move external IO/orchestration to `F[_]` via `EffectSystem`.
- Prefer typed contracts (`TypedSource/TypedSink/PipelineBuilder2` + `SchemaEq`), use `ValidatedNel` for multi‑rule DQ.
- Naming: `camelCase` vals/defs, `PascalCase` types, lowercase packages; avoid one‑letter names.
- Potential & power of Scala ecosystem: We must us potential & power of Scala ecosystem but not over-engineer it. All
  possible USPs of Scala
- Write idiomatic Scala code: We must write idiomatic Scala code, not Java in Scala. We must use Scala to its fullest
  potential.
- Purely Functional: Emphasizing pure functions and immutability to create predictable and maintainable code.
- Immutability: All data structures are immutable by default, ensuring thread safety and predictability.
- First-Class Functions: Functions are treated as first-class citizens, allowing them to be passed as arguments,
  returned from other functions, and assigned to variables.
- Higher-Order Functions: Functions that take other functions as parameters or return them as results, enabling powerful
  abstractions and code reuse.
- Pure Functions: Functions that always produce the same output for the same input and have no side effects, making them
  easier to reason about and test.
- Function Composition: Combining simple functions to build more complex ones, promoting modularity and code reuse.
- Pattern Matching: A mechanism for checking a value against a pattern, allowing for concise and expressive handling of
  different data structures.
- Concurrency with Futures: Using Scala's Future to handle asynchronous computations, enabling non-blocking and
  concurrent programming.
- Error Handling with Either and Try: Using Either and Try to represent computations that may fail, providing a
  functional approach to error handling.
- Implicits: Using implicit parameters and conversions to enable type class instances and enhance code readability.
- Monads: Abstractions that represent computations as a series of steps, enabling chaining of operations while managing
  side effects (e.g., Option, Either, Future).
- Type Classes: A way to define generic interfaces that can be implemented for different types, enabling ad-hoc
  polymorphism and code reuse.
- Algebraic Data Types (ADTs): Composite types formed by combining other types, such as case classes and sealed traits,
  allowing for expressive modeling of data.
- Referential Transparency: The property that an expression can be replaced with its corresponding value without
  changing the program's behavior, facilitating reasoning about code.
- Lazy Evaluation: Deferring computation until the result is needed, which can improve performance and enable the
  creation of infinite data structures.
- Tail Recursion: A special case of recursion where the recursive call is the last operation in a function, allowing for
  optimization and preventing stack overflow.
- Type Safety 100 %: Leveraging Scala's strong static type system to catch errors at compile. Builder Pattern with Type
  Safety.
- Complete Monadic Ecosystem: Utilizing libraries like Cats and Scalaz to work with monads, functors, and applicatives
  for elegant handling of side effects and asynchronous computations.
- Convention over Configuration with Functional Purity: Emphasizing sensible defaults and reducing boilerplate while
  maintaining pure functions and immutability.
- Effect Systems: Developer Choice Architecture -
    - ZIO OR Cats-Effect
        - Unified abstraction in flowforge enables switching without code changes
        - Template generation chooses effect system upfront
        - Clean separation prevents dependency conflicts
        - Type-safe resource management with automatic cleanup
        - Fiber-safe concurrency for parallel processing
        - Comprehensive ecosystem for functional programming
        - Interoperability with existing libraries and frameworks
        - Decorator Pattern with Effect Systems: Using the decorator pattern to add additional behavior to existing
          components in a type-safe manner, while leveraging the capabilities of effect systems like ZIO or Cats Effect
          to manage side effects and resource safety. time, ensuring robust and reliable applications.
- Phantom Types: Using phantom types to encode additional type information at compile time without affecting runtime
  representation, enhancing type safety and expressiveness.
- Type-Level Programming: Leveraging Scala's advanced type system to perform computations and enforce
- F-Bounded Polymorphism: Using F-bounded polymorphism to define type hierarchies where a type parameter is constrained
  to be a subtype of a specific type, enabling more precise typing and code reuse. For Type-Safe Composition
- Kleisli Arrows: Utilizing Kleisli arrows for Composable Transformations and to represent computations that produce
  monadic values, allowing for elegant composition of effectful functions and enabling a functional approach to building
  data pipelines.
- Kleisli for effectful stages: Kleisli composes effectful functions nicely. Each stage of the data pipeline (e.g.,
  extraction, transformation, loading) can be represented as a Kleisli arrow, allowing for seamless composition of
  effectful operations while maintaining type safety and clarity.
- Phantom-Type Builder Patterns: Using phantom types in builder patterns to enforce correct construction of complex
  objects at compile time, preventing invalid states and ensuring that all required parameters are provided before
  building the final object. Phantom types track the current output type of stages. Using a phantom-type builder pattern
  to enforce correct construction of complex objects at compile time, ensuring that all required parameters are provided
  and valid before the object can be instantiated. Phantom types are markers used only by the compiler; they don’t exist
  at runtime, but enforce rules at compile time. In a builder, each added stage updates the phantom Out type. Trying to
  build before types align fails to compile, not crash. They exist exclusively at compile time and carry extra
  information that enables the compiler to enforce rules. Phantom Types provide extra information to the compiler… allow
  extra constraints… program fails to compile if constraints don’t hold ...
- Higher-Kinded Type: Employing higher-kinded types to define abstractions that can operate on type constructors,
  enabling the creation of generic and reusable components that work with various data structures and effect types.
- Tagless Final Encoding: Adopting the tagless final encoding pattern to define type-safe and extensible algebras for
  domain-specific languages, allowing for flexible interpretation and composition of operations without relying on
  concrete data types.
- Free Monads: Using free monads to represent computations as a series of steps, enabling the separation of program
  description from execution and facilitating the creation of interpreters for different execution strategies.
- Type Classes: Leveraging type classes to define generic interfaces that can be implemented for different types,
  enabling ad-hoc polymorphism and code reuse across various data structures and effect types. Adapter Pattern with Type
  Classes.
- Type class patterns: Using type class patterns to define and implement generic behaviors for different types, allowing
  for flexible and reusable code that can work with various data structures and effect types.
- Self Types: Utilizing self types to express dependencies between traits, enabling more precise typing and ensuring
  that certain traits can only be mixed into classes that also extend specific other traits.
- Structural types: Employing structural types to define types based on their members rather than their explicit names,
  allowing for more flexible and dynamic typing in certain scenarios.
- Tagless Final for Effect Abstraction: Using the tagless final pattern to abstract over different effect types,
  enabling the creation of generic and reusable components that can work with various effect systems (e.g., Cats Effect,
  ZIO) without being tied to a specific implementation. Strategy Pattern with Tagless Final.
- Observer Design Pattern with Reactive Streams: fs2. Implementing the observer design pattern using reactive streams (
  e.g., Akka Streams, FS2) to enable asynchronous and event-driven data processing, allowing components to react to
  changes in data and propagate updates through the system in a non-blocking manner.
- Cats Monads - Higher Kinded Type class
- for-comprehensions are not iterations. Step away from the concept of iterations.
- FlatMap is mental model for chained transformations.
- Cats Monads Use cases: List Combinations, Option transformations, Asynchronous chained computations, Dependent
  computations, Cats Monad Transformers: Higher-Kinded-Types for convenience over nested monadic values. OptionT,
  EitherT, Cats Data Manipulation: Readers, Writers, Evaluations, State, Data Validations.
- Be mindful of -
    - No Over Engineering - Keep simplicity, scalable, understandable, adaptable yet creative - We need to be creative,
      innovative & something like Wow such a thing can be achieved who'd have never thought about such things in Data
      Engineering Data Pipelines.
    - The polymorphic effects of Cats-Effect already describe any effect type, and you can use ZIO Tasks instead of IO
      as the implementation of F - polymorphism lets me slot ZIO Task in F[_]
    - We can also have typed error channel, the entire type class hierarchy starts all the way from Cats, as MonadError[
      F[_], E]. Usually the error channel is Throwable and makes things easier, and you can have an entire error model
      starting from Throwable
    - You know why typed error channels haven’t made a difference yet - unless you model errors explicitly, they’re more
      effort than payoff - but ZIO’s typed channel is powerful when you start encoding domain-specific failures into
      your effects. Moreover, it forces clarity about what errors you can and should recover from, and ZIO even supports
      rich handling like folding, retries, and transforming failures with full type safety.
    - Scala has both Functional Programming and Object Oriented design elements - for the double dispatch pattern - the
      choice is clear - it is going to be ADTs... (pattern matching)
    - ADTs is the way to go - it is so powerful - that we can easily do triple dispatch (and more if we need) - the SUM
      and PRODUCT types etc.
    - For single dispatch though - there are some times when modelling via inheritance is suggested - if the hierarchy
      is volatile (subclasses are getting added/removed frequently) If it is largely static - ADTs are the way to go -
      ADTs are also non-intrusive - we don't have to touch the actual class definition - instead we can attach the
      behavior from outside.

### 🏛 SOLID principles implementation

Use all SOLID principles where applicable

- S - Single Responsibility Principle
- O - Open/Closed Principle
- L - Liskov Substitution Principle
- I - Interface Segregation Principle
- D - Dependency Inversion Principle - Depend on abstractions, not concretions

### 🏗 Design patterns

- Creational Patterns: Use all creational patterns where applicable
- Structural Patterns: Use all structural patterns where applicable
- Behavioral Patterns: Use all behavioral patterns where applicable

### Prototype integration principles

#### Existing prototype implementation references
- Utilities - Data Engineering Reusable functions SDK: https://github.com/vim89/reference-utilities/tree/main/src/main/scala/com/vim/de/utils
- Archetype - Data pipeline Codegen Template: https://github.com/vim89/reference-archetype

#### When integrating external patterns or libraries into FlowForge
- Adapter Pattern with Type Classes: Wrap imperative APIs in functional type class interfaces
- Compatibility Layers: Create functional bridges that preserve existing interfaces while adding type safety
- Effect-Safe Resource Management: All external integrations must use Resource[F, _] for cleanup
- Validation Aggregation: Multi-error scenarios use ValidatedNel, never fail-fast exceptions
- Kleisli Integration: External operations should be composable via Kleisli arrows where possible
- Example pattern for integrating imperative utilities:

```scala 
// DON'T: Direct imperative integration
def badIntegration(): Map[String, String] = externalLibrary.getConfig()

// DO: Functional wrapper with effect safety
trait ConfigurationAlgebra[F[_]] {
  def loadConfig[T: ConfigDecoder](key: String): F[ValidatedNel[ConfigError, T]]
}

// Compatibility layer preserving old interface
trait LegacyCompatibility[F[_] : Sync] extends ConfigurationAlgebra[F] {
  def getLegacyConfig(key: String): F[Option[Map[String, String]]] // Preserve existing API

  def adaptToTyped[T: ConfigDecoder](config: Map[String, String]): F[ValidatedNel[ConfigError, T]]
}
```

### Advanced Type-Level programming patterns
- Phantom State Machines: Use phantom types to encode valid state transitions at compile time
- Dependent Types with Refinement: Combine refined types with phantom types for maximum safety
- Type-Level Validation: Configuration and template validation should happen at compile time
- Effect Channel Modeling: Use MonadError[F[_], E] with domain-specific error ADTs for typed error handling
- Simplify Type Hierarchies: Remove intermediate traits that add no value
- Resource Management Patterns
- Bracket Everything: All resource acquisition must use bracket patterns or Resource[F, _]
- Compositional Resource Safety: Resources should compose via Resource.flatMap and Resource.parTupled
- Cloud Connector Safety: Multi-cloud operations require automatic connection cleanup
- Stream Resource Management: fs2.Stream operations must properly handle resource lifecycle

### Template generation philosophy
- Functional Template Generation: Templates use effect systems, not imperative file operations
- Phantom-Type Builders: Template construction prevents invalid states at compile time
- Validation Before Generation: Use ValidatedNel to collect all template errors before failing
- Resource-Safe Generation: File operations must use Resource[F, _] with proper cleanup
- Template Focus: Concentrate on 80/20 use cases instead of trying to handle every edge case

## Testing guidelines
- Frameworks: ScalaTest (+ property/law tests), optional ZIO Test; scoverage target ≥ 80% on changed code.
- Conventions: name specs `*Spec.scala`; place fixtures under the same package path in `src/test/scala`.
- Strategy: test algebras/instances first; integration/engine tests opt‑in and minimal.


### File structure guidance:
- A class and its companion should live together in the same file. 
- sealed traits and their subtypes must also be in the same file for compiler safety.
- File names usually match the main type they hold.
- Multi-type grouping: When types are closely related, grouping is permitted—but only when justified. The file should then use meaningful lowerCamelCase names. 
- Developer ergonomics: Having one type per file helps with navigation, findability, and code review. It avoids confusion and reduces search friction.

## Commit & Pull Request guidelines

- Commits: imperative subject (≤72 chars) + concise body (what/why); reference ADRs (e.g., ADR‑012 for effect rules).
- PR checklist: `sbt fmt` + `sbt compile; test:compile` green; link checker passing; description, test plan, and
  any CLI logs.
- Scope: keep PRs focused; avoid mixing refactors with feature changes.


## Contributor workflow

This project values clear plans, small scoped pull requests, and reliable build hygiene.

- Initialize:
  - Review key docs: `docs/adr`, `docs/plan`, `docs/archive/*/*`, `docs/contributing`, `build.sbt`.
  - Skim recent commits to understand current context.
- Develop:
  - Keep changes focused; reference ADRs (see `docs/adr/INDEX.md`).
  - Follow formatting/linting and run `sbt fmtCheck` and `sbt compile` locally.
- Validate:
  - Prefer module‑scoped tests (e.g., `sbt core/test`).
  - Target ≥80% coverage on changed code when practical.
- Submit PR:
  - Include a concise summary, ADR references, and a brief test plan.
  - Keep the scope tight and avoid mixed refactors.

## References
- ADR Index `docs/adr/INDEX.md`
- Developer Handbook `docs/contributing/HANDBOOK.md`

## Session Workflow
- See: docs/talks/presenter-cheatsheet.md
- See: docs/talks/timed-outline.md

## Condensed Pipeline Checklist
- See: docs/design/framework-behaviors.md
- See: docs/quality/release-criteria.md
