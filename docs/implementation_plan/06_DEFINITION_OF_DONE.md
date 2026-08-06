**Status:** Final **Owner:** architect **Audience:** anyone implementing or reviewing a change **Last Updated:**
2026-08-02 **Cross-references:** `docs/adr/ADR-0016-openspec-delivery-tracking.md`,
`docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`, `docs/specification/mockups/ui-mockup.html`

# Definition of Done

The unit of work is an **OpenSpec change** under `openspec/changes/<name>/` (ADR-0016). A change is ready to archive
only when **every** item below is true. Two things must hold: the **per-change checklist** (this change's work is
complete and proven) and the **always-true architecture invariants** (the change left the system's guarantees intact).

This checklist is mirrored into `openspec/config.yaml` under `operations.apply.guidance`, so the apply step sees it
without opening this file. Keep the two in sync.

## per-change-checklist {#per-change-checklist}

- [ ] **Every requirement in the change's `specs/**/spec.md` has a covering test.** The test carries
  `// Covers: FR-*` plus a one-line EARS restatement of the obligation it proves (ADR-0016 R5), for example:

  ```java
  // Covers: FR-DOC-05 — IF the placeholder multiset of the target differs from the source,
  //         THEN the chunk fails as a validation error with no repair attempt.
  @Test
  void validateChunk_placeholderMissingFromTarget_returnsValidationError() { … }
  ```

  A `skip_specs: true` change has no requirements, so this item is satisfied vacuously — its tasks still carry their
  own verification.
- [ ] **Every enumerated edge case (`EC-<AREA>-<N>`) the change touches has a covering test**, marked the same way.
- [ ] **Every checkbox in `tasks.md` is checked**, and none was checked without the work actually landing.
- [ ] **The entire build, lint, and format gate is green — `./gradlew clean build check spotlessCheck` passes with zero
  findings across the whole project, not just touched code.** There is **no "pre-existing" exemption**: if any
  mechanical check (Spotless/Palantir 120-col, Checkstyle, Error Prone + NullAway (JSpecify `@NullMarked`), SpotBugs +
  FindSecBugs, ArchUnit, tests) fails anywhere — even in code the change did not write — the change is **not done**
  until it is fixed (as part of this change or a cited prerequisite one). A red gate is never carried forward or waved
  through. This clean-gate requirement is an **implicit requirement of every change**.

  **Run `./gradlew :build-logic:clean` first when the gate is being run *as* the Definition-of-Done proof.**
  `build-logic` is an included build, so the root `clean` does not reach it: `:build-logic:test` can report
  `UP-TO-DATE` and serve its functional canaries — the tests that prove Spotless, NullAway, Checkstyle, FindSecBugs,
  the license gate, dependency locking, and the coverage gate each fail *red* — straight from cache, while the build
  still prints `BUILD SUCCESSFUL`. The tell is the task count: cite **`91 actionable tasks: 91 executed`**, not
  `BUILD SUCCESSFUL` alone.
- [ ] **The gate that counts is the LOCAL one, until the application is feature-complete.** Standing decision taken
  2026-08-03 (recorded in archived change 1, task 7.7): BookLoom is built on local feature branches for the whole
  build-out and GitHub Actions is not exercised until an end-of-project CI validation pass. So: never block a task on
  a CI run link, and never claim a CI run that has not happened. The workflow files must still be **correct and
  CI-ready** — authored, `actionlint`-clean, action versions pinned, and kept in step with the local scripts — that
  part is not deferred. The CI-only gates (`checkLicense`, `dependencyCheckAggregate`) may be run locally by name when
  a change touches dependencies; they are deliberately outside `check` because both need the network.
- [ ] **`./gradlew test` green** (unit + integration; UI tests green where the change touches `:ui`).
- [ ] **The test types the change implies are present and green** per
  `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`:

  | Area the change touches | Required test types                                                                                     |
  |-------------------------|---------------------------------------------------------------------------------------------------------|
  | `:llm` / provider       | WireMock at the **HTTP seam** for **both** dialects (Ollama-native `/api/*` + OpenAI-compatible `/v1/*`), covering request shaping, the response-handling contract, retry/`Retry-After`, and each HTTP→`ErrorCode` mapping |
  | `:document`             | The **canonical-equal golden round-trip** test for each format touched (TXT compares exact bytes)        |
  | `:pipeline`             | A **pipeline e2e** driving a small whole book against a stub/WireMock provider                           |
  | `:persistence`          | Integration against a **real temp SQLite file or `:memory:` with Flyway migrations applied** (no Testcontainers) |
  | `:ui`                   | TestFX + Monocle **widget**, **screen/state**, and **UI-matches-mockup conformance** tests               |
  | i18n                    | `messages_en` / `messages_uk` **key-set parity**, valid ICU patterns, UK plural one/few/many/other       |
  | `:app` / packaging      | The **boot smoke** (injector + two-phase init against a temp DB) and the jpackage-image launch check     |

- [ ] **Provider-related changes add or update a `liveLocal` test** (tagged, env-gated, excluded from CI and `check`)
  exercising the real local provider(s) — Ollama and/or LM Studio. It must **skip cleanly** when no endpoint is
  configured.
- [ ] **ArchUnit boundary tests green** (`./gradlew check`) — `fx-free-core`, `dependency-direction`,
  `ports-not-concretes`, `no-http-in-core-except-llm`, `no-sql-in-core-except-persistence`, `api-is-framework-free`,
  `records-first`, `bootstrap-no-static-logger`.
- [ ] **JaCoCo branch coverage holds at ~80% on the core modules** (`:api`, `:util`, `:document`, `:llm`, `:pipeline`,
  `:persistence`); `:ui` is excluded and covered behaviourally by TestFX.
- [ ] **Module inventory updated** in `docs/implementation_plan/01_MODULE_INVENTORY.md` if the change added a module or
  package.
- [ ] **`:ui` changes match the mockup visual reference (P6)** for each named screen/state/theme against
  `docs/specification/mockups/ui-mockup.html` — asserted structurally and by looked-up palette token.
- [ ] **No new background/unsolicited network calls** — the offline invariant holds (the only egress is user-triggered
  provider communication — inference, model discovery, verification — with the configured provider).
- [ ] **ADRs cited** (if the change took an architecturally significant decision) exist under `docs/adr/` and are
  `accepted`. A genuine gap in the frozen spec becomes a **new ADR**, never a spec edit.
- [ ] **Cited spec anchors resolve** — every `FR-*`/`NFR-*`/`DD-*` reference in the change's artifacts points at a real
  in-repo file and anchor.
- [ ] **`openspec validate <change> --strict` is clean**, then the change is archived with `openspec archive` so its
  requirements fold into `openspec/specs/`.

## stage-level-exit-criteria {#stage-level-exit-criteria}

A **stage** (ADR-0017, `docs/implementation_plan/07_ROADMAP.md#stages`) is complete when all its changes are archived
and its stated exit gate holds. In addition, every stage must exit with the stage-exit invariants from
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants`: the offline invariant intact; the
whole-project clean gate green; ArchUnit boundary tests green; `./gradlew test` green; document-touching stages keeping
the round-trip golden test green; UI-touching stages matching the mockup visual reference (P6). The `traceCheck` clause
in that section is struck by ADR-0016 and does not apply.

At each stage boundary, run `bash scripts/fr-coverage.sh` and read the output. It lists `FR-*` ids in the frozen catalog
that no shipped requirement in `openspec/specs/` claims yet. This is **advisory** — mid-build-out gaps are expected and
correct, since `openspec/specs/` tracks what is *built*, not what is *intended*. It is never a build failure.

The forward-compatibility seams (F1–F9) a stage establishes must exist and be exercised by a test before the stage
closes.

## always-true-architecture-invariants {#always-true-architecture-invariants}

These hold after **every** change, not just the one that introduced them. ArchUnit and the checks above enforce most of
them; a change that would break one is wrong.

| Invariant                                | Statement                                                                                                                                                                                                                                                                               | Enforced by                                                  |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| **FX-free core**                         | No class in `:api`/`:util`/`:document`/`:llm`/`:pipeline`/`:persistence` depends on `javafx..`. Only `:ui` and `:app` require JavaFX.                                                                                                                                                   | ArchUnit `fx-free-core`; JPMS                                |
| **Result / AppError envelope**           | Fallible operations return `Result{data,error}` with a typed `AppError`/`ErrorCode` and a safe-details allowlist; partial results supported. No leaking exceptions across ports.                                                                                                        | `:api`; code review; P3 scenarios                            |
| **Records for data, Lombok on services** | Data carriers are Java records; no `@Data`/`@Value`. DTO/`api` packages contain records only. Services may use Lombok `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` (DD-05, ADR-0014).                                                                                                 | ArchUnit `records-first` (carrier packages)                  |
| **Token-only theming**                   | The UI styles exclusively via looked-up color tokens on `.root`; light + dark from one token set. No hard-coded colors in controls/FXML.                                                                                                                                                | `:ui/ua.bookloom.ui.theme`; review; P6 scenarios             |
| **Offline invariant**                    | No code path issues a network call other than user-triggered provider communication (inference, model discovery, verification) with the configured provider. Tests run against WireMock in isolation.                                                                                   | ArchUnit `no-http-in-core-except-llm`; WireMock isolation    |
| **Skeleton not regenerated**             | The document skeleton is never sent to the model and never regenerated; only text nodes change. Round-trip is structure-and-text-preserving (canonical-equal) — exact bytes may differ under re-serialization; TXT compares exact bytes (modulo intentional language metadata) (DD-43). | Round-trip golden tests (canonicalized compare); `:document` |
| **Credentials as reference**             | Secrets are stored only as a reference (env-var name / OS keychain entry), never the secret value; secrets never logged or placed in error details.                                                                                                                                     | `:persistence/...secret`; FindSecBugs; review                |
| **Single-flight gate**                   | All inference is serialized through the `InferenceGate` so a local model serves one request at a time.                                                                                                                                                                                  | `:llm/ua.bookloom.llm.gate`; pipeline uses the gate          |
| **Verified provider before inference**   | No inference (run or diagnostic) is issued before connection + model availability are verified; a project resumes on its own bound provider/model, prompting before any fallback or settings-driven change.                                                                             | `:llm` verify; resume flow; DD-31                            |
| **Response-handling contract**           | Model responses are handled JSON-first and tolerantly: structured-output request where supported, reasoning/`<think>`/fence strip, unknown-field tolerance, one repair retry, deterministic text fallback.                                                                              | `:llm` client; DD-33                                         |
| **Format-preserving export**             | Export re-emits the book only in its original format; no cross-format conversion path exists.                                                                                                                                                                                           | Round-trip golden tests; DD-30                               |
| **SQLite / KV persistence**              | Durable state is SQLite (WAL) with Flyway-managed schema; preferences use the typed `settings(key,value,type)` KV table; writes are atomic.                                                                                                                                             | ArchUnit `no-sql-except-persistence`; `:persistence`         |

## what-is-deliberately-not-here {#what-is-not-here}

Retired by ADR-0016 — do not reintroduce any of these as a done-gate:

- `./gradlew traceCheck` with zero orphans, and the `./gradlew trace` regeneration that fed it.
- `docs/traceability.yaml` (the generated record) and its freshness fingerprint.
- `// Proves: STORY-NNN-AC-N` test markers.
- Story front-matter equality checks (`acceptance_criteria` matching `### STORY-NNN-AC-N` body headings).
- Story-status transitions and the `done`-is-immutable rule. An archived OpenSpec change plays that role; follow-up
  work is a new change.
