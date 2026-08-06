# ADR-0024 — Use Lombok `@NoArgsConstructor(PRIVATE)` for static-utility classes

**Status:** accepted **Date:** 2026-08-06
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

ADR-0014 settled the Lombok/records hybrid: records for immutable data carriers, and
`@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on Guice-wired service/component classes to remove constructor and
logger boilerplate. It says nothing about a third, common shape in this codebase: **static-utility and
no-instantiation classes** — no state, no injection, every member `static` — such as `AppLifecycle`'s companions,
mapper/factory helpers, and constant holders. Those classes fall outside ADR-0014's "service/component" scope, so
today they hand-write the private constructor that blocks instantiation:

```java
private Foo() {
    // no instances
}
```

A repo sweep found twelve such classes repeating this exact three-line idiom. It is boilerplate of the same kind
ADR-0014 already decided to remove elsewhere with Lombok — it just was not in scope there.

## Decision drivers

- **Remove real boilerplate** without re-opening ADR-0014's scope decisions (records still win for data; Lombok on
  services still means `@RequiredArgsConstructor`/`@Slf4j`/`@Builder`, nothing more).
- **Stay bounded.** The relaxation must name one specific annotation for one specific class shape, not become a
  general "Lombok everywhere" license (the same driver ADR-0014 weighed against Option C there).
- **Avoid magic.** Whatever is chosen must not implicitly change class semantics the author did not ask for.
- **Keep Checkstyle's existing `FinalClass`/`HideUtilityClassConstructor` checks meaningful** — the mechanical gate
  should still recognize a correctly-shaped utility class.

## Considered options

- **Option A — Status quo.** Keep hand-writing `private Foo() { /* comment */ }` on every static-utility class.
- **Option B — Lombok `@NoArgsConstructor(access = AccessLevel.PRIVATE)`.** Generate the same private no-arg
  constructor Lombok already generates for services' dependency constructors, applied to the utility-class shape
  instead.
- **Option C — Lombok `@UtilityClass`.** A single annotation that makes the class `final`, makes every member
  implicitly `static`, and generates the private constructor.

## Decision outcome

Chosen: **Option B**, because it removes the exact three-line boilerplate that motivated this ADR while changing
nothing else about the class — the author still declares `final` and `static` explicitly, exactly as before, so the
class's shape stays visible at the declaration site rather than implied by an annotation. Static-utility/
no-instantiation classes (a class with no instance state, no injected dependencies, and every member `static`) now
**MUST** use `@NoArgsConstructor(access = AccessLevel.PRIVATE)` instead of a hand-written private constructor. This
does **not** extend to data carriers, which remain records per ADR-0014, and it does **not** introduce
`@UtilityClass` (Option C) anywhere in the codebase.

### Consequences

Positive:

- Twelve existing classes (and every future one of the same shape) drop three lines of boilerplate each for one
  annotation.
- The generated constructor is bytecode-identical in intent to the hand-written one: `private`, no-arg, no body —
  Checkstyle's `FinalClass`/`HideUtilityClassConstructor` checks continue to pass unmodified.
- The class declaration (`final`, `static` members) stays exactly as explicit as it is today; nothing is implied by
  the annotation beyond the constructor itself.

Negative:

- A third Lombok annotation to know alongside `@RequiredArgsConstructor`/`@Slf4j`/`@Builder`, widening the allowed
  set ADR-0014 called "explicit and small."
- ArchUnit cannot distinguish a Lombok-generated private constructor from a hand-written one at the bytecode level
  (both compile identically) — the boilerplate ban is a source-text (Checkstyle) concern, not an architectural one.

Neutral:

- The twelve classes with the current hand-written idiom are not retrofitted by this ADR; that is tracked as
  follow-up debt against the now-stated rule, not part of this decision.

## Pros and cons of the options

### Option A — Status quo

Good: no new annotation to learn; nothing changes. Bad: leaves acknowledged, mechanical boilerplate in place with no
plan to remove it — the exact gap this ADR closes.

### Option B — `@NoArgsConstructor(PRIVATE)` (chosen)

Good: removes the boilerplate; changes nothing else about the class; composes cleanly with the existing
`FinalClass`/`HideUtilityClassConstructor` Checkstyle checks. Bad: one more permitted annotation.

### Option C — `@UtilityClass`

Good: single annotation, most compact. Bad: implicitly forces the class `final` and every member `static`, which is
more than this decision asks for — a class that is *mostly* static utility with one genuine exception would be
silently rewritten rather than flagged, and the "why is this method static" question moves from the declaration to
an annotation the reader must already know the semantics of. Rejected as too magic, mirroring why ADR-0014 rejected
`@Value`/`@Builder`-everywhere for data carriers.

## Links

- Design decisions: DD-05 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-05-records-for-data-carriers-lombok-on-services-hybrid`)
- Spec clauses: `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
  `docs/specification/05_Dependencies/01_DEPENDENCIES.md`
- Rules: `.claude/rules/java-coding-style.md`, ADR-0014 (`docs/adr/ADR-0014-lombok-hybrid.md`)
- Stories: none yet
