# ADR-0014 — Lombok on services, records for data carriers (hybrid)

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The codebase uses Java records for its data carriers (DTOs, value objects, wire types), which removes constructor and
accessor boilerplate for *immutable data*. It does not, however, help the **service and component classes** — the
Guice-wired collaborators that make up most of the application. Those classes accumulate hand-written constructors that
assign a list of `final` injected dependencies, plus boilerplate logger declarations. This is exactly the kind of
plumbing Lombok removes with `@RequiredArgsConstructor` and `@Slf4j`.

The original convention was "records-first, no Lombok (only `@Slf4j` if at all)." The team wants to cut the
service-layer boilerplate with Lombok without giving up records for data. This ADR settles the hybrid.

## Decision drivers

- **Remove real boilerplate.** Service constructors (assigning injected `final` fields) and logger declarations are
  repetitive and add no information.
- **Keep records for data.** Records are the right tool for immutable carriers; Lombok `@Data`/`@Value` would be a step
  backwards there.
- **Play well with Guice.** Constructor injection must keep working; `@RequiredArgsConstructor` generates exactly the
  constructor Guice needs.
- **Play well with the toolchain.** Lombok must coexist with Error Prone/NullAway, Spotless (Palantir), and the
  annotation-processor ordering.
- **Bounded, reviewable scope.** The relaxation must be specific, not "Lombok everywhere."

## Considered options

- **Option A — Records only, no Lombok (status quo).** Keep hand-written service constructors and loggers.
- **Option B — Hybrid: records for carriers, Lombok on services** (`@RequiredArgsConstructor`, `@Slf4j`, `@Builder`
  where useful); still no `@Data`/`@Value` on carriers.
- **Option C — Lombok everywhere**, including `@Value`/`@Builder` for data carriers, dropping records-first.

## Decision outcome

Chosen: **Option B.** Records remain the rule for immutable **data carriers** — the ArchUnit `records-first` check
stays, scoped to the carrier packages (`..dto`, `..api..`), so DTOs and value objects are records and never `@Data`/
`@Value`. On **service/component classes**, Lombok is allowed and encouraged for boilerplate removal:

- `@RequiredArgsConstructor` — generates the constructor over `final` injected dependencies (works with Guice
  constructor injection).
- `@Slf4j` — the logger field.
- `@Builder` — where a builder genuinely improves call-site clarity.

`@Data` and `@Value` remain forbidden (records cover that need). Lombok is added as a dependency and annotation
processor, ordered **before** Error Prone/NullAway in the annotation-processor path, and verified compatible with
Spotless (Palantir) formatting. The coding-style rule, the `coder` agent guardrails, the build/tooling and dependency
docs, and the Definition of Done are updated accordingly.

### Consequences

Positive:

- Service classes lose their repetitive constructors and logger declarations, staying focused on behaviour.
- Records still give immutable value semantics and pattern-matching for data.
- Guice wiring is unchanged (the generated constructor is what it injects).

Negative:

- Lombok is an annotation processor with its own toolchain interactions (Error Prone/NullAway ordering, IDE plugin) that
  must be configured and kept working.
- A second boilerplate mechanism (records vs Lombok) means contributors must know which applies where — mitigated by the
  carrier-package ArchUnit rule.

Neutral:

- The permitted annotation set is explicit and small; anything beyond it (notably `@Data`/`@Value`) is rejected in
  review and by convention.

## Pros and cons of the options

### Option A — Records only (status quo)

Pros: one mechanism; no extra processor. Cons: leaves genuine service-constructor and logger boilerplate in place.

### Option B — Hybrid (chosen)

Pros: removes the real boilerplate; keeps records for data; Guice-friendly; bounded scope. Cons: adds Lombok to the
toolchain; two mechanisms to understand.

### Option C — Lombok everywhere

Pros: one annotation style across the board. Cons: replaces idiomatic records with `@Value`, loses
pattern-matching/value semantics benefits, and is a larger, less reviewable change.

## Links

- Design decisions: DD-05 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-05-records-first`)
- Spec clauses: `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
  `docs/specification/05_Dependencies/01_DEPENDENCIES.md`,
  `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`
- Rules: `.claude/rules/java-coding-style.md`
- Stories: none yet
