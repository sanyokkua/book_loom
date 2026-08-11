# AGENTS.md — BookLoom

Operating manual for building this project — binding for every agent (Claude Code, Codex, anything else).
`CLAUDE.md` imports this file verbatim and adds nothing of its own; never duplicate a rule there.

## Where this stands

BookLoom is a local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a
locally-run (Ollama/LM Studio) or OpenAI-compatible LLM, preserving structure/IDs/images/fonts through a
structure-and-text-preserving round trip, via a tiered draft → QA → judge → repair pipeline that runs
end-to-end with minimal human interaction.

**Stack.** Java 25 + JavaFX 26, Gradle (Kotlin DSL) + Guice DI, SQLite/JDBI/Flyway, JUnit5/TestFX — full
inventory in `gradle/libs.versions.toml`.

**State.** The Gradle/JPMS module skeleton, quality toolchain, Lefthook hooks and CI are built and wired
(`./gradlew`, all 9 `modules/*`, `lefthook.yml`, `.github/workflows/ci.yml` all exist and run). Stage A is
complete: the EPUB round-trip shipped and four changes are archived. `:api`, `:util`, `:document` (EPUB
only) and `:app` carry real code; `:llm`, `:pipeline`, `:persistence` are Guice-module stubs and `:ui` is an
app-shell placeholder. CI never actually *executes* until the app is feature-complete — the local gate is
authoritative until then (standing decision; a debt list of canaries proving the deferred gates is owed once,
at the end). Current unit of work: `docs/implementation_plan/CHANGE_BACKLOG.md` + `openspec/changes/`.

**The spec is the authority.** `docs/specification/` — frozen during implementation. Never invent behaviour;
a genuine gap is a new ADR (`docs/adr/`), never a spec edit.

**Humans start at `docs/DEVELOPMENT.md`.** Prerequisites, build, run, debug, IDE setup, test tiers, packaging and
troubleshooting live there; this file stays the agent operating manual.

## The loop

Delivery runs on OpenSpec (ADR-0016), one change at a time, in `docs/implementation_plan/CHANGE_BACKLOG.md`
order. No story files, no generated traceability — those are retired, never reintroduce them.

| Phase | Exit condition | Command |
|---|---|---|
| ORIENT | Backlog entry picked, prior work in the area read | Read `CHANGE_BACKLOG.md` |
| SPEC | `proposal.md` + `specs/*/spec.md` exist with EARS requirements | `/opsx:propose` |
| PLAN | `tasks.md` is an ordered, sentence-level checklist; artifacts validate | `openspec validate <change> --strict` |
| BUILD | Each task group implemented and individually verified | `/opsx:apply` |
| VERIFY | Gate green **and** `spec-conformance-reviewer` confirms code matches spec | `./gradlew clean build check spotlessCheck` |
| CLOSE | Archived, docs updated, task branches merged, next entry named | `openspec archive <change>` |

Use `/opsx:explore` when the shape of the change is genuinely unclear, `/opsx:update` to revise a change's
artifacts coherently (never hand-patch one in isolation). **Advance without asking** through SPEC→BUILD→VERIFY.
Stop only to: (1) resolve a spec that is ambiguous or silent — ask, with a recommended default; (2) report a
gate red twice for the same cause; (3) raise an architecturally-significant, costly-to-reverse decision (→ a
new ADR); (4) do anything irreversible or outside the repo — merge to `master`, tag, publish; (5) report new
information that contradicts the approved spec — never resolve that silently in code.

## Definition of Done

**Gate:** `./gradlew clean build check spotlessCheck` — ≈1–2 min locally; capture this run's wall-clock as the
baseline, don't assume a prior number still holds.

A unit of work is done when the gate is green **and** the implementation matches the spec. Both — tests
passing against the wrong behaviour is not done.

- **No "pre-existing failure" exemption.** A red mechanical check anywhere, even untouched code, is fixed
  before archiving — never carried forward or waved through (`06_DEFINITION_OF_DONE.md`).
- **`build-logic` needs its own clean.** Root `clean` does not reach it (included build); run
  `./gradlew :build-logic:clean` first when the point of the run is re-proving its canary tests — see
  *What will bite you*.
- **A hang is a result.** Materially longer than the baseline means hung — kill it and diagnose; never run two
  full gates concurrently.

Closing checklist — evidence, not assertion:

- [ ] Every requirement's scenario met, each named with the covering `// Covers: FR-*` test
- [ ] Every `tasks.md` checkbox checked
- [ ] Gate green, pasted tail as evidence — "it passed" is not a result
- [ ] Task branches merged and deleted
- [ ] Docs/ADRs updated for anything that changed the public surface
- [ ] Next step stated

## Git protocol

```
master                                     protected. Never developed on, never committed to.
  └── feature/<slug>                       parent branch, one per unit of work
        └── feature/<slug>--<task>         task branch, one per plan task
```

1. On `master`, create `feature/<slug>`. Already on a suitable feature branch? Use it — do not stack another.
2. Before touching a file, branch a task sub-branch off the parent.
3. **The separator is `--`, not `/`.** Git stores a branch as a file at `.git/refs/heads/<name>`, so
   `feature/x` cannot be both a file and a directory: `feature/x/y` fails with `cannot lock ref
   'refs/heads/feature/x/y': 'refs/heads/feature/x' exists`. Not avoidable by naming — every task sub-branch
   hits this, because step 1 always creates the parent as a branch first.
4. Commit only on task branches, one coherent task per branch.
5. Task done: squash-merge into the parent as **one** consolidated commit, delete the task branch.
6. **Never merge the parent into `master`.** Final review and merge belong to the user.
7. Never `--no-verify`, never force-push. If a hook is wrong, fix the hook and say you did.

## Delegation

The main session holds the plan, the decisions, the conversation — not file dumps, search output, or test
logs. Anything shaped like "read N files, come back with a conclusion" goes to a sub-agent whose context is
discarded on return.

| Work | Delegate to |
|---|---|
| Map what exists before proposing | `investigator` (read-only) or `Explore` |
| Broad search across many files for one conclusion | `Explore` |
| Implement one independent task group | `coder` |
| Write the covering tests | `tester` |
| Pre-archive conformance gate | `spec-conformance-reviewer` |
| Diagnose a red gate | `debugger` |
| Docs following a shipped change | `docs-writer` |

Superpowers skills (`subagent-driven-development`, `dispatching-parallel-agents`, `systematic-debugging`,
`test-driven-development`, `verification-before-completion`), when installed, cover the same ground — do not
block on a missing plugin if they're absent.

**Never delegated:** the decision about what the next step is, and the judgment call on whether generated
artifacts satisfy the spec-authoring standard (R1–R6, `.claude/rules/spec-authoring.md`).

## Non-negotiables

| Rule | Enforced by |
|---|---|
| FX-free core — only `:ui`/`:app` see JavaFX | ArchUnit `fx-free-core` |
| Skeleton never regenerated; only text nodes change | per-format golden round-trip test |
| Records for data, Lombok only on services | ArchUnit `records-first` |
| Offline — only user-triggered provider calls leave the machine | ArchUnit `no-http-in-core-except-llm` |
| Uniform error envelope: `Result<T>` + typed `AppError` at every boundary | — (advisory) |
| Credentials are a reference (env-var/keychain), never a persisted secret | — (advisory) |
| Single-flight inference through one `InferenceGate` | — (advisory) |
| Scene graph touched only on the FX Application Thread | — (advisory) |
| Persistence commits are atomic per chunk (crash-safe resume) | — (advisory) |
| Green gate, no exceptions | pre-push `quality-gate.sh` + CI `quality` job |

## End every turn with Next step

**Every turn that advances the work ends with this block.** Not after a research answer, not after a partial
run, not after a failure. No exceptions.

```markdown
## Next step

**State:** <where the work actually is, one line, citing evidence>
**Command:** `<exact command>` — or "none — decision needed from you"
**Prompt:**
> <complete, copy-pasteable: names the change, the artifacts to read first, the constraint most likely to be
> violated at this step>
```

- **Be honest.** "All tasks written" is not "all tasks verified". If the gate hasn't run, the state is
  *unverified*, whatever the checkboxes say.
- **Self-contained.** `"continue"` is not a prompt — it must work in a fresh session.
- **A decision is a valid next step.** State the options, recommend one. Never invent a command to look
  productive.
- **One step, not a plan.** The state → command → prompt mapping for every stage of the loop is
  `docs/implementation_plan/08_NEXT_STEP_STATES.md`.

## What will bite you

- **`build-logic` canary tests silently skip re-running.** Root `clean` does not reach the `modules/build-logic`
  included build, so even though `check` depends on `:build-logic:test`, a repeat gate run can report it
  `UP-TO-DATE` instead of re-executing — the run still prints `BUILD SUCCESSFUL` and looks like proof when it
  isn't. Tell: compare the task count (`91 actionable tasks: 91 executed` vs `79 executed, 12 up-to-date`). Run
  `./gradlew :build-logic:clean` first whenever the point of the run is re-proving those canaries.
- **A version-catalog pin that nothing references does nothing.** `gradle/libs.versions.toml` entries only
  influence resolution through a matching `constraints {}` block in
  `bookloom.java-conventions.gradle.kts` — a pin with no constraint looks locked but isn't (this is exactly
  how Guava's CVE-2023-2976 shipped transitively once). Sanity check: grep for the `libs.` accessor; no hit
  means decoration.
- **`:app` runtime deps produce no lockfile diff — that's correct, not broken.** The JavaFX classifier
  carve-out (`deactivateDependencyLocking()` in `bookloom.javafx-conventions` and `modules/app/build.gradle.kts`)
  disables locking on `:app`'s compile/runtime classpaths, because a lock entry can't record *which* per-OS
  JavaFX artifact resolved. Verify a new `:app` dependency's version in the catalog, not the lockfile — the six
  FX-free modules still lock normally.

## Where things live

- Spec (authority, frozen): `docs/specification/`. Decisions: `docs/adr/`.
- Work tracking: `docs/implementation_plan/CHANGE_BACKLOG.md`, `openspec/changes/`, module map in
  `docs/implementation_plan/01_MODULE_INVENTORY.md`.
- Rules are `paths:`-scoped in `.claude/rules/`; skills and subagents load themselves — not listed here.
- `.agents/skills/` is canonical; `.claude/skills/` and `.codex/skills/` are generated symlinks — never
  hand-edit them, run `python3 scripts/sync-agent-files.py --apply`.
- The `modules/` directory layout and the Gradle project names (`:api` … `:app`) are deliberately decoupled
  (ADR-0021) — `settings.gradle.kts` repoints each `projectDir` explicitly, so `:app:run` etc. are unaffected.
