# Java Coding Style

Scope: all Java source (`**/src/main/java/**`, `**/src/test/java/**`) across every module. Java 25 language level. Formatting is enforced separately by Spotless (`gradle-build-and-quality.md`); this rule governs design idioms.

## MUST

- **MUST** model immutable data carriers (DTOs, value objects, wire types) as `record`s — never Lombok `@Data`/`@Value`. On **service/component classes** Lombok is allowed to remove boilerplate: `@RequiredArgsConstructor` (constructor injection of `final` deps), `@Slf4j` (logger), and `@Builder` where a builder genuinely helps. — Rationale: records give immutability, value semantics, and pattern-matching for data; Lombok removes constructor/logger boilerplate on services (DD-05, ADR-0014).
- **MUST** use `Optional<T>` for return values only — never as a field, parameter, or collection element. Use `@Nullable` (JSpecify) at boundaries where absence is expressed instead. — Rationale: `Optional` communicates "no result" at a call site without polluting state.
- **MUST** call `Objects.requireNonNull(x, "x")` for every non-`@Nullable` reference parameter of a public/boundary method (constructors, port impls). — Rationale: fail fast at the boundary, never propagate a silent null inward.
- **MUST** model closed hierarchies as `sealed` types and branch on them with an exhaustive pattern-matching `switch` (no `default` when the compiler can prove exhaustiveness). — Rationale: the compiler flags every unhandled case when a variant is added.
- **MUST NOT** declare or propagate checked exceptions in application code. Wrap I/O/library checked exceptions at the adapter boundary into a `Result.err(AppError(...))`, preserving the original as `cause`. — Rationale: failures cross module edges as the typed envelope, not as `throws`.
- **MUST NOT** use `synchronized` (methods or blocks) in new code. Use immutability, confinement to one thread, `java.util.concurrent` types, or the `InferenceGate` semaphore instead. — Rationale: coarse locks cause contention and deadlocks; see `threading-concurrency.md`.
- **MUST** use constructor injection only (Guice) — no field/setter injection, no static singletons, no service locator. — Rationale: dependencies are explicit, final, and testable.
- **MUST** declare fields, parameters, and locals `final` by default; drop `final` only where reassignment is genuinely required. — Rationale: immutability is the default, mutation is a deliberate exception.

## SHOULD

- **SHOULD** keep methods ≤ 30 lines, classes ≤ ~400 lines, and nesting depth ≤ 3. Extract helpers or types past those limits. — Rationale: small units stay reviewable and testable.
- **SHOULD** name by convention: `PascalCase` types, `camelCase` methods/fields, `UPPER_SNAKE` constants, port impls `XxxService`/`XxxImpl`, DAOs `XxxDao`, Guice modules `XxxModule`. — Rationale: predictable names aid ArchUnit rules and navigation.
- **SHOULD** prefer expressions (switch expressions, streams for transformation) over statement-heavy loops when it reduces mutable state — but keep hot paths simple. — Rationale: fewer moving parts, clearer intent.
- **SHOULD** annotate every package `@NullMarked` (JSpecify, package-info) so NullAway treats unannotated references as non-null. — Rationale: null-correctness is checked at compile time.

## Reject if

- A data carrier uses Lombok `@Data`/`@Value` instead of a record (Lombok on services is fine: `@RequiredArgsConstructor`/`@Slf4j`/`@Builder`).
- `Optional` appears as a field, parameter, or collection element.
- A public/boundary method omits `requireNonNull` on a non-nullable reference parameter.
- A closed hierarchy is branched with `instanceof` chains or a non-exhaustive switch instead of a sealed type.
- Application code declares `throws` of a checked exception, or swallows a cause when wrapping.
- New code introduces `synchronized`, field/setter injection, or a mutable static singleton.
- A method exceeds ~30 lines, a class ~400 lines, or nesting exceeds 3 with no extraction and no justification.
