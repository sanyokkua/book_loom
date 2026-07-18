**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/02_STORY_FORMAT.md`, `docs/implementation_plan/03_TRACEABILITY.md`,
`docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`, `docs/specification/mockups/ui-mockup.html`

# Definition of Done

A story is `done` (see `docs/implementation_plan/02_STORY_FORMAT.md#lifecycle`) only when **every** item below is true.
`done` is immutable. Two things must hold: the **per-story checklist** (this story's work is complete and proven) and
the **always-true architecture invariants** (the story left the system's guarantees intact).

## per-story-checklist {#per-story-checklist}

- [ ] **Every acceptance criterion has a passing proving test** whose first line is `// Proves: STORY-NNN-AC-N` (see
  `docs/implementation_plan/03_TRACEABILITY.md#proves-convention`).
- [ ] **Every edge case** in the story's `edge_cases` front-matter has a covering test carrying
  `// Proves: EC-<AREA>-N`.
- [ ] **`acceptance_criteria` front-matter equals** the set of `### STORY-NNN-AC-N` body headings.
- [ ] **The entire build, lint, and format gate is green — `./gradlew clean build check spotlessCheck` passes with zero
  findings across the whole project, not just touched code.** There is **no "pre-existing" exemption**: if any
  mechanical check (Spotless/Palantir 120-col, Checkstyle, Error Prone + NullAway (JSpecify `@NullMarked`), SpotBugs +
  FindSecBugs, ArchUnit, tests) fails anywhere — even in code the story did not write — the story is **not done** until
  it is fixed (as part of this story or a cited prerequisite one). A red gate is never carried forward or waved through.
  This clean-gate requirement is an **implicit acceptance criterion of every story**.
- [ ] **`./gradlew test` green** (unit + integration; UI tests green where the story touches `:ui`).
- [ ] **The test types the story implies are present and green** per `04_Build_and_Release/06_TESTING_STRATEGY.md`:
  provider stories add WireMock tests for **both** dialects (Ollama-native + OpenAI-compatible) covering the
  response-handling contract; document stories add round-trip golden tests; pipeline stories add e2e coverage; `:ui`
  stories add widget + screen/state + mockup-conformance tests; i18n-affecting stories keep English/Ukrainian bundle
  parity.
- [ ] **Provider-related stories add or update a `liveLocal` test** (env-gated, excluded from CI) exercising the real
  local provider (s) — Ollama and/or LM Studio.
- [ ] **ArchUnit boundary tests green** (`./gradlew check`) — FX-free core, dependency direction, ports-not-concretes,
  no-http-except-`:llm`, no-sql-except-`:persistence`, api-framework-free, records-first.
- [ ] **`./gradlew traceCheck` passes with zero orphans and a fresh record** (`./gradlew trace` re-run after the last
  change).
- [ ] **Module inventory updated** in `docs/implementation_plan/01_MODULE_INVENTORY.md` if the story added a module or
  package.
- [ ] **UI stories match the mockup visual reference (P6)** for each named screen/state/theme against
  `docs/specification/mockups/ui-mockup.html`.
- [ ] **No new background/unsolicited network calls** — the offline invariant holds (only user-triggered provider
  communication — inference, model discovery, verification — with the configured provider).
- [ ] **ADRs cited** (if the story took an architecturally significant decision) exist under `docs/adr/` and are
  `accepted`.
- [ ] **Cited spec clauses resolve** and the story is the live (non-superseded) prover of its chain.
- [ ] **Story status set to `done`** and any story it supersedes moved to `superseded`.

## always-true-architecture-invariants {#always-true-architecture-invariants}

These hold after **every** story, not just the one that introduced them. ArchUnit and the checks above enforce most of
them; a story that would break one is wrong.

| Invariant                                | Statement                                                                                                                                                                                                                                                                               | Enforced by                                                  |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| **FX-free core**                         | No class in `:api`/`:util`/`:document`/`:llm`/`:pipeline`/`:persistence` depends on `javafx..`. Only `:ui` and `:app` require JavaFX.                                                                                                                                                   | ArchUnit `fx-free-core`; JPMS                                |
| **Result / AppError envelope**           | Fallible operations return `Result{data,error}` with a typed `AppError`/`ErrorCode` and a safe-details allowlist; partial results supported. No leaking exceptions across ports.                                                                                                        | `:api`; code review; P3 ACs                                  |
| **Records for data, Lombok on services** | Data carriers are Java records; no `@Data`/`@Value`. DTO/`api` packages contain records only. Services may use Lombok `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` (DD-05, ADR-0014).                                                                                                 | ArchUnit `records-first` (carrier packages)                  |
| **Token-only theming**                   | The UI styles exclusively via looked-up color tokens on `.root`; light + dark from one token set. No hard-coded colors in controls/FXML.                                                                                                                                                | `:ui/ua.bookloom.ui.theme`; review; P6 ACs                   |
| **Offline invariant**                    | No code path issues a network call other than user-triggered provider communication (inference, model discovery, verification) with the configured provider. Tests run against WireMock in isolation.                                                                                   | ArchUnit `no-http-in-core-except-llm`; WireMock isolation    |
| **Skeleton not regenerated**             | The document skeleton is never sent to the model and never regenerated; only text nodes change. Round-trip is structure-and-text-preserving (canonical-equal) — exact bytes may differ under re-serialization; TXT compares exact bytes (modulo intentional language metadata) (DD-43). | Round-trip golden tests (canonicalized compare); `:document` |
| **Credentials as reference**             | Secrets are stored only as a reference (env-var name / OS keychain entry), never the secret value; secrets never logged or placed in error details.                                                                                                                                     | `:persistence/...secret`; FindSecBugs; review                |
| **Single-flight gate**                   | All inference is serialized through the `InferenceGate` so a local model serves one request at a time.                                                                                                                                                                                  | `:llm/ua.bookloom.llm.gate`; pipeline uses the gate          |
| **Verified provider before inference**   | No inference (run or diagnostic) is issued before connection + model availability are verified; a project resumes on its own bound provider/model, prompting before any fallback or settings-driven change.                                                                             | `:llm` verify; resume flow; DD-31                            |
| **Response-handling contract**           | Model responses are handled JSON-first and tolerantly: structured-output request where supported, reasoning/`<think>`/fence strip, unknown-field tolerance, one repair retry, deterministic text fallback.                                                                              | `:llm` client; DD-33                                         |
| **Format-preserving export**             | Export re-emits the book only in its original format; no cross-format conversion path exists.                                                                                                                                                                                           | Round-trip golden tests; DD-30                               |
| **SQLite / KV persistence**              | Durable state is SQLite (WAL) with Flyway-managed schema; preferences use the typed `settings(key,value,type)` KV table; writes are atomic.                                                                                                                                             | ArchUnit `no-sql-except-persistence`; `:persistence`         |

## phase-level-exit-criteria {#phase-level-exit-criteria}

A **phase** is complete when all its stories are `done` and its `## Phase exit checklist` (in the phase file under
`docs/implementation_plan/phases/`) is satisfied. In addition, every phase must exit with the stage-exit invariants from
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants`: the offline invariant intact; all
touched code passing Spotless/Checkstyle/Error Prone+NullAway/SpotBugs; ArchUnit boundary tests green; `./gradlew test`
green; `./gradlew traceCheck` green with zero orphans; document-touching phases keep the round-trip golden test green;
UI-touching phases match the mockup visual reference (P6). The forward-compatibility seams (F1–F9) a phase establishes
must exist and be exercised by a test before the phase closes.
