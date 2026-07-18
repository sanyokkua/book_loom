# Architecture & Layering

Scope: all Gradle subprojects — `:api :util :document :llm :pipeline :persistence :ui :app`. Governs the module graph, dependency direction, FX-free core, and port-vs-concrete boundaries. Authoritative spec: `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`.

## MUST

- **MUST** keep the dependency graph pointing inward. Allowed edges only:
  `:app → :ui :pipeline :document :llm :persistence :api :util` · `:ui → :pipeline :api :util` · `:pipeline → :document :llm :persistence :api :util` · `:document/:llm/:persistence → :api :util` · `:util → :api` · `:api → (nothing internal)`. No other internal edge. — Rationale: a single inward-pointing DAG has no cycles and lets any collaborator be mocked at `:api`.
- **MUST** declare contracts as interfaces/records/enums in `:api` and implement them elsewhere (`DocumentPort`→`:document`, `Provider`/`ProviderFactory`→`:llm`, `TranslationEngine`→`:pipeline`, repository ports→`:persistence`). Callers hold the port, never the impl. — Rationale: ports keep the graph aimed at `:api` and keep code testable.
- **MUST** keep the core FX-free: only `:ui` and `:app` may `requires javafx.*`. No class in `:api/:util/:document/:llm/:pipeline/:persistence` may import `javafx..`. Enforced by JPMS `requires` + an ArchUnit `fx-free-core` test. — Rationale: business logic must run headless (WireMock/JUnit) and never depend on a UI toolkit.
- **MUST** restrict framework leakage: only `:llm` imports `java.net.http..`; only `:persistence` imports `java.sql..`/`org.jdbi..`/`org.flywaydb..`; `:api` imports no framework (Guice/Jackson/JavaFX/JDBI/parser libs). — Rationale: each concern stays in its owning module; `:api` is a pure contract floor.
- **MUST** keep every `internal`/non-exported package (e.g. `document.epub`, `llm.dto`, `pipeline.*`, `persistence.*`) out of `:ui`. `internal` code never imports `ui` and is never exported across the module edge; cross-module access goes through exported ports only. — Rationale: implementation details must not become an accidental public API or couple to presentation.
- **MUST** wire each port→impl binding in the owning module's Guice module, with one composition root in `:app` (constructor injection only). — Rationale: one place assembles the graph; modules stay independently loadable.
- **MUST** back every layering claim with an ArchUnit test in the shared `arch-test` source set (`fx-free-core`, `dependency-direction` layered check, `ports-not-concretes`, `no-http-in-core-except-llm`, `no-sql-in-core-except-persistence`, `api-is-framework-free`, `records-first`, `bootstrap-no-static-logger` — no static SLF4J `Logger` on the pre-logging bootstrap path, see `logging.md`). A failing ArchUnit test fails the build. — Rationale: the boundary is enforced, not documented.

## SHOULD

- **SHOULD** route `:ui → :pipeline` calls through the `TranslationEngine` port and `:api` DTOs, not through `:document`/`:llm`/`:persistence` directly. — Rationale: the UI depends on one orchestration seam.
- **SHOULD** expose new cross-module capability by first adding the interface/record to `:api`, then implementing it, then binding it in Guice. — Rationale: contract-first keeps the direction correct.
- **SHOULD** register JPMS `provides ... with ...` for each port impl so the module system and ServiceLoader agree with Guice. — Rationale: the declared module graph mirrors the runtime graph.

## Reject if

- Any core module (`:api/:util/:document/:llm/:pipeline/:persistence`) imports `javafx..`.
- A new edge appears that is not in the allowed list, or any dependency cycle is introduced.
- `:ui` or `:pipeline` depends on a concrete `..Impl`/`..Dao`/`..Service` from another module instead of an `:api` port.
- `java.net.http` used outside `:llm`, or `java.sql`/JDBI/Flyway used outside `:persistence`.
- `:api` gains a dependency on Guice, Jackson, JavaFX, JDBI, or a parser library.
- An `internal`/non-exported package is imported across a module boundary, or imports `ui`.
- New layering behaviour lands without a corresponding ArchUnit test.
