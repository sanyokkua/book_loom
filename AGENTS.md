# AGENTS.md — BookLoom

Operating manual for building this project. Read this first; it is binding.

**This is the single source of truth for the project's operating rules.** `CLAUDE.md` imports this file and adds
nothing of its own — never duplicate a rule there, and never edit one there. Every coding agent (Claude Code, Codex,
and anything else) reads the same text.

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

Delivery runs on **OpenSpec** (ADR-0016). There are no story files and no generated traceability.

1. **`docs/specification/` is frozen** during implementation — the binding requirements/architecture/decisions. Never
   edit it to make a change easier; a genuine gap → a new ADR in `docs/adr/`.
2. **The unit of work is an OpenSpec change** under `openspec/changes/<name>/`: `proposal.md`, optional `design.md`,
   `specs/<capability>/spec.md` (EARS requirements + concrete scenarios), `tasks.md` (the implementation checklist).
   `openspec/specs/` starts **empty and grows** — archiving a change folds its requirements in, so it always means
   *what is actually built*, while `docs/specification/` means *what is intended*.
3. **The loop:** pick the next entry from `docs/implementation_plan/CHANGE_BACKLOG.md` → `/opsx:propose` →
   `openspec validate <change> --strict` → `/opsx:apply` → Definition of Done → `/opsx:archive`. Use `/opsx:explore`
   when the shape of the work is unclear and `/opsx:update` to revise a change's plan.
4. **The authoring standard (R1–R6, ADR-0016).** Every artifact must be readable **without opening another file**; IDs
   are citations, never payloads. Requirements in EARS (`WHEN … the system SHALL …`), each with a `Source:` block
   giving the `FR-*` ids **and** a plain-words gloss; scenarios with concrete values (`ErrorCode.validation`, not "an
   error"); task checkboxes as full sentences naming module and spec anchor; tests marked
   `// Covers: FR-* — <one-line EARS restatement>`. Rules live in `openspec/config.yaml` and steer generation.
5. **Coverage is a grep**, not a build task: `bash scripts/fr-coverage.sh`, advisory, run at stage boundaries.
6. **Delivery order:** five stages, 28 changes — infrastructure → round-trip core ∥ UI component library → engine →
   composition → release (`docs/implementation_plan/07_ROADMAP.md`, ADR-0017).
7. **Definition of Done:** `docs/implementation_plan/06_DEFINITION_OF_DONE.md`.

**Retired — never reintroduce:** `docs/stories/`, the story format, `docs/traceability.yaml`, `./gradlew trace`,
`./gradlew traceCheck`, and `// Proves: STORY-NNN-AC-N` markers.

## Always end a turn with the next step

**Every turn that advances the work ends with a `## Next step` block.** No exceptions — not after a research answer,
not after a partial task run, not after a failure. The user should never have to work out where they are in the
OpenSpec loop or what to type; losing the thread between sessions is the single most common way this workflow decays.

The block has exactly three parts:

```markdown
## Next step

**State:** <where the work actually is, one line — cite the evidence, e.g. "tasks 1.1–1.8 checked, 2.x untouched">
**Command:** `/opsx:apply`   ← or the exact shell command, or "none — decision needed from you"
**Prompt:**
> <a complete, copy-pasteable prompt that steers the next agent correctly — names the change, the artifacts to
> read first, and the specific constraint most likely to be violated at this step>
```

Rules for the block:

- **Be honest about state.** "All 55 tasks written" is not "all 55 tasks verified". Say which. If the green gate has
  not been run, the state is *unverified*, whatever the checkboxes say.
- **The prompt must be self-contained**, exactly as R1–R4 demand of every other artifact here. It names the change,
  the files to load, and the trap. `"continue"` is not a prompt.
- **If the next step is a decision, not a command**, say so and state the options with a recommendation. Never invent
  a command to look productive.
- **One step, not a plan.** Suggest the immediate next action, not the next five.

### The state → next-step table

| Where the work just landed | Command | What the prompt must carry |
|---|---|---|
| Nothing in flight | `/opsx:propose` | The backlog entry name and number, its capability + NEW/MOD, the phase file and FR area to read as source material |
| The shape of the change is genuinely unclear | `/opsx:explore` | The question being resolved and what a good answer would let us decide |
| Artifacts generated, unreviewed | *(review — no command)* | The five R1–R5 checks to apply, and that `openspec validate` will **not** catch a lazy `Source:` block or an abstract scenario |
| Artifacts reviewed and good | `openspec validate <change> --strict` | — |
| Validation failed, or review found the plan wrong | `/opsx:update` | Which artifact is wrong, why, and that the four artifacts must stay coherent — do not hand-patch one |
| Validated, not started | `/opsx:apply` | The change name, which task group to start with, and the test types this change implies |
| Tasks partially done | `/opsx:apply` | The exact next unchecked task number, and what the previous group actually produced |
| All tasks checked, gate not run | `./gradlew clean build check spotlessCheck` | That "written" ≠ "verified"; report the real output, never assert green |
| Gate green, not reviewed | Spawn `spec-conformance-reviewer` | The change name and that the reviewer checks the *code* honours the requirements, where `openspec validate` only checked artifact shape |
| Gate red | Spawn `debugger` | The failing task/test, the actual output, and that the fix stays inside the change's scope |
| Reviewed and passing | `openspec archive <change>` | Note `--skip-specs` is the fallback if archive objects to a zero-delta change |
| Archived, stage incomplete | `/opsx:propose` | The next backlog entry; confirm its stage dependencies are satisfied |
| Archived, stage complete | `bash scripts/fr-coverage.sh` | Read the output as advisory; then the first entry of the next stage |
| A spec gap or a genuinely new decision surfaced | *(decision — no command)* | Which frozen clause is wrong or silent, and that the remedy is a **new ADR**, never a spec edit |

## Delegate to sub-agents; keep the main session for decisions

The main session holds the plan, the decisions, and the conversation. **It should not hold file dumps, search output,
or test logs.** Anything that is "go read N files and come back with a conclusion" belongs in a sub-agent, whose
context is discarded when it returns.

| Work | Delegate to |
|---|---|
| Mapping what exists before proposing | `investigator` (read-only) or the `Explore` agent |
| Broad search across many files for one conclusion | `Explore` |
| Implementing an independent task group | `coder` |
| Writing the covering tests | `tester` |
| Pre-archive conformance gate | `spec-conformance-reviewer` |
| Diagnosing a red gate | `debugger` |
| Docs following a shipped change | `docs-writer` |

**Superpowers skills, when installed** (check the available-skills list; they are user-level, not repo-level, so never
assume they exist): `superpowers:subagent-driven-development` for a task list with independent items ·
`superpowers:dispatching-parallel-agents` for 2+ genuinely independent tasks · `superpowers:systematic-debugging`
before proposing any fix to a failure · `superpowers:test-driven-development` when implementing ·
`superpowers:verification-before-completion` before claiming anything passes. If they are absent, the repo agents
above cover the same ground — do not block on a missing plugin.

Two things **never** get delegated: the **decision** about what the next step is, and the **judgment call** on whether
generated artifacts satisfy R1–R5. Both need the conversation's context, and both are where this workflow fails when
it fails.

## Configuration inventory

`.claude/` is the **canonical** tree — rules and commands live there and nowhere else. `.agents/skills/` and `.codex/`
are mirrors for other harnesses; when a skill or agent changes, update `.claude/` first, then re-mirror.

| What | Canonical location | Mirrored to |
|---|---|---|
| Rules (always-on invariants) | `.claude/rules/` | — |
| Skills | `.claude/skills/` | `.agents/skills/`, `.codex/skills/` |
| Agents | `.claude/agents/` | `.codex/agents/` (`.toml`) |
| Slash commands | `.claude/commands/opsx/` | — |

**Rules** (`.claude/rules/`): `architecture-layering`, `java-coding-style`, `javafx-ui`, `gradle-build-and-quality`,
`error-envelope`, `llm-provider-integration`, `document-roundtrip`, `persistence-sqlite`, `threading-concurrency`,
`logging`, `testing`, `offline-and-privacy`, `theming-tokens`, `spec-authoring`.

**Skills**: `openspec-change-authoring`, `adr-authoring`, `javafx-ui-designer`, `llm-provider-integration`,
`document-pipeline`, `testing-javafx`, `create-mermaid-diagrams`, `project-navigator` — plus the vendored
`openspec-{propose,apply,archive,explore,update,sync-specs}` skills backing the `/opsx:*` commands.

**Agents**: `investigator` (read-only mapper), `coder` (implements), `tester` (covering tests),
`spec-conformance-reviewer` (read-only pre-archive gate — checks the *code* honours the requirements, where
`openspec validate` only checks the artifacts are well-formed), `docs-writer` (docs), `debugger` (diagnose failures).
The `architect` agent is retired — `/opsx:propose` writes the change artifacts and the `adr-authoring` skill covers
ADRs.

**Commands** (`.claude/commands/opsx/`): `propose`, `apply`, `archive`, `explore`, `update`, `sync`. The former
`plan-phase-stories-creation` and `plan-user-story-implementation` commands are retired.

## Build & test commands

```
./gradlew build            # compile + spotlessCheck + lint + test
./gradlew test             # unit + integration (excludes the UI/TestFX task in the fast gate)
./gradlew spotlessApply    # format (also run by the pre-commit hook)
./gradlew spotlessCheck    # format check (CI)
./gradlew check            # full quality gate: Spotless, Error Prone/NullAway, Checkstyle, SpotBugs, ArchUnit, tests
./gradlew liveLocal        # local-only provider tests vs a real Ollama / LM Studio; env-gated, never in CI
./gradlew :app:collectDist # stage app jar + runtime classpath for packaging
scripts/package-<os>       # jpackage CLI per OS (.app+.dmg / portable zip / tar.gz+.deb); no cross-compile
bash scripts/fr-coverage.sh # advisory: frozen FR-* ids no shipped requirement claims yet
```

None of the `./gradlew` tasks exist yet — they arrive with change 1, `bootstrap-gradle-and-quality-toolchain`.

## Stack (decided — see `docs/adr/`)

Java 25 (LTS) · JavaFX 25 (LTS) · Gradle (Kotlin DSL) + version catalog · Guice DI (constructor injection) · SQLite
(sqlite-jdbc + WAL) + Flyway + JDBI · JDOM2/dom4j + jsoup + `java.util.zip` (documents) · a CommonMark library
(Markdown) · `java.net.http` + Jackson (LLM HTTP/JSON) · Lingua + ICU4J (language detect / segmentation) · SLF4J +
Logback · Spotless (Palantir) + Error Prone/NullAway + Checkstyle + SpotBugs + ArchUnit · Lefthook · JUnit 5 + AssertJ +
Mockito + WireMock + TestFX/Monocle · jpackage (unsigned, per-OS). MIT license; permissive dependencies only.
