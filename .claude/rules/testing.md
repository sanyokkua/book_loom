# Testing

Scope: `**/src/test/java/**` and the shared `arch-test` source set, all modules. Stack: JUnit 5 + AssertJ + Mockito 5; WireMock for the LLM HTTP seam; real temp SQLite + Flyway for persistence; TestFX + Monocle (headless) for UI. Spec: `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md` (the full test taxonomy) and `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md` (where a failure blocks the build).

The required test types are: **unit**; **persistence integration** (real temp SQLite + Flyway); **provider integration via WireMock** simulating BOTH the OpenAI-compatible (`/v1/*`) and Ollama-native (`/api/*`) endpoints; **document round-trip golden** (canonical-equal, per format; TXT exact bytes); **pipeline e2e** (a small whole book through the engine against a stub/WireMock provider); **UI widget** and **UI screen/state** (TestFX + Monocle); **UI-matches-mockup conformance**; **i18n** (EN default + UK bundles); and **smoke** (app boot + jpackage image). All of these run in **CI** (the merge gate). Three tagged sets are **excluded from CI/`check`**: **`liveLocal`** (real local servers — see below), **`promptEval`** (prompt evals against a real model + embedding scorer), and **`visual`** (pinned-environment snapshot diffs); accessibility checks are **advisory, not a gate** (`docs/specification/03_NonFunctional/04_ACCESSIBILITY.md`).

## MUST

- **MUST** write tests with **JUnit 5 + AssertJ** assertions and **Mockito 5** for collaborators. — Rationale: one consistent, fluent test stack.
- **MUST** mark every proving test with its acceptance criterion on the **first line** as `// Proves: STORY-NNN-AC-N` (or in the method name/`@DisplayName`), so traceability can attribute it. — Rationale: `./gradlew traceCheck` maps AC → test with zero orphans; see `traceability-and-stories.md`.
- **MUST** name tests `method_state_expected` (e.g. `translateChunk_tagMismatch_returnsValidationError`). — Rationale: the name states the scenario and outcome.
- **MUST** exercise the LLM at the **HTTP seam with WireMock** — mock the transport/endpoint, not the `Provider`/client class — stubbing **both** the OpenAI-compatible (`/v1/chat/completions`, `/v1/models`) and Ollama-native (`/api/chat`, `/api/tags`, `/api/show`) endpoints, so request shaping (nullable-param omission, `num_ctx` on Ollama options, reasoning low/off), response parsing (`<think>`-stripping/sanitization, tolerant parse, one repair retry, plain-text fallback), retry, `Retry-After`, and each HTTP→`ErrorCode` mapping are all covered. — Rationale: the real HTTP behaviour is what breaks; mock the wire, not our code.
- **MUST** cover the document round-trip **golden** test per format — a no-op reassembly whose output is **canonical-equal** to the source: the test compares **canonicalized** forms (re-parse-equal / canonical-XML equal; EPUB decompressed canonical content + entry order + mimetype-first/STORED; **TXT exact bytes**), not raw bytes (DD-43) — and a **pipeline e2e** test that drives a small whole book through the engine against a stub/WireMock provider, asserting accepted/flagged outcomes and same-format export. — Rationale: fidelity and the whole-book flow are proven, not assumed.
- **MUST** test UI with **TestFX + Monocle headless** — **widget** tests (control state, P4), **screen/state** tests (every enumerated state in `01_Product/08_UI_SCREENS_AND_STATES.md`), and **UI-matches-mockup conformance** (structural + looked-up-colour/palette-token assertions, a screen/state coverage checklist, and a mockup-vs-checklist diff meta-test, P6). "**Wired**" is asserted **behaviourally**: drive the viewmodel property / state-mirror `publish*` and assert the node updates (property → node), and fire the control and assert the viewmodel command/port was invoked via a Mockito spy (node → command) — never by inspecting bindings. Accessibility assertions are **advisory/best-effort only**, never a merge gate. — Rationale: UI behaviour and mockup conformance are verified without a display server.
- **MUST** cover **i18n** (ICU-aware, DD-48): `messages_en` (default) and `messages_uk` bundles have identical key sets with no missing keys, ICU MessageFormat patterns are **valid** and UK plural keys cover **one/few/many/other**, every key referenced through the typed message-key registry is defined (and vice versa), first-start OS-locale selection via the **injectable `Locale` provider** (Ukrainian OS → `uk`, else English), and the DB-persisted (`ui.language`) switch. These i18n requirements are **hard** gates. — Rationale: both shipped languages stay complete, grammatical, and correctly selected.
- **MUST** keep a **smoke** test that boots the app (injector + two-phase init against a temp DB) and the jpackage-image launch check in the packaging matrix. — Rationale: composition-root and packaging breakage are caught early.
- **MUST** mock **only** I/O and non-determinism (network, clock, filesystem, randomness); never mock the class under test or pure domain logic. — Rationale: tests exercise real behaviour, not a mirror of the implementation.
- **MUST** keep **JaCoCo branch coverage ~80% on core modules** (`:api/:util/:document/:llm/:pipeline/:persistence`); `:ui` is excluded from the coverage gate. — Rationale: core logic is thoroughly covered; UI is covered behaviourally by TestFX.

## SHOULD

- **SHOULD** cover each edge case (`EC-<AREA>-<N>`) with a test, pairing negative/guard ACs (P5) with their EC id. — Rationale: every enumerated edge case has a proof.
- **SHOULD** use a real temp DB file or `:memory:` with **Flyway migrations applied** for persistence integration tests — **no Testcontainers** (SQLite is embedded). — Rationale: fast, hermetic DB tests that prove the real migration + DAO stack.
- **SHOULD** keep the golden document round-trip tests (`document-roundtrip.md`) as the gate for each format. — Rationale: fidelity is proven per format.
- **SHOULD** add a `liveLocal`-tagged case per provider-related feature (a client, prompt-template, or response-handling change), exercising real prompt building and request/response structures + sanitization for BOTH clients. — Rationale: live behaviour drifts in ways a mock cannot show.

## Local-only (`liveLocal`)

- **`liveLocal` is a separate tagged test set run manually against a REAL local Ollama and LM Studio** — never in CI, never in `check`. It confirms real request/response structures + sanitization for both the Ollama-native and OpenAI-compatible clients.
- **Env-gated:** each test reads its endpoint from configuration (e.g. `BOOKLOOM_LIVE_OLLAMA_URL`, `BOOKLOOM_LIVE_LMSTUDIO_URL`) and **skips** via a JUnit 5 assumption / `@EnabledIfEnvironmentVariable` when unset — a checkout with no local endpoint is green without running them.
- **Gradle wiring:** the default `Test` task uses `useJUnitPlatform { excludeTags("liveLocal", "promptEval", "visual") }`; separate registered tasks (`liveLocal`, `promptEval`, `visual`) each `includeTags(...)` their own tag, are **not** wired into `check`, and are invoked manually (`./gradlew liveLocal` etc.). See `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#live-local`.
- The **`promptEval`** (prompt evals: production prompt builder + real local model + embedding scorer — embeddings exist only in this test harness, never app runtime) and **`visual`** (pinned-env snapshot/tolerant-diff) sets follow the same pattern: tagged, env-gated, excluded from CI/`check`/coverage.

## Reject if

- A proving test has no `Proves: STORY-NNN-AC-N` marker, leaving a traceability orphan.
- The LLM is tested by mocking the `Provider`/client instead of the WireMock HTTP seam, or only one dialect is stubbed (both OpenAI-compatible and Ollama-native are required).
- The class under test or pure domain logic is mocked.
- A UI test needs a real display instead of Monocle headless.
- Testcontainers (or any external DB) is introduced for SQLite tests, or persistence tests skip Flyway migrations.
- A `liveLocal`, `promptEval`, or `visual` test runs in CI / `check`, is not env-gated (does not skip when no endpoint is configured), or is counted toward the coverage/traceability gate.
- A golden round-trip test asserts raw-byte equality for a structured format (EPUB/FB2/MD) instead of comparing canonicalized forms, or an accessibility check is made a blocking merge gate.
- Core-module branch coverage drops below ~80% without justification, or `:ui` is added to the coverage gate.
