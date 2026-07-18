---
name: testing-javafx
description: >-
  Use when writing tests for BookLoom — choosing the test tier (unit, persistence,
  provider-WireMock both dialects, golden round-trip, pipeline e2e, UI widget/screen/state,
  UI-matches-mockup conformance, i18n, smoke, and the local-only liveLocal / promptEval /
  visual tagged sets), using JUnit 5
  + AssertJ + Mockito, WireMock for the LLM HTTP seam, TestFX + Monocle (headless) for UI,
  the `// Proves: STORY-NNN-AC-N` convention, coverage targets, and running
  `./gradlew test`. Covers what to test where, keeping the offline invariant, and
  regenerating/validating traceability.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Testing (JavaFX + core)

Tests prove acceptance criteria and hold the architecture invariants. Every acceptance
criterion has a passing test that names it; the traceability chain binds spec clause ->
story -> AC -> test -> module.

## When to use

- Writing unit, integration, UI, or ArchUnit tests for a story's acceptance criteria.
- Mocking the LLM HTTP seam with WireMock, or a port with Mockito.
- Writing a headless TestFX control-state or screenshot assertion.
- Adding `// Proves:` tags and running `./gradlew test` / `trace` / `traceCheck`.

## When NOT to use

- Do NOT hit a real network or a real model in the automated suite — the LLM seam is WireMock
  only; the offline invariant must hold (no unsolicited network calls). The one exception is the
  `liveLocal` set, which is env-gated, manual, and excluded from CI/`check`.
- Do NOT use Testcontainers — SQLite is embedded; use a temp DB file or `:memory:`.
- Do NOT put JavaFX/TestFX in a core-module test — only `:ui` (and `:app`) touch FX.
- Do NOT assert on private internals when an AC is a public contract (P3) — assert the
  `Result` envelope and `ErrorCode`.
- Do NOT write a test without a `Proves:` tag if it proves an AC — it will orphan the AC.

## Test tiers

| Tier | Scope | Tools |
|---|---|---|
| Unit | one class, ports mocked | JUnit 5, AssertJ, Mockito 5 |
| Persistence integration | DAO + real DB, Flyway applied | JUnit 5, temp SQLite / `:memory:` |
| Provider integration | provider + HTTP, both dialects | JUnit 5, WireMock (LLM) |
| Pipeline e2e | small whole book through the engine | JUnit 5, stub/WireMock provider |
| ArchUnit | module boundaries, FX-free core, ports-not-concretes, cycles | ArchUnit (`archTest` source set) |
| Golden | per-format document round-trip canonical equality (DD-43; TXT exact bytes) | JUnit 5 over fixtures |
| UI widget | one control's state + bindings | TestFX + Monocle (headless) |
| UI screen/state | each screen's enumerated states/dialogs | TestFX + Monocle (headless) |
| UI conformance | matches the mockup: structure, palette tokens, coverage checklist (P6) | TestFX + Monocle (headless) |
| i18n | EN default + UK bundle completeness, locale selection, DB switch | JUnit 5 over resource bundles |
| Smoke | app boots (injector + two-phase init); jpackage image launches | JUnit 5 (boot), packaging matrix |
| Live-local (`liveLocal`) | real prompt/request/response vs real servers | JUnit 5, real Ollama + LM Studio (env-gated, NOT CI) |
| Prompt eval (`promptEval`) | production prompt builder vs real model, embedding-scored rubric | JUnit 5, real local model (env-gated, NOT CI) |
| Visual (`visual`) | pinned-env snapshot + tolerant diff of key screens × themes | TestFX snapshot (nightly/on-demand, NOT the merge gate) |

Full taxonomy and CI-vs-local split: `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`.

## Workflow

1. **Pick the tier from the AC pattern.** P1 Given/When/Then and P2 state transition ->
   unit/integration; P3 contract -> assert the `Result`/`ErrorCode` shape; P4 rendering ->
   TestFX control-state; P5 guard/negative -> pair with an `EC-` id; P6 visual reference ->
   TestFX/Monocle screenshot matching the mockup screen/state/theme.
2. **Name the proving test.** Put `// Proves: STORY-NNN-AC-N` on the test's first comment
   or name line so the trace generator binds it. One AC's value per test; every `EC-` id in
   the story appears in some proving test.
3. **Unit test with mocks.** Inject mocked `:api` ports via constructor; assert with
   AssertJ. ViewModels are unit-testable without a scene graph (they hold no FX nodes).
4. **Mock the LLM with WireMock — BOTH dialects.** Stub the OpenAI-compatible endpoints
   (`/v1/chat/completions`, `/v1/models`) AND the Ollama-native endpoints (`/api/chat`,
   `/api/tags`, `/api/show`) to exercise: request shape per dialect (nullable-param omission,
   `num_ctx` on the Ollama `options` block, reasoning low/off); response handling
   (`<think>`-stripping and code-fence/preamble sanitization, tolerant parse with unknown
   fields ignored + missing defaulted, the single repair retry, the plain-text fallback);
   the three-stage verification; discovery fallback to manual model-ID entry; retry on typed
   retryable errors (`timeout`/`rateLimited`/`unreachable`/`upstream`), Retry-After handling,
   per-attempt timeout, and each HTTP -> `ErrorCode` mapping. This is the ONLY network seam in
   the automated suite.
5. **Test persistence against a temp DB.** Use a temp SQLite file or `:memory:` with Flyway
   migrations applied (as in two-phase init); assert settings KV round-trips including the
   `ui.language` key and the per-project provider/model snapshot. No Testcontainers.
6. **Test documents with golden fixtures.** Parse -> reassemble without changing target
   text -> assert **canonical equality** with the fixture (DD-43): compare canonicalized
   forms — re-parse-equal / canonical-XML equal; EPUB by decompressed canonical content +
   preserved entry order + mimetype-first/STORED, unchanged binaries by decompressed bytes;
   **TXT exact bytes** — minus intentional language metadata. Never raw-byte-diff the
   structured formats.
7. **Drive a pipeline e2e.** Run a small whole book through `:pipeline` against a
   deterministic stub/WireMock provider; assert the accepted/flagged outcome distribution
   (both branches exercised deterministically), a flagged residual reaching Review,
   checkpoint/resume restoring mid-run state (re-entering at the **first PENDING** segment;
   FLAGGED is terminal-for-run), and same-original-format export, canonical-equal.
8. **Test UI headless — widget, screen/state, and conformance.** TestFX + Monocle.
   - **Widget:** assert one control's state and its viewmodel binding (P4); assert long work
     marshals state via the mirror's `Platform.runLater` wrapping.
   - **Screen/state:** drive the viewmodel/navigator into each enumerated state from
     `01_Product/08_UI_SCREENS_AND_STATES.md` (Import ×4, Translating ×3, Projects/Review
     populated-vs-empty, each dialog in the modal host) and assert the expected controls,
     banners, and empty states.
   - **Conformance (P6):** back a **screen/state coverage checklist** (one entry per screen +
     enumerated state/dialog/toast/banner/empty-state) with a proving test each, plus a
     **mockup-vs-checklist diff meta-test** (a surface in the mockup but not the checklist
     fails, and vice versa). Use **structural** look-ups (control present, mapped JavaFX
     type) with "**wired**" asserted **behaviourally**, not by inspecting bindings:
     property → node (drive the viewmodel property / `publish*` and assert the node
     updates) and node → command (fire the control with TestFX and assert the viewmodel
     command/port was invoked via a Mockito spy). Add **looked-up-colour** assertions that
     `.root` tokens resolve to the full token catalogue (`09_THEMING.md#token-catalog`:
     Charcoal `#3a4a52`, Slate `#b2babd`, Sand Dollar `#e7d6c0`, Cognac `#a58075`
     + desaturated status colours, per-role light AND dark values) in both value blocks.
     Prefer structural + palette-token assertions over pixel diffing (pixel snapshots live
     in the non-CI `visual` set). Accessibility checks (contrast, keyboard reach, names,
     target size) are **advisory/best-effort — never a merge gate**
     (`03_NonFunctional/04_ACCESSIBILITY.md`).
9. **Test i18n (ICU-aware, DD-48).** Assert `messages_en` (default) and `messages_uk` have
   identical key sets with no missing keys; ICU MessageFormat patterns are valid and UK
   plural keys cover **one/few/many/other**; every key referenced via the typed message-key
   registry is defined (and no dead keys); first-start OS-locale selection through the
   **injectable `Locale` provider** (Ukrainian OS -> `uk`, else English); and the Settings
   -> Appearance switch persists to the `ui.language` KV key. These are hard gates.
10. **Smoke the boot + package.** A boot test builds the injector, runs two-phase init against
    a temp DB, and reaches ready headlessly; the jpackage-image launch check runs in the
    packaging matrix (`03_PACKAGING_JPACKAGE.md#verification`).
11. **Add `liveLocal` per provider feature (NOT CI).** For any client/prompt/response-handling
    change, add a `liveLocal`-tagged test against a real local Ollama + LM Studio; env-gate it
    (`assumeTrue` / `@EnabledIfEnvironmentVariable` on e.g. `BOOKLOOM_LIVE_OLLAMA_URL`,
    `BOOKLOOM_LIVE_LMSTUDIO_URL`) so it skips when unconfigured. Wire it via JUnit tags: the
    default `Test` task `excludeTags("liveLocal", "promptEval", "visual")`; separate
    `./gradlew liveLocal` / `promptEval` / `visual` tasks `includeTags(...)` their own tag
    and are NOT in `check`. Prompt evals (`promptEval`) score the production prompt builder
    against a real local model with an embedding scorer — embeddings exist only in that
    test harness, never in the app runtime.
12. **Guard boundaries with ArchUnit:** `fx-free-core`, `dependency-direction`,
    `ports-not-concretes`, `no-http-in-core-except-llm`, `no-sql-in-core-except-persistence`,
    `api-is-framework-free`, `records-first`, `bootstrap-no-static-logger`. A failing
    ArchUnit test fails the build.
13. **Run and record.** `./gradlew test` green; then `./gradlew trace` (regenerate
    `docs/traceability.yaml`) and `./gradlew traceCheck` (zero orphans, fresh record).

## TestFX + Monocle headless setup

- UI tests only touch FX in `:ui` (and `:app`); core-module tests stay FX-free.
- Run headless (no display server) by setting the Monocle system properties on the test JVM:
  `-Dtestfx.robot=glass -Dglass.platform=Monocle -Dmonocle.platform=Headless`
  (plus `-Dprism.order=sw -Djava.awt.headless=true`). Set these in the JavaFX/test convention
  plugin so CI and local runs match; a missing property hangs the UI test.
- Start the FX toolkit once per suite (a TestFX `ApplicationTest` or a shared `@BeforeAll`
  toolkit init); construct controllers through the Guice controller factory so viewmodels/
  ports are injected as in production.
- **Widget** tests mount one control/component; **screen/state** tests load a full screen and
  drive it into each enumerated state; **conformance** tests look up controls by id/type and
  read `.root` looked-up colours for the palette-token assertions. Marshal any off-thread state
  through the mirror's `Platform.runLater` wrapping and pump the FX queue before asserting.

## Coverage targets

- JaCoCo branch ~80% on core modules (`:api`, `:util`, `:document`, `:llm`, `:pipeline`,
  `:persistence`). `:ui` is excluded from the coverage threshold (covered behaviourally by the
  TestFX widget/screen/conformance tests). The `liveLocal` set is excluded from coverage.
- Optional PIT mutation testing on `:document` / `:pipeline`.

## Running

- `./gradlew test` — full automated suite including headless TestFX/Monocle (CI runs the same);
  excludes `liveLocal`, `promptEval`, and `visual`.
- pre-push runs unit tests excluding UI/TestFX + the fast ArchUnit subset (< 60s).
- `./gradlew liveLocal` — the local-only live-provider set against a real Ollama + LM Studio;
  env-gated, skips when unconfigured, NOT part of `check`/CI.
- `./gradlew trace` / `./gradlew traceCheck` — regenerate / validate traceability.

## Reference index

- In-repo authorities: `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md` (the
  full test taxonomy + CI-vs-local split), `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
  `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md` (screens/states/dialogs for the
  conformance checklist), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md` (both client
  dialects + response handling), `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`
  (ArchUnit rules), `docs/specification/02_Architecture/09_ERROR_HANDLING.md`.

## Mandatory validation checklist

- [ ] Every AC has a passing test carrying `// Proves: STORY-NNN-AC-N` (exact id).
- [ ] Every `EC-` id in the story appears in some proving test.
- [ ] The correct tier is used for each AC pattern (P1..P6).
- [ ] LLM interactions use WireMock only, stubbing BOTH the OpenAI-compatible and Ollama-native
      endpoints; response handling (`<think>`-strip, tolerant parse, repair, text fallback) is
      covered; no real network in the automated suite; offline invariant intact.
- [ ] Persistence uses temp SQLite / `:memory:` with Flyway applied; no Testcontainers.
- [ ] Document golden round-trip (per format, canonical-equal; TXT exact bytes) and a
      pipeline e2e (accepted/flagged + first-PENDING resume + same-format export) exist.
- [ ] Core-module tests are FX-free; UI widget, screen/state, and mockup-conformance tests are
      TestFX + Monocle headless, with palette-token/looked-up-colour and coverage-checklist
      assertions.
- [ ] i18n: `messages_en` + `messages_uk` key sets identical, OS-locale first-start selection,
      `ui.language` DB switch.
- [ ] Smoke: app boots (injector + two-phase init) and jpackage image launches headlessly.
- [ ] `liveLocal`/`promptEval`/`visual` tests (if added) are env-gated, skip when
      unconfigured, and excluded from `check`/CI; a11y checks are advisory, not gating.
- [ ] ArchUnit boundary tests are green.
- [ ] JaCoCo branch ~80% on core met; `:ui` and `liveLocal` excluded.
- [ ] `./gradlew test` green; `./gradlew trace` + `traceCheck` pass with zero orphans.

## Gotchas

- A `Proves:` typo silently orphans the AC — `traceCheck` fails; match the id exactly.
- TestFX on CI needs Monocle headless (`-Dtestfx.robot=glass -Dglass.platform=Monocle
  -Dmonocle.platform=Headless`); a missing property hangs the UI test.
- Assert typed errors by `ErrorCode`, not by message text — messages are not contract.
- Retry tests must assert NON-retryable codes (`auth`, `modelNotFound`, `contextWindow`,
  `validation`, `emptyCompletion`) are NOT retried, and retryable ones are.
- The round-trip golden asserts CANONICAL equality, not bytes (DD-43): entity/quote/
  whitespace normalization by jsoup/CommonMark/zip is allowed, but any structural, ID,
  encoding, or text drift fails. Only TXT is compared byte-for-byte.
