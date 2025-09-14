# Overview: 
Most of the code in flowforge is not Scala idiomatic, I see lot of java-like coding patterns which spoils entire asthetics.
Below are some insights and instructions I have put but you plan accordingly which one to use where - for that you must do aggressive code review of entire flowforge - all modules

## Instructions -
1. Without changing functionality or functional-requirements or non-functional-requirements, function-signatures, return Types etc. refactor the code to pure Scala idiomatic => Specifically Scala 2 (We are using Scala 2.13.16)
2. Follow idiomatic functional programming, immutability, pure functions, explicit effects, use for-comprehensions.. example: Monads instead of try-catch-finally
3. Keep Spark transforms pure (return `Dataset[...]`), move external IO/orchestration to `F[_]` via `EffectSystem`.
4. Naming: `camelCase` vals/defs, `PascalCase` types, lowercase packages; avoid one‑letter names.
5. Purely Functional: Emphasizing pure functions and immutability to create predictable and maintainable code.
6. Immutability: All data structures are immutable by default, ensuring thread safety and predictability.
7. First-Class Functions: Functions are treated as first-class citizens, allowing them to be passed as arguments,
   returned from other functions, and assigned to variables.
8. Higher-Order Functions: Functions that take other functions as parameters or return them as results, enabling powerful
   abstractions and code reuse.
9. Pure Functions: Functions that always produce the same output for the same input and have no side effects, making them
   easier to reason about and test.
10. Function Composition: Combining simple functions to build more complex ones, promoting modularity and code reuse.

11. Pattern Matching: A mechanism for checking a value against a pattern, allowing for concise and expressive handling of
    different data structures.

12. Implicits: Using implicit parameters and conversions to enable type class instances and enhance code readability.

13. Monads: Abstractions that represent computations as a series of steps, enabling chaining of operations while managing
    side effects (e.g., Option, Either, Future).

14. Type Classes: A way to define generic interfaces that can be implemented for different types, enabling ad-hoc
    polymorphism and code reuse.
15. Algebraic Data Types (ADTs): Composite types formed by combining other types, such as case classes and sealed traits,
    allowing for expressive modeling of data.
    16 Referential Transparency: The property that an expression can be replaced with its corresponding value without
    changing the program's behavior, facilitating reasoning about code.
17. Lazy Evaluation: Deferring computation until the result is needed, which can improve performance and enable the
    creation of infinite data structures.
18. Tail Recursion: A special case of recursion where the recursive call is the last operation in a function, allowing for
    optimization and preventing stack overflow.
19. Type Safety 100 %: Leveraging Scala's strong static type system to catch errors at compile. Builder Pattern with Type
    Safety.
20. Complete Monadic Ecosystem: Utilizing libraries like Cats and Scalaz to work with monads, functors, and applicatives
    for elegant handling of side effects and asynchronous computations.
21. Convention over Configuration with Functional Purity: Emphasizing sensible defaults and reducing boilerplate while
    maintaining pure functions and immutability.

22. Fiber-safe concurrency for parallel processing
23. Comprehensive ecosystem for functional programming
24. Interoperability with existing libraries and frameworks
25. Decorator design Pattern with Effect Systems: Using the decorator pattern to add additional behavior to existing
    components in a type-safe manner, while leveraging the capabilities of effect systems like ZIO or Cats Effect
    to manage side effects and resource safety. time, ensuring robust and reliable applications.
26. Phantom Types: Using phantom types to encode additional type information at compile time without affecting runtime
    representation, enhancing type safety and expressiveness.
27. Type-Level Programming: Leveraging Scala's advanced type system to perform computations and enforce
28. F-Bounded Polymorphism: Using F-bounded polymorphism to define type hierarchies where a type parameter is constrained
    to be a subtype of a specific type, enabling more precise typing and code reuse. For Type-Safe Composition
29. Kleisli Arrows: Utilizing Kleisli arrows for Composable Transformations and to represent computations that produce
    monadic values, allowing for elegant composition of effectful functions and enabling a functional approach to building
    data pipelines.
30. Kleisli for effectful stages: Kleisli composes effectful functions nicely. Each stage of the data pipeline (e.g.,
    extraction, transformation, loading) can be represented as a Kleisli arrow, allowing for seamless composition of
    effectful operations while maintaining type safety and clarity.
31. Phantom-Type Builder Patterns: Using phantom types in builder patterns to enforce correct construction of complex
    objects at compile time, preventing invalid states and ensuring that all required parameters are provided before
    building the final object. Phantom types track the current output type of stages. Using a phantom-type builder pattern
    to enforce correct construction of complex objects at compile time, ensuring that all required parameters are provided
    and valid before the object can be instantiated. Phantom types are markers used only by the compiler; they don’t exist
    at runtime, but enforce rules at compile time. In a builder, each added stage updates the phantom Out type. Trying to
    build before types align fails to compile, not crash. They exist exclusively at compile time and carry extra
    information that enables the compiler to enforce rules. Phantom Types provide extra information to the compiler… allow
    extra constraints… program fails to compile if constraints don’t hold ...
32. Higher-Kinded Type: Employing higher-kinded types to define abstractions that can operate on type constructors,
    enabling the creation of generic and reusable components that work with various data structures and effect types.
33. Tagless Final Encoding: Adopting the tagless final encoding pattern to define type-safe and extensible algebras for
    domain-specific languages, allowing for flexible interpretation and composition of operations without relying on
    concrete data types.
34. Free Monads: Using free monads to represent computations as a series of steps, enabling the separation of program
    description from execution and facilitating the creation of interpreters for different execution strategies.
35. Type Classes: Leveraging type classes to define generic interfaces that can be implemented for different types,
    enabling ad-hoc polymorphism and code reuse across various data structures and effect types. Adapter Pattern with Type
    Classes.
36. Type class patterns: Using type class patterns to define and implement generic behaviors for different types, allowing
    for flexible and reusable code that can work with various data structures and effect types.
37. Self Types: Utilizing self types to express dependencies between traits, enabling more precise typing and ensuring
    that certain traits can only be mixed into classes that also extend specific other traits.
    38 Structural types: Employing structural types to define types based on their members rather than their explicit names,
    allowing for more flexible and dynamic typing in certain scenarios.
39. Tagless Final for Effect Abstraction: Using the tagless final pattern to abstract over different effect types,
    enabling the creation of generic and reusable components that can work with various effect systems (e.g., Cats Effect,
    ZIO) without being tied to a specific implementation. Strategy Pattern with Tagless Final.
40. Observer Design Pattern with Reactive Streams: fs2. Implementing the observer design pattern using reactive streams (
    e.g., Akka Streams, FS2) to enable asynchronous and event-driven data processing, allowing components to react to
    changes in data and propagate updates through the system in a non-blocking manner.
41. Cats Monads - Higher Kinded Type class
42. for-comprehensions are not iterations. Step away from the concept of iterations.
43. FlatMap is mental model for chained transformations.
44. Cats Monads Use cases: List Combinations, Option transformations, Asynchronous chained computations, Dependent
    computations, Cats Monad Transformers: Higher-Kinded-Types for convenience over nested monadic values. OptionT,
    EitherT, Cats Data Manipulation: Readers, Writers, Evaluations, State, Data Validations.
45. Be mindful of -
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
46. Adapter Pattern with Type Classes: Wrap imperative APIs in functional type class interfaces
47. Compatibility Layers: Create functional bridges that preserve existing interfaces while adding type safety
48. Effect-Safe Resource Management: All external integrations must use Resource[F, _] for cleanup
49. Validation Aggregation: Multi-error scenarios use ValidatedNel, never fail-fast exceptions
50. Kleisli Integration: External operations should be composable via Kleisli arrows where possible

51. ### 🏛 SOLID Principles Implementation

Use all SOLID principles where applicable

- S - Single Responsibility Principle
- O - Open/Closed Principle
- L - Liskov Substitution Principle
- I - Interface Segregation Principle
- D - Dependency Inversion Principle - Depend on abstractions, not concretions

52. ### 🏗 Design Patterns

- Creational Patterns: Use all creational patterns where applicable
- Structural Patterns: Use all structural patterns where applicable
- Behavioral Patterns: Use all behavioral patterns where applicable


53. ### Advanced Type-Level Programming Patterns
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



54. ### File structure guidance:
- A class and its companion should live together in the same file.
- sealed traits and their subtypes must also be in the same file for compiler safety.
- File names usually match the main type they hold.
- Multi-type grouping: When types are closely related, grouping is permitted—but only when justified. The file should then use meaningful lowerCamelCase names.
- Developer ergonomics: Having one type per file helps with navigation, findability, and code review. It avoids confusion and reduces search friction.


