**Status:** Final **Owner:** architect **Audience:** architect, engineering, QA **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/adr`

# Design Decisions

This is the lightweight decision log. Each entry states the settled decision, its rationale, and its consequences. Heavy
decisions are mirrored as ADRs under `docs/adr/`. Decisions are facts; requirement IDs point to where each decision is
realized.

## dd-01-local-first-offline {#dd-01-local-first-offline}

### DD-01 — Local-first, offline by default

**Decision:** The application runs fully offline; the only outbound traffic is user-triggered provider communication —
inference, model discovery, and verification — with the configured local or OpenAI-compatible provider. **Why:** Book
content and translations must stay under the user's control; there is no account or backend. **Consequences:** No
telemetry, analytics, background updates, or remote crash reporting; an offline invariant must be enforced and testable.
**ADR:** ADR-0010 **Requirements:** FR-INFER-01, NFR-OFFLINE-01, NFR-PRIV-01.

## dd-02-java-javafx {#dd-02-java-javafx}

### DD-02 — Java 25 (LTS) + JavaFX 25 (LTS)

**Decision:** Implement in Java 25 with a JavaFX 25 desktop UI. **Why:** A single mature cross-platform runtime with
strong tooling, records, virtual threads, and a native-feeling desktop toolkit. **Consequences:** jpackage-based native
delivery; only `:ui`/`:app` may depend on JavaFX. **ADR:** ADR-0001 **Requirements:** FR-UI-01, NFR-PORT-01.

## dd-03-gradle-kotlin-dsl {#dd-03-gradle-kotlin-dsl}

### DD-03 — Gradle (Kotlin DSL) build

**Decision:** Build with Gradle using the Kotlin DSL, a wrapper, `build-logic/` convention plugins, a version catalog,
and committed dependency locks. **Why:** Reproducible, multi-module builds with centralized dependency management.
**Consequences:** Convention plugins own shared config; contributors use the wrapper. **ADR:** — **Requirements:**
NFR-MAINT-01.

## dd-04-guice-di {#dd-04-guice-di}

### DD-04 — Google Guice DI, constructor injection only

**Decision:** Use Guice 7 with constructor injection only, one module per Gradle module, and a single composition root
in `:app`. **Why:** Explicit, testable wiring without field/reflection magic. **Consequences:** Stateless services are
`@Singleton`; no service locator. **ADR:** — **Requirements:** NFR-MAINT-02.

## dd-05-records-first {#dd-05-records-first}

### DD-05 — Records for data carriers, Lombok on services (hybrid)

**Decision:** Model immutable data carriers (DTOs, value objects, wire types) as Java **records** — never `@Data`/
`@Value`. Use **Lombok on service/component classes** to remove boilerplate: `@RequiredArgsConstructor` for constructor
injection of `final` dependencies (works with Guice), `@Slf4j` for loggers, and `@Builder` where a builder genuinely
helps. **Why:** Records give immutable value semantics for carriers and pair well with Jackson and pattern matching;
Lombok removes constructor and logger boilerplate on services without hand-written plumbing. **Consequences:** Carrier
packages stay records-only (ArchUnit-enforced); services may carry the permitted Lombok annotations; Lombok is a build
dependency + annotation processor ordered before Error Prone/NullAway. **ADR:** ADR-0014 **Requirements:** NFR-MAINT-03.

## dd-06-multimodule-jpms {#dd-06-multimodule-jpms}

### DD-06 — Multi-module JPMS with FX-free core

**Decision:** Split into JPMS modules with dependencies pointing inward; only `:ui` and `:app` may require JavaFX,
enforced by JPMS and ArchUnit. **Why:** Keep the document/LLM/pipeline/persistence core headless and testable.
**Consequences:** Boundary tests fail the build on violations. **ADR:** — **Requirements:** NFR-MAINT-04, NFR-PORT-01.

## dd-07-skeleton-segment-model {#dd-07-skeleton-segment-model}

### DD-07 — Skeleton + segment document model

**Decision:** Parse every document into a skeleton plus an ordered segment list; translate text nodes only; guarantee a
**structure-and-text-preserving (canonical-equal) round-trip** — structure, nesting, IDs, images, fonts, encoding, and
all text are preserved, though exact bytes may differ after re-serialization (DD-43). **Why:** Structure preservation is
correct by construction if the skeleton is never semantically regenerated; requiring bit-identical bytes is unachievable
through jsoup/CommonMark/zip and unnecessary. **Consequences:** The skeleton is never sent to the model; the round-trip
golden test asserts canonical/re-parse equality, not raw bytes. **ADR:** ADR-0003 **Requirements:** FR-DOC-01,
FR-DOC-02, FR-EXPORT-02.

## dd-08-scope-formats {#dd-08-scope-formats}

### DD-08 — Scope limited to EPUB/FB2/MD/TXT

**Decision:** Support EPUB, FB2, Markdown, and TXT only; exclude PDF and DOCX. **Why:** These formats allow a clean
text/structure separation for faithful round-tripping. **Consequences:** PDF/DOCX are explicitly out of scope. **ADR:**
ADR-0004 **Requirements:** FR-IMPORT-01, FR-IMPORT-05.

## dd-09-general-instruction-models {#dd-09-general-instruction-models}

### DD-09 — General instruction models over the provider abstraction

**Decision:** Use general-purpose instruction models reached through the provider abstraction — an Ollama-native client
for Ollama and an OpenAI-compatible client for every other server (see DD-10). **Why:** Broadest compatibility with
local runtimes; no bespoke fine-tuned model required. **Consequences:** Provider quirks are handled inside the matching
client (e.g. Ollama `num_ctx` via native `options`), not by callers. **ADR:** ADR-0005 **Requirements:** FR-INFER-01,
FR-INFER-02.

## dd-10-single-provider-impl {#dd-10-single-provider-impl}

### DD-10 — Provider abstraction with two client implementations

**Decision:** A `Provider` port + `ProviderFactory` hide two concrete client implementations selected by provider
**kind** (`OLLAMA | OPENAI_COMPATIBLE`): an **Ollama-native** client (Ollama's own `/api/*` and native `options`) and an
**OpenAI-compatible** client (`/v1/*`, for LM Studio and any OpenAI-shaped server). Per-kind `ProviderProfile` data
still carries endpoint/credential/model details. The abstraction already admits future providers (e.g. Gemini, Claude)
as new implementations without changing callers; those are not implemented in this scope. **Why:** Ollama's
OpenAI-compatible endpoint does not fully honour options such as num_ctx, so Ollama needs its native API; other servers
are well served by the OpenAI-compatible client. **Consequences:** Two client classes behind one interface plus a
per-kind factory; new vendors are new implementations, not caller branches. **ADR:** ADR-0005 **Requirements:**
FR-PROV-02, FR-PROV-03.

## dd-11-credential-as-reference {#dd-11-credential-as-reference}

### DD-11 — Credentials stored as a reference, never the secret

**Decision:** Persist only a reference to a credential (environment-variable name or OS keychain entry), never the
secret value. **Why:** Avoid storing plaintext secrets in the local database. **Consequences:** The secret is resolved
at call time; export/backup never contains secrets. **ADR:** ADR-0006 **Requirements:** FR-PROV-05, FR-PERSIST-05,
NFR-PRIV-02.

## dd-12-inference-gate {#dd-12-inference-gate}

### DD-12 — Single-flight InferenceGate

**Decision:** Serialize inference through a single-flight gate so a local model serves one request at a time. **Why:**
Local single-GPU backends degrade or fail under concurrent calls. **Consequences:** Fan-out I/O is bounded at the model;
the gate is the throughput choke point. **ADR:** ADR-0008 **Requirements:** FR-INFER-06.

## dd-13-service-owned-retry {#dd-13-service-owned-retry}

### DD-13 — Service-owned retry on typed retryable errors

**Decision:** The provider service owns retry logic, keyed on typed retryable errors, honouring `Retry-After` and using
a fresh timeout per attempt. **Why:** Transient failures (rate limits, model load) should not surface to the user.
**Consequences:** Non-retryable errors fail fast as typed `AppError`s. **ADR:** ADR-0008 **Requirements:** FR-INFER-05,
FR-INFER-07.

## dd-14-result-apperror-envelope {#dd-14-result-apperror-envelope}

### DD-14 — Uniform Result / AppError envelope

**Decision:** Every service returns `Result{data, error}` with one typed `AppError`/`ErrorCode`, a safe-details
allowlist, and support for partial results. **Why:** Consistent, user-safe error surfacing across modules.
**Consequences:** No throwing across module boundaries for expected failures. **ADR:** — **Requirements:** FR-NOTIF-04,
NFR-REL-01.

## dd-15-automatic-first {#dd-15-automatic-first}

### DD-15 — Automatic-first pipeline, single trust dial

**Decision:** The pipeline runs automatically end to end; a single trust-threshold dial governs review; human review is
opt-in. **Why:** The core value is a hands-off full-book translation. **Consequences:** Review UI is optional; ~99% of
chunks pass without a human. **ADR:** ADR-0007 **Requirements:** FR-ALGO-01, FR-REVIEW-01.

## dd-16-tiered-self-heal {#dd-16-tiered-self-heal}

### DD-16 — Tiered self-heal fires only on QA failure

**Decision:** Repair uses a directed fix (1 call, concrete findings) or reflect→improve (2 calls) and runs only when a
QA gate fails. **Why:** Spend extra inference only where needed. **Consequences:** Passing chunks incur no repair cost;
repair budget N bounds attempts. **ADR:** ADR-0007 **Requirements:** FR-ALGO-08, FR-QA-02.

## dd-17-deterministic-qa-plus-judge {#dd-17-deterministic-qa-plus-judge}

### DD-17 — Deterministic QA gate + LLM-as-judge; neural QE deferred

**Decision:** Combine deterministic checks with an LLM-as-judge score; defer a neural quality-estimation sidecar.
**Why:** Cheap deterministic gates catch hard failures; the judge scores subtle quality. **Consequences:** Neural QE is
a documented future extension. **ADR:** ADR-0007 **Requirements:** FR-QA-01, FR-QA-02.

## dd-18-consistency-stack {#dd-18-consistency-stack}

### DD-18 — Name dictionary + context-aware TM + rolling summary

**Decision:** Use a name/term dictionary, context-aware translation memory (exact / context / fuzzy via deterministic
string similarity), and a rolling bilingual summary as the consistency stack. **Why:** These
deterministic-plus-lightweight mechanisms give most of the consistency benefit cheaply, with no vector store to build or
ship. **Consequences:** No embeddings and no RAG stage; fuzzy matching is string-similarity, not vectors. The optional
glossary **pre-scan is a general-instruction-model call** (DD-46), not an NER library or embeddings. **ADR:** ADR-0007
**Requirements:** FR-ALGO-05, FR-ALGO-06, FR-ALGO-07, FR-GLOSS-01.

## dd-19-paragraph-chunking {#dd-19-paragraph-chunking}

### DD-19 — Paragraph-grouped chunking, edge-loaded context

**Decision:** Group whole paragraphs into chunks up to the token budget; sentence-split only on overflow; place
load-bearing context at prompt edges; carry a capped preceding-target window. **Why:** Preserve paragraph coherence and
mitigate lost-in-the-middle. **Consequences:** Single oversized paragraphs are split by sentence; preceding-target
window is capped (~3 blocks). **ADR:** ADR-0007 **Requirements:** FR-ALGO-02, FR-ALGO-03, FR-ALGO-04.

## dd-20-sqlite-persistence {#dd-20-sqlite-persistence}

### DD-20 — SQLite (WAL) + Flyway + JDBI, typed KV settings

**Decision:** Persist with SQLite in WAL mode, Flyway migrations, JDBI access, a generic typed
`settings(key,value,type)` KV table, atomic writes, a per-OS data dir, and a single-instance lock. **Why:** Embedded,
robust, zero-admin local storage. **Consequences:** No external DB; schema evolves via migrations; `synchronous=NORMAL`
makes checkpoints process-crash-safe and forced-quit-safe (on OS crash/power loss at most the last in-flight commit is
lost — see reliability); the process single-instance lock is acquired pre-injector by `:app`/`:util`, not by
`:persistence`. **ADR:** ADR-0009 **Requirements:** FR-PERSIST-01, FR-PERSIST-02, FR-SETTINGS-01.

## dd-21-fxml-guice-fx-threading {#dd-21-fxml-guice-fx-threading}

### DD-21 — JavaFX FXML + Guice controller factory + off-thread work

**Decision:** Use FXML views with a Guice controller factory, an observable state mirror, all long work off the FX
thread via `Task`/`Service` + `Platform.runLater`, and virtual threads for I/O fan-out. **Why:** A responsive UI with
testable controllers. **Consequences:** No blocking work on the FX thread; state is mirrored, not shared. **ADR:** —
**Requirements:** FR-UI-02, FR-UI-03.

## dd-22-token-theming {#dd-22-token-theming}

### DD-22 — Token-only theming via looked-up colors

**Decision:** Theme exclusively through looked-up colors on `.root`; define **one set of token *roles* with two value
blocks (light + dark) swapped at `.root`**; use the fixed palette. **Why:** One source of truth for colour; easy
light/dark parity. **Consequences:** No hard-coded colours in controls; palette changes are token edits. **ADR:** —
**Requirements:** FR-UI-05, FR-SETTINGS-04.

## dd-23-slf4j-logback {#dd-23-slf4j-logback}

### DD-23 — SLF4J + Logback rolling file logging

**Decision:** Log via SLF4J + Logback to a rolling file in the per-OS log dir, human-readable, parameterized, MDC
cleared in finally, never logging secrets. **Why:** Local, privacy-safe diagnostics. **Consequences:** No remote log
shipping. **ADR:** — **Requirements:** FR-SETTINGS-06, NFR-PRIV-01.

## dd-24-jpackage-unsigned {#dd-24-jpackage-unsigned}

### DD-24 — Script-driven jpackage per-OS matrix, unsigned, no Windows installer

**Decision:** Package with the plain **`jpackage` CLI driven by committed scripts** (`scripts/jpackage-common.sh` + one
thin script per OS) over a Gradle-collected jar directory (`./gradlew :app:collectDist`) — no Gradle packaging plugin.
Artifact matrix: macOS `.app` tar.gz + `.dmg` (x86_64 + aarch64), **Windows portable app-image zip only — no `.msi`
/installer (no WiX)**, Linux app-image tar.gz + `.deb` (x86_64 + aarch64; `.deb` gracefully skipped without `fakeroot`).
Runtimes are jlink-trimmed (`--strip-debug --no-header-files --no-man-pages --compress zip-6`); packaging JDK is Temurin
except **Liberica Full (`jdk+fx`)** on Windows and Linux-ARM where jpackage needs the JavaFX jmods. All unsigned and
un-notarized, with documented "open anyway" steps. CI is three workflows: `ci.yml` (PR merge gate), `build.yml` (`main`
snapshots, 14-day artifacts), `release.yml` (tag → verify-on-main → matrix → GitHub Release with auto pre-release
detection). **Why:** Proven in the reference desktop project; Windows installers require the WiX toolchain and add
nothing for an unsigned app; scripts keep jpackage flags transparent and locally reproducible. **Consequences:** No
`org.beryx.jlink` dependency; a contributor reproduces any artifact locally with the matching script; users follow
documented Gatekeeper/SmartScreen steps. **ADR:** — **Requirements:** NFR-PORT-02. Normative:
`04_Build_and_Release/03_PACKAGING_JPACKAGE.md`, `04_CI_CD.md`. Edge cases: EC-REL-*.

## dd-25-quality-tooling {#dd-25-quality-tooling}

### DD-25 — Formatter, linters, and hooks

**Decision:** Enforce Spotless + Palantir format; Error Prone + NullAway + Checkstyle + SpotBugs + ArchUnit; Lefthook
git hooks. **Why:** Consistent, safe, boundary-respecting code. **Consequences:** CI fails on security/correctness;
pre-commit auto-formats. **ADR:** — **Requirements:** NFR-MAINT-05.

## dd-26-foreign-passage-policy {#dd-26-foreign-passage-policy}

### DD-26 — Foreign-language passages: keep-as-is by default

**Decision:** Detected foreign-language passages default to keep-as-is behind a feature flag, detected per segment; the
QA "wrong language" check must not fight the policy. **Why:** Intentional foreign text (quotes, epigraphs) should
survive. **Consequences:** QA language checks are policy-aware. **ADR:** — **Requirements:** FR-BRIEF-04, FR-QA-03. Edge
cases: EC-FOREIGN- *, EC-LANG-*.

## dd-27-resume-on-launch {#dd-27-resume-on-launch}

### DD-27 — Resume-on-launch from per-chunk checkpoints

**Decision:** Checkpoint crash-safely per chunk and offer resume on relaunch. **Why:** Long jobs must survive crashes
and quits without redoing work. **Consequences:** Every accepted/flagged chunk is durably persisted before proceeding.
**ADR:** ADR-0009 **Requirements:** FR-RESUME-01, FR-RESUME-02.

## dd-28-mockup-source-of-truth {#dd-28-mockup-source-of-truth}

### DD-28 — The UI mockup is the binding source of truth

**Decision:** `docs/specification/mockups/ui-mockup.html` is the binding visual reference for screens, states, widgets,
and palette. **Why:** One authoritative rendering avoids UI drift. **Consequences:** UI stories cite the mockup via the
P6 pattern; specs reference it rather than restating pixels. **ADR:** — **Requirements:** FR-UI-01, FR-UI-04.

## dd-29-app-icon-and-branding {#dd-29-app-icon-and-branding}

### DD-29 — App icon & branding: single background-removed master, per-OS derivation

**Decision:** The app is named **BookLoom** everywhere (UI, OS package metadata, package `ua.bookloom`, DB
`bookloom.db`, per-OS `BookLoom`/`bookloom` data dirs). The owner-provided glass-tile artwork sits on a near-black
backdrop; a deterministic pipeline (`assets/icon/process_icon.py`) removes the backdrop to a single 1024×1024 RGBA
master (`assets/icon/appicon.png`) with transparent corners, and every per-OS icon (`.icns`/`.ico`/`.png`) is derived
from that one master (`assets/icon/generate_platform_icons.py`) — never hand-forked. jpackage consumes the matching file
per OS. **Why:** Shipping the backdrop would render a dark plate on the macOS Dock, Windows taskbar, and Linux
launchers; one derived master keeps all platforms consistent and reproducible. **Consequences:** A visual change means
replace the source and re-run both scripts; no `.icns`/`.ico`/`.png` is edited by hand. **ADR:** ADR-0011
**Requirements:** NFR-PORT-02. Normative: `04_Build_and_Release/05_ICON_AND_BRANDING.md`. Edge cases: EC-ICON-*.

## dd-30-export-original-format {#dd-30-export-original-format}

### DD-30 — Export preserves the original format only

**Decision:** Export always writes the book back in its **original format** (EPUB→EPUB, FB2→FB2, Markdown→Markdown,
TXT→TXT), structure-and-text-preserving via the skeleton (canonical-equal, DD-43). **Converting between document formats
is out of scope** (it is lossy); the export path offers no target-format choice. **Why:** Cross-format conversion cannot
preserve structure/metadata faithfully and adds tooling and failure modes with little value for a book translator.
**Consequences:** The only export contract to verify is the same-format round trip; no conversion code paths exist.
**ADR:** ADR-0004 **Requirements:** FR-EXPORT-01, FR-EXPORT-02.

## dd-31-per-project-provider-model {#dd-31-per-project-provider-model}

### DD-31 — Per-project provider/model binding with change confirmation

**Decision:** The settings-configured provider and models are **defaults for new projects only**. On creation, a project
copies the current default provider + translator/judge models as its own binding and records a **last-used** snapshot
(provider id + endpoint + model ids). A project reuses its own binding on every resume. On resume the app
**preflight-verifies** connection and model availability; if the bound provider/model is unavailable it prompts and
falls back to the current default **only on user confirmation**; if the settings default differs from the project's
last-used it prompts *apply new vs continue with previous* (default: continue). Verification of connection + model
availability runs **before any inference** (runs and diagnostics). **Why:** Prevent mid-run translation-quality
regression from a silently changed model or provider. **Consequences:** Projects persist a provider/model binding +
last-used snapshot; resume carries explicit confirm dialogs; no inference is issued against an unverified
provider/model. **ADR:** ADR-0012 **Requirements:** FR-PROV-08, FR-PROV-09, FR-PROV-10, FR-INFER-08.

## dd-32-two-clients-abstraction {#dd-32-two-clients-abstraction}

### DD-32 — Two LLM client implementations behind one abstraction

**Decision:** Inference is issued through a `Provider` port with a `ProviderFactory` selecting one of two client
implementations by kind: an **Ollama-native** client and an **OpenAI-compatible** client. The abstraction is defined so
future providers (Gemini, Claude, …) can be added as new implementations without changing callers; none are implemented
in this scope. **Why:** Ollama's OpenAI-compatible endpoint is not fully faithful (e.g. `num_ctx`), so Ollama uses its
native API; all other servers use the OpenAI-compatible client. **Consequences:** Callers never branch on vendor; adding
a provider is adding an implementation. **ADR:** ADR-0005 **Requirements:** FR-PROV-02, FR-PROV-03, FR-INFER-01.
(Extends DD-10.)

## dd-33-response-handling {#dd-33-response-handling}

### DD-33 — JSON-first, tolerant response handling with repair + text fallback

**Decision:** Every model-facing call requests structured output where the client supports it, then: strips
reasoning/thinking blocks (`<think>…</think>` and analogues), code-fence wrappers and stray prose; sets reasoning level
low/off where controllable; parses tolerantly (ignore unknown fields, default missing, trim); on malformed output does
**one** repair retry; then falls back to deterministic plain-text extraction; only a chunk that then fails QA is
flagged. HTTP clients are built once (injected), fresh per-request timeout, record DTOs with `@JsonInclude(NON_NULL)`,
tolerant `ObjectMapper`, never logging secrets or full book text. **Why:** Local models vary — some ignore
structured-output requests, emit reasoning, or add fields; the runtime must be robust to all of it. **Consequences:**
Response handling is a defined, testable contract shared by both clients; unexpected fields are ignored, missing fields
defaulted. **ADR:** ADR-0013 **Requirements:** FR-INFER-09, FR-INFER-10.

## dd-34-ui-languages-en-uk {#dd-34-ui-languages-en-uk}

### DD-34 — UI languages: English (default) + Ukrainian

**Decision:** This version ships exactly two UI languages — **English (default)** and **Ukrainian** (`uk`) — with all
user-facing text in `ResourceBundle`s (`messages_en`, `messages_uk`) kept in parity. First start detects the OS locale
(Ukrainian → `uk`, else English); the choice is stored in the DB (typed KV key `ui.language`) and is user-switchable in
Settings → Appearance (apply-on-change; restart acceptable). **Why:** Deliver a fully localized product for the two
target audiences without over-scoping i18n. **Consequences:** Two complete bundles are a release gate; a third language
is future work, not assumed here. **ADR:** — **Requirements:** FR-I18N-1, FR-I18N-2, FR-I18N-4, FR-I18N-7.

## dd-35-testing-strategy {#dd-35-testing-strategy}

### DD-35 — Comprehensive testing incl. mockup conformance + local-only live provider tests

**Decision:** Testing spans unit, persistence integration, provider integration (WireMock simulating both
OpenAI-compatible and Ollama-native endpoints), document round-trip golden, pipeline e2e, UI widget + screen/state
(TestFX/Monocle), UI-matches-mockup conformance, i18n completeness, and smoke (boot + jpackage image). A separate
**local-only** `liveLocal` set runs manually against real Ollama and LM Studio to validate provider logic, prompts, and
response structures for both clients; it is env-gated and **excluded from CI**. **Why:** The app's correctness spans
documents, providers, pipeline, and a binding UI; local models can only be truly validated against real local servers.
**Consequences:** CI runs everything except `liveLocal`; `liveLocal` is expected per provider-related feature.
**ADR:** — **Requirements:** NFR-MAINT-05, FR-UI-01. Normative: `04_Build_and_Release/06_TESTING_STRATEGY.md`.

## dd-36-temperature {#dd-36-temperature}

### DD-36 — Low default temperature, per-phase guidance, user-adjustable

**Decision:** Recommend a **low** sampling temperature for fidelity: default **0.2**, with per-phase guidance (draft ~
0.2; judge/QA ~0.0–0.2; repair/reflect may go slightly higher). It is user-adjustable in Generation settings (range
0.0–2.0), and a per-retry "lower temperature" option exists. **Why:** Low temperature yields more deterministic, less
hallucinated translations. **Consequences:** Defaults favour fidelity; users can trade for variability knowingly.
**ADR:** — **Requirements:** FR-SETTINGS-03.

## dd-37-chunking-and-prompts {#dd-37-chunking-and-prompts}

### DD-37 — Detailed chunking + a normative prompt catalog

**Decision:** Chunk creation/division/translation is specified in detail (paragraph-grouped up to a token budget derived
from `num_ctx`; sentence-split only on overflow; masked inline tags never split; edge-loaded context; capped
preceding-target window) with a worked example, and every pipeline LLM call has a concrete system + user prompt
template, injected variables, required/optional fields, parameters, and output shape in a normative prompt catalog.
**Why:** Reliable, reproducible translation requires the chunking and prompting to be fully specified, not implied.
**Consequences:** Implementers build prompts from the catalog; changes to prompts are spec changes. **ADR:** ADR-0007
**Requirements:** FR-ALGO-02, FR-ALGO-03, FR-ALGO-04. Normative: `01_Product/12_PROMPT_CATALOG.md`.

## dd-38-manual-model-id {#dd-38-manual-model-id}

### DD-38 — Manual model-ID entry when discovery is unavailable

**Decision:** `ProviderProfile` carries a `supportsModelDiscovery` capability. When discovery is absent, fails, is
unauthenticated, or returns empty, the user enters model IDs **manually** (free-text), and manual entry is always
available as an override. **Why:** Some providers expose no model-listing endpoint; configuration must never be blocked.
**Consequences:** The Models UI and add/edit-provider dialog always allow manual model IDs; discovery is a convenience,
not a requirement. **ADR:** ADR-0005 **Requirements:** FR-MODEL-01, FR-MODEL-02.

## dd-39-app-environment-and-paths {#dd-39-app-environment-and-paths}

### DD-39 — Per-OS app paths, resolved first, with dev/prod separation

**Decision:** A small hand-rolled resolver in `:util` (no third-party dir library / JNA) resolves the per-OS data dir
(SQLite DB) and log dir — Windows `%LOCALAPPDATA%\BookLoom`, macOS `~/Library/Application Support/BookLoom` +
`~/Library/Logs/BookLoom`, Linux `$XDG_DATA_HOME/bookloom` + `$XDG_STATE_HOME/bookloom/logs` — via injected env/property
seams. Development builds resolve to a `-Dev`/`-dev` sibling folder (selected by an `isDev` signal: `BOOKLOOM_ENV` env →
`-Dbookloom.env=prod` build stamp → `jpackage.app-path` presence → default dev), so a dev build never collides with
production. Path resolution + directory creation + the single-instance lock run **before** logging and SQLite
initialize. **Why:** Logging needs the log dir and SQLite needs the data dir, so this is foundational; dev/prod
separation prevents data corruption during development. **Consequences:** A dedicated foundation task precedes
persistence; the resolver is a pure, testable function; no native dependency. **ADR:** ADR-0015 **Requirements:**
FR-PERSIST-03, FR-PERSIST-06, FR-PERSIST-04. Normative: `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`.

## dd-40-prompt-evals {#dd-40-prompt-evals}

### DD-40 — Local prompt evals as unit tests, layered scoring, embeddings test-only

**Decision:** Every pipeline prompt has a **local-only** eval implemented as a JUnit test (tag `promptEval`, excluded
from CI, env-gated to skip when no local server is present). Each eval calls the **real production prompt builder** to
produce the system+user messages, sends them to a real Ollama/LM Studio model, and scores the response with a **layered
rubric**: (0) deterministic structural hard-fails (masked-tag multiset preserved, no reasoning/`<think>` leakage, JSON
shape); (1) field-level assertions on structured output; (2) **embedding cosine similarity** vs a reference answer,
calibrated per model and advisory-by-default; (3) an optional local LLM-as-judge tie-breaker in the ambiguous band. Runs
at temperature 0 with multi-sample averaging and threshold margins. **Embeddings appear only in this test harness —
never in the app runtime** (preserves DD-18). Default eval embedding model is a small multilingual one (e.g.
`embeddinggemma:300m`); chat model defaults to the smallest viable, configurable per story. **Why:** Prompts are
load-bearing and must be regression-tested against real local models; cosine alone is a drift signal, so structural
checks catch what it hides. **Consequences:** A test-scope embedding client (java.net.http + Jackson, no new runtime
dep); reference-answer fixtures maintained as test resources; evals are advisory tripwires, not a substitute for human
review. **ADR:** — **Requirements:** NFR-MAINT-05, FR-ALGO-02. Normative: `04_Build_and_Release/06_TESTING_STRATEGY.md`,
`01_Product/12_PROMPT_CATALOG.md`.

## dd-41-visual-ui-validation {#dd-41-visual-ui-validation}

### DD-41 — Visual/rendered UI validation (there is no Playwright for JavaFX)

**Decision:** Rendered-UI validation is layered: (1) **primary, deterministic, headless** — looked-up-color/token
assertions per theme and layout/geometry (bounds/overlap/clipping) assertions via TestFX+Monocle; (2) **secondary** —
`Scene.snapshot(...)`→PNG with a **tolerant/perceptual** image diff, run only in a **pinned** environment (software
pipeline, fixed DPI/font/window size), tagged `visual`, kept out of the default gate (nightly/on-demand); (3) **assisted
review** — OS-level screenshots of the real app scored by a local vision model (e.g. qwen3-vl / gemma4) against a
rubric, on-demand for late phases, advisory only. Robot screen-grabs need a real display or Xvfb (Monocle-headless grabs
return black). **Why:** No Playwright equivalent exists for JavaFX; strict pixel diffing is fragile across OS/DPI/font,
so token/geometry assertions are the stable core and pixel/vision checks are pinned/advisory. **Consequences:** Visual
checks are required for the late UI phases but are CI-optional and environment-locked; baselines are regenerated
deliberately. **ADR:** — **Requirements:** FR-UI-01, FR-UI-04. Normative: `04_Build_and_Release/06_TESTING_STRATEGY.md`.

## dd-42-candidate-models {#dd-42-candidate-models}

### DD-42 — Candidate model guidance (non-final)

**Decision:** The spec records a **candidate, non-final** set of local models and their capabilities to guide defaults
and testing: structured-output-friendly, no-hard-reasoning models (e.g. ministral-3, granite4, gemma4:12b, qwen3.5) are
preferred defaults; models with always-on or non-disableable reasoning (qwen3.5/3.6 thinking, gemma4:26b-a4b MoE,
gpt-oss:20b) rely on the response-handling text-fallback and reasoning-strip (DD-33); vision models (qwen3-vl) are not
used for text translation but may serve the visual-eval vision reviewer (DD-41). For eval embeddings, a small
multilingual model (`embeddinggemma:300m`, or `bge-m3` for long inputs) is the default. **Why:** The app must behave
across a moving field of local models with different output/reasoning capabilities; recording capability guidance keeps
defaults sane without hard-coding a model. **Consequences:** The capability matrix is guidance, not a supported-list
guarantee; users may configure any compatible model. **ADR:** — **Requirements:** FR-MODEL-01, FR-MODEL-03, DD-09.
Detail: `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`.

## dd-43-canonical-round-trip {#dd-43-canonical-round-trip}

### DD-43 — Round-trip fidelity is canonical/semantic equality, not byte-identity

**Decision:** Export re-serializes from the parsed model; the guarantee is **structure-and-text-preserving canonical
equality** — element/attribute nesting, IDs, images, fonts, encoding, and all text are preserved, but exact bytes may
differ (entity/attribute-quote/whitespace normalization by jsoup/CommonMark/zip is allowed). The **golden round-trip
test** compares canonicalized output to canonicalized source per format (EPUB: decompressed canonical content +
preserved entry order + mimetype-first/STORED, unchanged binaries by decompressed bytes; Markdown: re-parse-equal AST;
TXT: exact byte-for-byte; FB2: canonical XML). Only text nodes change; structure is never semantically regenerated.
**Why:** Bit-identical output is unachievable through the chosen parse/serialize libraries and unnecessary — "opens
identically, structure and text intact" is the real requirement. **Consequences:** All "byte-faithful/byte-identical"
wording is replaced by canonical-equal; the golden gate compares canonical forms. **ADR:** ADR-0003, ADR-0004
**Requirements:** FR-DOC-02, FR-EXPORT-01, FR-EXPORT-02. (Refines DD-07/DD-30.)

## dd-44-token-budget-heuristic {#dd-44-token-budget-heuristic}

### DD-44 — Token budget by heuristic + per-provider effective context

**Decision:** No tokenizer is shipped. Tokens are estimated with a deterministic `chars ÷ K` heuristic (per-script K
table + a 0.15 safety margin). The **effective context window** is a per-provider value resolved: Ollama `num_ctx`/
`/api/show` → discovery → a manual "effective context (tokens)" provider field → a conservative default. The Generation
chunk-budget setting is a **cap**: `chunkBudget = min(effectiveContext − reservedHeadroom, chunkBudgetSetting)`.
**Why:** The user's model tokenizer is unknown and OpenAI-compatible servers don't expose a context size; a heuristic +
explicit per-provider value is deterministic and offline. **Consequences:** `providers.effective_context` column; K
table + margin + rolling-summary K are fixed constants, not settings. **ADR:** — **Requirements:** FR-ALGO-02,
FR-INFER-02, FR-MODEL-06a.

## dd-45-acceptance-model {#dd-45-acceptance-model}

### DD-45 — Acceptance model: review-mode dial owns τ; deterministic confidence gate

**Decision:** The **review-mode dial** (Unattended/Assisted/Manual) is the sole owner of the trust threshold τ; the
**quality dial** (Fast/Balanced/Max) owns only mechanics (chunk size, preceding-target count, repair budget N, judge
on/off, backward-revision on/off) and never sets τ. Manual Settings τ / τ_judge are advanced overrides of highest
precedence. Accept = `hardGatesPass ∧ confidence ≥ τ ∧ (judgeOff ∨ judgeScore ≥ τ_judge)`, where `confidence` is a
documented weighted blend of the soft deterministic-QA check margins (hard gates excluded; the judge score is not folded
into confidence). The judge returns `{score, verdict}`; `score ≥ τ_judge` decides, `verdict` is advisory. **Why:** τ had
three owners and three meanings; the accept gate must be single-sourced and testable, including when the judge is off.
**Consequences:** Quality-dial mapping loses its τ row; τ_judge defaults to τ. **ADR:** ADR-0007 **Requirements:**
FR-ALGO-01, FR-QA-02, FR-REVIEW-02.

## dd-46-glossary-llm-pre-scan {#dd-46-glossary-llm-pre-scan}

### DD-46 — Glossary seeded by an LLM pre-scan (deterministic fallback)

**Decision:** Names/terms with type and provisional **gender** are proposed by a dedicated **LLM pre-scan call** (a
normative prompt-catalog entry), surfaced user-editable in the Names & Style step. The offline/disabled fallback is
deterministic frequency + capitalization extraction with `gender = unknown` (filled later by the user or backward
revision). This is an app-runtime model call, distinct from the eval-only embeddings. **Why:** Type/gender are not
deterministically derivable from source text without an LLM, and the app forbids NER libraries/embeddings (DD-18).
**Consequences:** One more model call in Phase B; `glossary.gender` allows `unknown`. **ADR:** ADR-0007
**Requirements:** FR-GLOSS-01, FR-ALGO-05.

## dd-47-metadata-nav-alt-translation {#dd-47-metadata-nav-alt-translation}

### DD-47 — Translate metadata, ToC/nav labels, and image alt (toggle group)

**Decision:** In scope: translate **book metadata title/author, Markdown frontmatter values, image `alt`, and EPUB nav (
EPUB3)/NCX (EPUB2) ToC labels**, controlled by a Book-Brief **"Also translate" toggle group** (defaults: ToC/nav on, alt
on, metadata title/author on, frontmatter values off). These are modelled as a synthetic **metadata unit** with kinds
`METADATA_TITLE/METADATA_AUTHOR/FRONTMATTER_VALUE/ALT/NAV_LABEL` anchored to OPF/frontmatter/attribute/nav nodes;
nav/NCX is carved out of the "out-of-spine = verbatim" rule. **Why:** An untranslated ToC/metadata over a translated
book is a visible defect. **Consequences:** New segment kinds + toggle group + nav/NCX handling. **ADR:** ADR-0004
**Requirements:** FR-DOC-07, FR-BRIEF-04.

## dd-48-icu-i18n-messages {#dd-48-icu-i18n-messages}

### DD-48 — ICU MessageFormat for UI i18n (plurals/gender)

**Decision:** UI text uses **ICU4J `MessageFormat`** for plural/gender-sensitive strings so Ukrainian one/few/many/other
forms render correctly; bundles store ICU patterns; the i18n/parity test is ICU-aware (identical keys, pattern validity,
UK plural categories). Locale-aware number/date/size/duration formatting uses `NumberFormat`/`java.time` per locale; an
**injectable `Locale` provider** makes first-start OS-locale detection unit-testable; a **typed message-key registry**
(no bare string literals) makes the referenced-key check enumerable. **Why:** Plain `ResourceBundle` cannot express
Ukrainian plural grammar, and the UI is count-heavy. **Consequences:** ICU4J (already a dependency) also drives i18n
messages. **ADR:** — **Requirements:** FR-UI-06, FR-I18N-*.

## dd-49-code-and-technical-content-preservation {#dd-49-code-and-technical-content-preservation}

### DD-49 — Preserve code, math, and technical markup verbatim

**Decision:** Beyond inline tags, locked terms, selective numerals, and URLs, the following are **preserved verbatim and
never translated** (confirmed by the technical-book corpus, which is saturated with them): inline `<code>` (masked as an
atomic protected placeholder, its text kept exactly), block code listings `<pre>`/`<pre><code>` (a non-translatable
block — never a segment, skeleton-preserved), **MathML `<math>`**, and index-term/cross-reference anchors (targets/ids
preserved; visible link text stays translatable). Admonitions/sidebars (`note`/`tip`/`warning`/`sidebar`) are ordinary
translatable prose blocks; table cells and figure captions are translatable segments with structure preserved. **Why:**
Technical books contain thousands of inline code spans and code listings; translating identifiers/keywords/math corrupts
the book, and naive masking would explode the placeholder multiset. **Consequences:** The masking classifier gains a
"code/math protected" category; `<pre>` blocks are excluded from segmentation and from the token budget; the
translated-vs-preserved table is extended. **ADR:** ADR-0003 **Requirements:** FR-DOC-04, FR-DOC-05.

## dd-50-version-injection {#dd-50-version-injection}

### DD-50 — App version injected from the git tag (build-generated resource)

**Decision:** The **git tag is the single source of truth for the app version**; no version constant is hand-maintained
anywhere. Because Java has no link-time injection (go_text's `-ldflags -X` has no JVM equivalent), the release pipeline
passes `-PappVersion=<full>` to Gradle, whose `generateVersionResource` task (wired into `:app` `processResources`)
writes a `version.properties` classpath resource; a small `AppVersion` reader surfaces it in exactly **two places** —
the About dialog and a single startup log line. The default is **`dev`** (local/IDE/snapshot builds pass nothing), so
`dev` in About proves a non-release build. The **numeric** `X.Y.Z` goes separately to `jpackage --app-version`
(`APP_VERSION` env) for the OS package metadata — the analogue of go_text's `wails.json` patch — while the **full**
version (incl. `-rc` suffixes) names artifacts and fills the resource. The release packaging smoke asserts the launched
artifact logs the tag version (EC-REL-7). **Why:** One tag drives binary, About, OS metadata, and artifact names with no
drift; a generated classpath resource works identically in `gradlew run`, tests, and the jpackaged image (unlike the
jar-manifest `Implementation-Version`, null off-jar). **Consequences:** A `generateVersionResource` task with declared
inputs; the reader never caches a second copy; version mismatch is a release-blocking failure. **ADR:** —
**Requirements:** FR-UI-09, NFR-PORT-02. Normative: `04_Build_and_Release/01_BUILD_AND_TOOLING.md#version-injection`.
