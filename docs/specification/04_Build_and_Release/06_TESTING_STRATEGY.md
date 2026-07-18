**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
`docs/specification/04_Build_and_Release/04_CI_CD.md`,
`docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md`

# Testing Strategy

This document fixes the complete test taxonomy for BookLoom: every test type that must exist, what each covers, the
tools it uses, and where it runs — in CI (the merge gate) or local-only. It is the normative companion to the quality
gates (`02_QUALITY_GATES.md`), which decides where a failure blocks the build, and to the CI pipeline (`04_CI_CD.md`),
which runs the automated set. Testing is comprehensive by design: correctness of the FX-free core is proven at the seam
that actually breaks, the document round-trip is proven **canonical-equal** (structure-and-text-preserving) per format,
a whole small book is driven end-to-end, the UI is proven headlessly against the mockup — **structure, tokens, and
geometry gated in CI; pixel fidelity nightly** — and both LLM client dialects are proven against simulated and — locally
only — real servers. Every test that proves an acceptance criterion carries a `// Proves: STORY-NNN-AC-N` marker so
`traceCheck` binds it (`.claude/rules/testing.md`).

The single stack is JUnit 5 + AssertJ + Mockito 5 for assertions and collaborators; WireMock for the LLM HTTP seam; a
real temporary SQLite database with Flyway for persistence; and TestFX + Monocle (headless) for the UI. No
Testcontainers — SQLite is embedded (`06_DATA_MODEL_SQLITE.md`). No test contacts a real network except the local-only
live set, which is env-gated and excluded from CI.

## test-types {#test-types}

Every type below is first-class and required. Each is added alongside the feature it proves, not deferred.

### unit {#unit}

Unit tests exercise a single class with its ports mocked, using JUnit 5 + AssertJ assertions and Mockito 5 for
collaborators. They cover pure domain logic and service behaviour: masking/unmasking, chunk budgeting, tolerant JSON
parsing and sanitization helpers, the `Result`/`ErrorCode` envelopes, string-similarity translation-memory matching,
glossary application, and viewmodel command logic (viewmodels hold no scene-graph nodes, so they are unit-testable
without FX — `07_UI_ARCHITECTURE_JAVAFX.md#mvvm`). Only I/O and non-determinism (network, clock, filesystem, randomness)
are mocked; the class under test and pure domain logic are never mocked. Runs in CI.

### persistence-integration {#persistence-integration}

Persistence integration tests run the real DAO stack against a **real temporary SQLite database** — a temp file or
`:memory:` — with **Flyway migrations applied** exactly as the app applies them in two-phase init
(`10_DI_AND_LIFECYCLE.md`). They prove the DDL and JDBI DAOs together: settings KV round-trips (including the persisted
`ui.language` key), project/segment/glossary/TM/summary stores, the provider/model snapshot bound per project, atomic
writes, and that every committed `V{ver}__{desc}.sql` migration applies cleanly on an empty database and is never edited
after the fact. No Testcontainers, no external database. Runs in CI.

### provider-integration-wiremock {#provider-integration-wiremock}

Provider integration tests exercise `:llm` at the **HTTP seam with WireMock**, mocking the transport rather than the
`Provider`/client class, so request shaping, response parsing, retry, `Retry-After`, per-attempt timeout, and every
HTTP→`ErrorCode` mapping are covered against a real HTTP client. The seam is stubbed for **both** dialects
(`04_LLM_INTEGRATION.md`):

- **OpenAI-compatible endpoint** — `/v1/chat/completions`, `/v1/models`, with `response_format` structured output.
  Covers LM Studio and any other OpenAI-compatible server.
- **Ollama-native endpoint** — `/api/chat`, `/api/tags`, `/api/show`, with the native `options` block (`num_ctx`,
  `keep_alive`, `format`, `think`).

These tests assert the **request shape** per dialect (correct paths; nullable params omitted via
`@JsonInclude(NON_NULL)` rather than serialized as `null`; `num_ctx` sent only on the Ollama options block; reasoning
set low/off for translation and judge calls) and the full **response-handling contract**
(`04_LLM_INTEGRATION.md#response-handling`): parsing the structured shape, **`<think>…</think>`-stripping** and
analogous reasoning/code-fence/preamble sanitization, **tolerant parse** (unknown fields ignored, missing optional
fields defaulted, JSON located within surrounding prose), the **single repair retry** on malformed JSON, and the
deterministic **plain-text fallback** when output stays unparseable. Discovery fallback to manual model-ID entry
(unavailable/empty/unauthorized `/v1/models` or `/api/tags`) and the three-stage verification
(`#three-stage-verification`) are proven here too. This is the ONLY network seam in the automated suite. Runs in CI.

### document-roundtrip-golden {#document-roundtrip-golden}

Golden round-trip tests prove per-format **canonical/semantic equality** (structure-and-text-preserving, not
byte-identical): parse a fixture book → reassemble with **zero segment edits** → assert the output is
**canonical-equal** to the input (`03_DOCUMENT_MODEL.md#golden-round-trip-test`). The comparison canonicalizes before
diffing — re-parse-equal / canonical-XML equal — so entity, attribute-quote, and whitespace normalization by
jsoup/CommonMark/zip is allowed, while structure, element/attribute nesting, IDs, images, fonts, encoding, and **all
text** must be preserved. EPUB entries compare by **decompressed canonical content** honouring preserved entry order and
the mimetype-first/STORED rule; unchanged binary entries compare by decompressed bytes (recompression differences
allowed). The invariant that **only text nodes change** and the skeleton is never semantically regenerated still holds;
any structural, encoding, ID, or text drift fails the test. Fixtures cover the enumerated edge cases (`EC-EPUB-*`,
`EC-FB2-*`, `EC-MD-*`, `EC-VERSE-*`, `EC-INLINE-*`, `EC-IMG-*`, `EC-FONT-*`), proving the skeleton path is faithful
before any translation is layered on. Because export re-emits the **original format only** — no format conversion — the
golden test also fixes the export container contract. Runs in CI.

### pipeline-e2e {#pipeline-e2e}

Pipeline end-to-end tests drive a **small whole book** through the real `:pipeline` engine — parse → mask → chunk →
infer through the gate → QA → checkpoint → reassemble → export — against a **stub or WireMock provider** (never a real
model). They assert the outcome distribution (segments **accepted** vs **flagged**), that a flagged residual reaches the
review queue, that resume/checkpoint restores mid-run state (re-entering at the **first PENDING** segment; FLAGGED is
terminal-for-run), and that export writes the book back in its **same original format**, canonical-equal. Deterministic
stub responses make the accepted/flagged assertions repeatable. Runs in CI.

### ui-widget {#ui-widget}

UI widget tests exercise individual controls and their bindings headlessly with **TestFX + Monocle**, asserting control
state (the P4 rendering pattern): a `ToggleSwitch` reflecting its bound property, a `TableView` populated from an
`ObservableList`, a `SegmentedButton`/`ToggleGroup` selection, progress/label bindings on the translating screen, and
that long work marshals state changes onto the FX thread via the observable state mirror's `Platform.runLater` wrapping
(`07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`). No real display server. Runs in CI.

### ui-screen-state {#ui-screen-state}

UI screen/state tests assert each screen's enumerated states render and wire correctly, headless with TestFX + Monocle.
They cover every state named in `08_UI_SCREENS_AND_STATES.md`: Import (`detected-ok`, `language-mismatch`,
`DRM-blocked`, `unsupported`), Translating (`running`, `paused`, `stopped`, `provider-error`), Projects and Review
populated-vs-empty, and each dialog opening in the modal host with scrim. A state test drives the viewmodel/navigator
into the state and asserts the expected controls, banners, and empty states appear — including that the `stopped` state
shows Resume and no error surface (cancellation is not an error). Runs in CI.

### ui-matches-mockup-conformance {#ui-conformance}

UI-conformance tests assert the built UI **matches the binding mockup** (`docs/specification/mockups/ui-mockup.html`)
and the enumeration in `08_UI_SCREENS_AND_STATES.md` — the P6 visual-reference pattern. Rather than pixel diffing,
conformance is proven **structurally and by looked-up colours**, headless under TestFX + Monocle:

- **Screen/state coverage checklist** — a machine-checkable list, one entry per screen and per enumerated state/dialog
  in `08_UI_SCREENS_AND_STATES.md` (Projects +empty, Import ×4 states, Book Brief with its sections including the "Also
  translate" toggle group, Structure, Names & Style, Translating × **4** states incl. `stopped`, Review +empty, Export,
  Settings ×6 tabs; the welcome, add/edit-provider, **the two provider-binding prompts** (bound-unavailable,
  settings-differ), add-glossary-term, retry-with-note, confirm-delete, unsaved-changes, error-with-details,
  export-complete, and about dialogs; toasts `ok/info/warn/err`; banners `info/warn/err`; the three empty states). Each
  entry is satisfied by a proving test that loads the screen/state and asserts its presence — an unlisted screen or an
  uncovered state fails the checklist.
- **Mockup-vs-checklist diff meta-test** — a meta-test parses the screens/states/dialogs enumerated in the mockup and
  diffs them against the checklist above: a surface present in the mockup but absent from the checklist (a
  **mockup-only** surface) **fails**, and vice-versa. This closes the gap where a mockup screen could ship untested
  because no one added its checklist entry.
- **Structural + wiring assertions (behavioural)** — for each screen, look up the controls the mockup shows and assert
  they are present and of the mapped JavaFX type (`control-mapping-summary`). "**Wired**" is asserted **behaviourally**,
  not by inspecting bindings: (a) **property → node** — drive the viewmodel property (or the state mirror's `publish*`)
  and assert the node updates (e.g. `publishJobProgress(...)` updates the progress label/counters; a bound
  `ObservableList` populates the `TableView`); and (b) **node → command** — fire the control (TestFX) and assert the
  viewmodel **command/port was invoked**, using a Mockito **spy** on the viewmodel command or the injected port (e.g.
  clicking Accept invokes the review command; clicking each of the three provider-test buttons invokes its verification
  port; clicking Save-edit invokes the save-revision command). Examples the assertions cover: the add/edit-provider
  dialog's Test connection / models / inference trio; Review's two side-by-side `TextArea`s with Save-edit / Accept /
  Revert-to-machine-target / Retry / Retry-with-note / Skip; Export offering no target-format choice because export is
  original-format only.
- **Palette-token assertions** — assert the theme's **looked-up colour tokens** on `.root` resolve to the mockup's
  palette and the full token catalogue (`01_Product/09_THEMING.md#token-catalog`) — every role's light and dark value
  (surface, border, text, primary/Cognac, nav- *, title-*, status ok/warn/err/info incl. `-bg`/`-bd`, shadow, focus) —
  proving controls reference role tokens rather than hard-coded hex, in **both** value blocks
  (`07_UI_ARCHITECTURE_JAVAFX.md#theming`). A mismatch between the resolved token and the catalogue/mockup value fails (
  "any drift").
- **Accessibility assertions (CI-gated, concrete).** Riding alongside the token checks, over each screen's enumerated
  **primary controls** (`10_I18N_AND_ACCESSIBILITY.md#primary-controls`): **contrast** in both light and dark
  (FR-A11Y-V1), **keyboard reachability + visible focus** in a logical order (FR-A11Y-V2), **accessible name/role**
  non-empty (FR-A11Y-V3), and **target size ≥ 24×24 px** (FR-A11Y-4/V4). These replace any "where practical" hedging —
  they are hard, gated assertions over the enumerated control set. Screen-reader **announcement** of toasts/banners
  (FR-A11Y-8/V5) is **not** claimed in CI; it is a documented **manual/late-phase** check.

Runs in CI.

### i18n {#i18n-tests}

i18n tests prove the two shipped UI languages — **English (default)** and **Ukrainian** (`uk`) — are complete and
correctly selected, and **ICU-aware** (`10_I18N_AND_ACCESSIBILITY.md`, DD-48). They assert:

- **Identical key sets** — `messages_en` and `messages_uk` have the same keys with **no missing keys**, and no key that
  the **typed message-key registry** / a controller/FXML references but no bundle defines (referenced-but-undefined is
  enumerable because keys are typed constants, not literals). The reverse (defined-but-unreferenced) is reported too.
- **ICU pattern validity** — every plural/gender-sensitive value parses as a valid ICU4J `MessageFormat` pattern in its
  locale (a malformed `{count, plural, …}` fails at test time, not runtime).
- **UK plural categories** — Ukrainian plural patterns supply the **one / few / many / other** categories ICU requires
  for `uk`; a pattern missing a required category fails.
- **First-start OS-locale selection** — using a **fake `Locale` provider**, `uk` is picked for a Ukrainian OS locale and
  English otherwise (unit-testable without the machine locale, FR-I18N-4).
- **Persistence** — the chosen language is persisted under the `ui.language` KV key and reloaded on next start, and
  switching language in Settings → Appearance updates the persisted value.

The bundle-completeness and ICU-validity checks are data-driven over the resource files so a newly added key without its
Ukrainian translation, or an invalid ICU pattern, fails. Runs in CI.

### smoke {#smoke}

Smoke tests prove the app comes up: an **app-boot** test builds the Guice injector, runs two-phase init against a temp
DB, and reaches a ready state headlessly without throwing; and a **jpackage-image** smoke confirms the script-built
app-image launches headlessly — must-launch-or-fail, never a skip (`03_PACKAGING_JPACKAGE.md#verification`,
`04_CI_CD.md#packaging-matrix`). These catch composition-root and packaging breakage that unit tests miss. The boot
smoke runs in the CI quality job; the jpackage-image smoke runs in the CI packaging matrix.

## live-local-provider-tests {#live-local}

A separate, **local-only** test set — tag **`liveLocal`** — exercises the real provider logic against **real local
servers**: a running **Ollama** (Ollama-native client) and a running **LM Studio** (OpenAI-compatible client). Unlike
the WireMock provider tests, these hit a live endpoint to confirm that real request/response **structures** hold end to
end: prompt building, the serialized request shape per dialect, structured-output negotiation, reasoning-off behaviour,
and **response sanitization** (`<think>`-stripping, tolerant parse, repair, text fallback) against what a real model
actually emits. They exist because a local model can drift from the mock's assumptions in ways only a live call reveals;
they are added **per provider-related feature** (any change to a client, prompt template, or response handling gets a
`liveLocal` case).

They are **env-gated and excluded from CI**:

- **Env-gated / skipped by default** — each test reads an endpoint from configuration (e.g. `BOOKLOOM_LIVE_OLLAMA_URL`,
  `BOOKLOOM_LIVE_LMSTUDIO_URL`). When the relevant variable is unset, the test is **skipped** via a JUnit 5 assumption
  (`assumeTrue`) / `@EnabledIfEnvironmentVariable`, so a checkout with no local endpoint is green without running them.
- **Excluded from CI and the default gate** — CI runs no live models (`04_CI_CD.md#no-secrets`, offline posture), so the
  `liveLocal` set is never part of `check` or the merge gate.

**Gradle wiring** — the `liveLocal` set is isolated so it can never leak into `check`/CI. Either wire it as a **JUnit 5
tag** plus a dedicated Gradle task that includes only that tag while the default `test`/`check` tasks **exclude** it, or
place it in a **separate `liveLocal` source set** with its own task. Concretely, in
`bookloom.test-conventions.gradle.kts` (`01_BUILD_AND_TOOLING.md#convention-plugins`): the default `Test` task sets
`useJUnitPlatform { excludeTags("liveLocal", "promptEval", "visual") }`, and separate registered tasks (`liveLocal`,
`promptEval`, `visual`) each `includeTags(...)` their own tag, are **not** wired into `check`, and are invoked manually
(`./gradlew liveLocal` / `promptEval` / `visual`). Each documents its required env vars and no-ops (all-skipped) when
they are absent. The same exclusion keeps the prompt-eval and visual sets out of the merge gate.

## prompt-evals {#prompt-evals}

Every pipeline prompt has a **local-only prompt eval** implemented as a JUnit test (tag **`promptEval`**, excluded from
CI, env-gated like `liveLocal`). The eval calls the **same production prompt builder** the app uses to produce the
`{system, user}` messages, sends them to a real local model, and scores the response with a **layered rubric** (DD-40).
Embeddings appear **only here**, in the test harness — never in the app runtime (DD-18).

- **Layer 0 — structural, deterministic, hard-fail.** Masked-tag multiset preserved (exact set equality of `⟦gN⟧`
  placeholders), **no reasoning/`<think>`/code-fence leakage**, JSON parses and has the required shape, output non-empty
  and length-sane. These catch the errors similarity hides (e.g. a dropped tag or a leaked reasoning block).
- **Layer 1 — field assertions on structured output.** For judge/directed-fix steps: verdict/enum in the allowed set,
  score in `[0,1]`, arrays present. Assert on fields, not prose.
- **Layer 2 — embedding cosine similarity vs a reference answer.** Advisory-by-default, for free-text steps (draft,
  reflect-rewrite). Embed output and reference (L2-normalize → dot product), calibrate a per-model threshold from a
  small labelled good/bad set, and assert against `threshold − margin`. Cosine is a **drift/aboutness** signal, not a
  correctness oracle.
- **Layer 3 — local LLM-as-judge tie-breaker.** Only in the ambiguous cosine band: reference-guided binary PASS/FAIL
  from a local model, low temperature, structured output, order/verbosity-bias mitigated. Advisory.

Step verdict = AND of the hard layers, and (cosine passes OR judge PASS). **Nondeterminism** is handled with temperature
0 (+ pinned `seed`/`top_p`/`num_predict` where supported), N-sample averaging for sampled steps, and threshold margins;
Layers 0/1 are hard-fail, Layers 2/3 advisory until a step's bands are proven separable.

**Embedding client (test-scope only)** — `java.net.http` + Jackson, no new runtime dependency: Ollama `POST /api/embed`
(`{model,input}` → `embeddings[0]`) or LM Studio `POST /v1/embeddings` (`{model,input}` → `data[0].embedding`); vectors
L2-normalized before cosine. **Model defaults** — a small **multilingual** embedder, default `embeddinggemma:300m`
(`bge-m3` for long inputs); chat model the smallest viable, both configurable per test/story via `EVAL_CHAT_MODEL` /
`EVAL_EMBED_MODEL` / `EVAL_BASE_URL` (`04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, DD-42). **Reference-answer
fixtures** live as test resources (`src/test/resources/eval/<step>/<case>.json` with
`{sourceText, config, referenceAnswer, threshold, hardFail}`), each step also carrying a couple of **negative fixtures**
(dropped negation/tag, reasoning-leak) asserted to fail, which guard the harness itself. Reusable pieces:
`EmbeddingScorer`, `ChatClient`, `StructuralChecks`, `PromptEvalHarness`, optional `LlmJudge`. **Caveat (documented in
the eval module):** these are prompt-regression tripwires and A/B aids on a dev box, calibrated per model+version —
**not** a substitute for human review of translation quality. Env-gated + `promptEval`-tagged → skipped when no local
server is present and never in CI; added per prompt as prompts are built.

## visual-validation {#visual-validation}

Rendered-UI validation catches styling/layout/theming defects that structural conformance cannot (DD-41). **There is no
Playwright equivalent for JavaFX**, so it is layered:

- **Primary — deterministic, headless, CI.** The looked-up-colour/token and **layout/geometry** assertions of
  `#ui-conformance` and `#ui-screen-state` (bounds, no-overlap, no-clipping, alignment ratios) are the stable core —
  they survive OS/DPI/font differences because they assert *resolved styles and relationships*, not pixels.
- **Secondary — snapshot + tolerant diff, pinned, non-CI.** For a curated set of key screens × states × **themes**,
  capture via `Scene.snapshot(...)`/TestFX `captureNode` (scene-graph render, headless-safe) and compare with a
  **tolerant/perceptual** diff (TestFX `PixelMatcher` or an SSIM/non-match-ratio budget), emitting the diff image as an
  artifact. Runs only in a **pinned** environment (software Prism pipeline, fixed window size, forced 1× DPI, bundled
  test font), tagged **`visual`**, kept out of the default gate (nightly/on-demand); baselines are regenerated
  deliberately via an explicit switch. Robot **screen-grabs** (not scene-graph snapshots) return black under
  Monocle-headless and need a real display or **Xvfb**.
- **Assisted review — vision model, late-phase, advisory.** OS-level screenshots of the **real running app** scored by a
  local vision model (e.g. `qwen3-vl` / `gemma4` via the app's own Ollama/LM Studio setup) against a rubric ("labels
  clipped? contrast legible? accent token applied? spacing consistent?"). Slow and non-deterministic, so it is an
  **on-demand assisted-review** for release candidates and the late UI phases — a report with flagged screenshots for a
  human to confirm, never a blocking assertion. This is the closest available substitute for "computer-use" visual
  validation.

Visual validation is **required for the late UI phases** (`PHASE_09`–`PHASE_11`, `PHASE_13`) but is CI-optional and
environment-locked; strict pixel diffing across the OS matrix is deliberately avoided.

## ci-vs-local {#ci-vs-local}

Which test types run where — the automated set is the merge gate (`02_QUALITY_GATES.md#ci-gates`); the live set is
manual.

| Test type                                                        | CI (merge gate)                                                                         | Local-only                   |
|------------------------------------------------------------------|-----------------------------------------------------------------------------------------|------------------------------|
| Unit                                                             | yes                                                                                     | —                            |
| Persistence integration (temp SQLite + Flyway)                   | yes                                                                                     | —                            |
| Provider integration (WireMock, both dialects)                   | yes                                                                                     | —                            |
| Document round-trip golden                                       | yes                                                                                     | —                            |
| Pipeline e2e (stub/WireMock provider)                            | yes                                                                                     | —                            |
| UI widget (TestFX + Monocle)                                     | yes                                                                                     | —                            |
| UI screen/state (TestFX + Monocle)                               | yes                                                                                     | —                            |
| UI-matches-mockup conformance (token/geometry, headless)         | yes                                                                                     | —                            |
| i18n (EN default + UK bundles)                                   | yes                                                                                     | —                            |
| Smoke — app boot                                                 | yes                                                                                     | —                            |
| Smoke — jpackage app-image                                       | yes (`build.yml`/`release.yml` packaging matrix — runs on `main`/tags, not the PR gate) | —                            |
| Visual snapshot + tolerant diff (`visual`, pinned env)           | **no** (nightly/on-demand)                                                              | yes (pinned)                 |
| Visual vision-model assisted review (real app screenshots)       | **no**                                                                                  | yes (on-demand, late phases) |
| Prompt evals (`promptEval`, real local model + embedding scorer) | **no** (excluded)                                                                       | yes (env-gated, per prompt)  |
| Live-local provider (`liveLocal`, real Ollama + LM Studio)       | **no** (excluded)                                                                       | yes (env-gated, manual)      |

The pre-push hook runs the fast subset — unit tests excluding UI/TestFX plus the fast ArchUnit subset — and the full
automated suite (including headless UI and coverage) runs in the CI quality job (`02_QUALITY_GATES.md#lefthook-stages`).
`liveLocal` runs only when a developer invokes it against local servers.

## coverage-and-traceability {#coverage-traceability}

JaCoCo branch coverage is kept at ~80% on the core modules (`:api`, `:util`, `:document`, `:llm`, `:pipeline`,
`:persistence`); `:ui` is excluded from the coverage threshold because it is covered behaviourally by the TestFX
widget/screen/conformance tests (`02_QUALITY_GATES.md#tools`). Every test that proves an acceptance criterion carries
`// Proves: STORY-NNN-AC-N`, and each enumerated edge case (`EC-<AREA>-<N>`) appears in some proving test;
`./gradlew traceCheck` fails on any orphan or stale record (`.claude/rules/testing.md`). The `liveLocal` set is excluded
from coverage and traceability gating — it verifies real-server behaviour, not acceptance criteria that must hold
offline.
