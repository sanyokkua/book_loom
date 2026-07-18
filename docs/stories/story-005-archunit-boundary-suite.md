---
id: STORY-005
title: Add the ArchUnit boundary suite enforcing the eight architecture rules
status: ready
spec_clauses:
  - docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules
  - docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#logging-bootstrap-order
  - DD-06
modules:
  - arch-test
acceptance_criteria:
  - STORY-005-AC-1
  - STORY-005-AC-2
  - STORY-005-AC-3
edge_cases: [ ]
depends_on:
  - STORY-001
  - STORY-004
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: tester
estimate: M
---

# STORY-005 — Add the ArchUnit boundary suite enforcing the eight architecture rules

## Goal

Turn the architecture's paper rules into failing tests: a shared `arch-test` source set hosting the eight ArchUnit
rules — fx-free-core, dependency-direction, ports-not-concretes, no-http-except-`:llm`, no-sql-except-`:persistence`,
api-framework-free, records-first (carrier packages), and bootstrap-no-static-logger — wired into `check` so a boundary
violation anywhere fails the build from the first line of production code onward.

## In scope

- The `arch-test` source set and its Gradle wiring into `check`.
- All eight rules with package/module scopes exactly as the layering spec defines them.
- Freezing rule scope so empty modules pass now and violations fail as code arrives.

## Out of scope

- The offline-invariant WireMock isolation harness (belongs with `:llm` in PHASE_05; the structural `no-http-except-llm`
  rule here is the PHASE_00 seed of F9).

## Spec inputs

- `02_MODULES_AND_LAYERING.md#archunit-rules` — the eight rules and their exact scopes.
- `02_MODULES_AND_LAYERING.md#dependency-direction` — the allowed edges the direction rule encodes.
- `10_DI_AND_LIFECYCLE.md#logging-bootstrap-order` — the bootstrap-no-static-logger rule's rationale and scope.
- DD-06.

## Design constraints

- Rules live once in `arch-test`, not per-module; they run in `check` and the pre-push fast subset (STORY-011).
- records-first scopes to carrier packages (`..dto`, `..api..`) per DD-05 — Lombok-bearing service classes are out of
  its scope.

## Acceptance criteria

### STORY-005-AC-1

`./gradlew check` executes all eight ArchUnit rules and is green on the scaffold. (P1)

### STORY-005-AC-2

Given a fixture class violating each rule in turn (an FX import in `:document`; a reversed module edge; an HTTP client
in `:pipeline`; SQL in `:llm`; a Guice import in `:api`; a mutable class in an `..api..` package; a
`static final Logger` in the bootstrap package), the corresponding rule fails the build. (P5)

### STORY-005-AC-3

The dependency-direction rule accepts every edge the layering table allows and rejects any edge it omits — verified for
the full eight-module graph. (P3)

## Test plan

- STORY-005-AC-1 → architecture · arch-test/src/test/java/ua/bookloom/arch/ArchRulesPresentTest.java ·
  allEightRulesRunGreenOnScaffold · `Proves: STORY-005-AC-1`
- STORY-005-AC-2 → architecture · arch-test/src/test/java/ua/bookloom/arch/ArchRuleViolationFixturesTest.java ·
  eachViolationFixtureFailsItsRule · `Proves: STORY-005-AC-2`
- STORY-005-AC-3 → architecture · arch-test/src/test/java/ua/bookloom/arch/DependencyDirectionMatrixTest.java ·
  allowedEdgesPassForbiddenEdgesFail · `Proves: STORY-005-AC-3`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate.
