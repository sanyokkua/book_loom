# CLAUDE.md — BookLoom

Operating manual for building this project. Read this first; it is binding.

## What this app is

A **local-first, offline desktop application** that translates whole books (EPUB, FB2, Markdown, TXT) with a
general-purpose LLM the user runs locally (Ollama / LM Studio) or points at any OpenAI-compatible endpoint. It parses
each book into a structured XML/AST **skeleton** and an ordered list of **segments**, translates only the visible text
while keeping structure, images, fonts and IDs intact — a structure-and-text-preserving (canonical-equal) round trip;
exact bytes may differ under re-serialization (DD-43) — and runs **automatically end-to-end** — typically ~99% of a book
completes with no human interaction (a non-gating aspiration). A tiered pipeline (draft → deterministic QA →
LLM-as-judge → self-heal repair; optional whole-book consistency pass) keeps names, tone and formatting consistent; only
the small minority of chunks it cannot clear are flagged for optional side-by-side review. Nothing leaves the machine
except user-triggered provider communication (inference, model discovery, verification) with the configured provider.

## Non-negotiable invariants (always true)

- **FX-free core.** Only `:ui` and `:app` may `requires javafx.*`. `:api :util :document :llm :pipeline :persistence`
  are JavaFX-free — enforced by JPMS + ArchUnit.
- **Skeleton is never regenerated.** Only text nodes change; structure/IDs/images/fonts survive a
  structure-and-text-preserving (canonical-equal) round trip — the golden test compares canonicalized output to
  canonicalized source, not raw bytes; TXT is exact (DD-43). Inline tags and locked terms are masked to `⟦gN⟧`,
  validated on return (tag-multiset hard gate), then unmasked; inline code and inline MathML mask as single atomic
  protected placeholders, while `<pre>`/block-code listings and block-level math are non-translatable blocks excluded
  from segmentation entirely (DD-49).
- **Uniform error envelope.** Boundaries return `Result<T>{data,error}` with one typed
  `AppError{code,title,message,details,retryable,cause}` + `ErrorCode`. `details` never contains secrets. Partial
  results carry both data and error.
- **Records for data, Lombok on services.** Data carriers are Java `records` (never `@Data`/`@Value`); services may use
  Lombok `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` (DD-05, ADR-0014). `Optional` as return type only;
  `requireNonNull` at public boundaries.
- **Credentials are a reference, never a secret.** Persist an env-var name or an OS-keychain reference; resolve just
  before the call; never store or log the secret.
- **Single-flight inference.** One `InferenceGate` (`Semaphore(1)`, `tryAcquire`) guards all inference (real runs and
  diagnostics). Retry is service-owned, keyed on typed retryable errors, honours `Retry-After`, fresh timeout per
  attempt.
- **Token-only theming.** Looked-up colors on `.root`; light + dark from one token set; never inline
  `node.setStyle(...)`.
- **Offline.** No background/unsolicited network, no telemetry; the only outbound traffic ever is user-triggered
  provider communication (inference, model discovery, verification) with the configured provider.
- **Acceptance model.** The review-mode dial (Unattended/Assisted/Manual) is the sole owner of the trust threshold τ;
  the quality dial (Fast/Balanced/Max) owns mechanics only (chunk size, context depth, repair budget, judge on/off,
  backward revision). Accept = hard gates pass ∧ confidence ≥ τ ∧ (judge off ∨ judge score ≥ τ_judge) (DD-45).
- **UI thread discipline.** Scene graph only on the JavaFX Application Thread; long work in `Task`/`Service` off an
  injected daemon executor; `Platform.runLater` to bridge; virtual threads for I/O fan-out; no `synchronized` in new
  code.
- **Persistence.** SQLite (WAL) via JDBI, Flyway additive migrations, a generic typed KV settings table; atomic
  per-chunk commits — process-crash-safe and forced-quit-safe; on OS crash/power loss at most the last in-flight commit
  may be lost, never earlier accepted work (DD-20). The single-instance lock (`bookloom.lock`) is acquired pre-injector
  by `:app`/`:util`, not by `:persistence`; a second launch shows an "already running" dialog and exits.
- **Paths first.** A `:util` resolver picks the per-OS data/log dirs (Windows `%LOCALAPPDATA%`, macOS
  `Application Support`+`Logs`, Linux XDG data+state) with dev builds under a `-Dev` sibling; it runs **before** logging
  and SQLite (DD-39, `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`).
- **Green gate, no exceptions.** Every story leaves `./gradlew clean build check spotlessCheck` green project-wide; a
  red mechanical check is never excused as "pre-existing" (`06_DEFINITION_OF_DONE.md`).

## Modules (Gradle subprojects = JPMS modules; base package `ua.bookloom.<module>`)

```
:util → :api
:document → :api, :util
:llm      → :api, :util
:pipeline → :api, :util, :document, :llm
:persistence → :api, :util
:ui  → :api, :document, :llm, :pipeline, :persistence, javafx.*      (only :ui/:app see JavaFX)
:app → everything; Launcher + Application + Guice composition root
```

`:api` = contracts (interfaces, records/DTOs, `Result`/`AppError`) — the dependency floor, framework-free. Canonical
package/paths: `docs/implementation_plan/01_MODULE_INVENTORY.md`.

## How work is tracked

1. **`docs/specification/` is frozen** during implementation — the binding requirements/architecture/decisions. Never
   edit it to make a story easier; a genuine gap → a new ADR in `docs/adr/`, then a story.
2. Work is delivered as **phases** (`docs/implementation_plan/phases/`) → **stories** (`docs/stories/`, one per
   session) → tests → traceability → Definition of Done.
3. **Two commands drive it (both plan-first, end with `ExitPlanMode`):**
    - `/plan-phase-stories-creation <PHASE>` — plan the story set for a phase; on approval the `architect` writes the
      story files.
    - `/plan-user-story-implementation <STORY>` — plan one story's implementation + tests; on approval `coder` +
      `tester` execute.
4. **Traceability** links spec-clause → story → acceptance-criterion → test → module. Every AC has a proving test naming
   it (`// Proves: STORY-NNN-AC-N`). Run `./gradlew trace` then `./gradlew traceCheck` before a story is `done`. Record:
   `docs/traceability.yaml` (generated — never hand-edit).
5. **Definition of Done:** `docs/implementation_plan/06_DEFINITION_OF_DONE.md`.

## Configuration inventory

**Rules** (always-on invariants, `.claude/rules/`): `architecture-layering`, `java-coding-style`, `javafx-ui`,
`gradle-build-and-quality`, `error-envelope`, `llm-provider-integration`, `document-roundtrip`, `persistence-sqlite`,
`threading-concurrency`, `logging`, `testing`, `offline-and-privacy`, `theming-tokens`, `traceability-and-stories`.

**Skills** (`.claude/skills/`): `story-and-traceability-workflow`, `adr-authoring`, `javafx-ui-designer`,
`llm-provider-integration`, `document-pipeline`, `testing-javafx`, `create-mermaid-diagrams`, `project-navigator`.

**Agents** (`.claude/agents/`): `investigator` (read-only mapper), `architect` (writes stories + ADRs only), `coder`
(implements), `tester` (proving tests + trace), `spec-conformance-reviewer` (read-only gate), `docs-writer` (docs),
`debugger` (diagnose failures).

**Commands** (`.claude/commands/`): `plan-phase-stories-creation`, `plan-user-story-implementation`.

## Build & test commands

```
./gradlew build            # compile + spotlessCheck + lint + test
./gradlew test             # unit + integration (excludes the UI/TestFX task in the fast gate)
./gradlew spotlessApply    # format (also run by the pre-commit hook)
./gradlew spotlessCheck    # format check (CI)
./gradlew check            # full quality gate: Spotless, Error Prone/NullAway, Checkstyle, SpotBugs, ArchUnit, tests
./gradlew trace            # regenerate docs/traceability.yaml
./gradlew traceCheck       # validate traceability (must be clean before a story is done)
./gradlew :app:collectDist # stage app jar + runtime classpath for packaging
scripts/package-<os>       # jpackage CLI per OS (.app+.dmg / portable zip / tar.gz+.deb); no cross-compile
```

## Stack (decided — see `docs/adr/`)

Java 25 (LTS) · JavaFX 25 (LTS) · Gradle (Kotlin DSL) + version catalog · Guice DI (constructor injection) · SQLite
(sqlite-jdbc + WAL) + Flyway + JDBI · JDOM2/dom4j + jsoup + `java.util.zip` (documents) · a CommonMark library
(Markdown) · `java.net.http` + Jackson (LLM HTTP/JSON) · Lingua + ICU4J (language detect / segmentation) · SLF4J +
Logback · Spotless (Palantir) + Error Prone/NullAway + Checkstyle + SpotBugs + ArchUnit · Lefthook · JUnit 5 + AssertJ +
Mockito + WireMock + TestFX/Monocle · jpackage (unsigned, per-OS). MIT license; permissive dependencies only.
