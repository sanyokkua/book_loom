# ADR-0021 — Relocate the nine code directories under a single `modules/` parent

**Status:** accepted **Date:** 2026-08-03
**Deciders:** project owner
**Supersedes:** the directory tree in `04_Build_and_Release/01_BUILD_AND_TOOLING.md#project-layout`, and the literal
`app/build/dist/libs/` staging path at all four of its occurrences — `01_BUILD_AND_TOOLING.md#javafx-plugin` and
`#task-surface`, and `03_PACKAGING_JPACKAGE.md#approach` (twice). Everything else those documents fix — the build
tool, the wrapper, the toolchain, the version catalog, the convention-plugin set, the set of tasks the task surface
enumerates, and the Gradle project names — stands unchanged.

## Context and problem statement

Change `bootstrap-gradle-and-quality-toolchain` created the build by placing all nine code directories — the eight
Gradle subprojects `api util document llm pipeline persistence ui app` plus the `build-logic` included build — as
direct children of the repository root, exactly as `01_BUILD_AND_TOOLING.md#project-layout` draws them.

That was faithful to the spec, and it left the repository root holding fourteen directories and eleven files. Nine of
those directories are code. The rest — `docs/` (about forty specification files, twenty-one ADRs, the implementation
plan), `openspec/` (the delivery ledger), `config/`, `tooling/`, `scripts/`, `gradle/` — plus `AGENTS.md`, `README.md`,
`LICENSE`, and the build scripts, are not. BookLoom is an unusually documentation-heavy project: the written corpus is
the larger and more frequently read half of the repository, and today it is interleaved with the code at the same
level, sorted alphabetically among it.

This matters more here than in a typical repository because of how the project is built. Every unit of work begins by
reading `AGENTS.md`, the frozen specification, and the change backlog; agents and humans alike land at the root and
have to work out which of fourteen sibling directories are prose and which are Java. Nothing about the current layout
answers that at a glance.

The spec fixes the flat layout as a picture, not as a requirement — `#project-layout` is an ASCII tree with no
accompanying obligation, and no FR/NFR/DD id attaches to it. But `docs/specification/**` is frozen and read-only, so
even a layout the spec merely *depicts* cannot be diverged from silently. Per ADR-0016 the remedy is this ADR.

Three further passages hard-code a path that the move invalidates. `01_BUILD_AND_TOOLING.md#javafx-plugin` and
`#task-surface` and `03_PACKAGING_JPACKAGE.md#approach` all state that `:app:collectDist` stages the application jar
and its runtime classpath into `app/build/dist/libs/`, and `#approach` additionally specifies that the packaging
scripts auto-discover the main jar there. `#javafx-plugin` is the easiest of the three to miss, because the path sits
mid-sentence in a section that is otherwise about the JavaFX Gradle plugin rather than about packaging. None is
implemented yet — `collectDist` does not exist in any build script today — so this ADR settles the path before the
packaging work is written against it, rather than after.

## Decision drivers

- **The root should answer "where is the code?" in one glance.** A reader arriving at this repository is far more
  often looking for prose than for Java, and the two should not be shuffled together alphabetically.
- **Gradle project names must not change.** `:api`, `:app`, and the rest are the join key for the entire corpus: the
  `:module/package` citation convention in `01_MODULE_INVENTORY.md#how-to-cite-a-module`, every ArchUnit rule, every
  `module-info.java`, every task citation in the archived change, and every rule file under `.claude/rules/`. A move
  that renamed projects would invalidate all of it; a move that only relocates directories invalidates none of it.
- **The cost is paid once, now, and rises steeply later.** The tree today is eight placeholder types, four canary
  tests, and the ArchUnit fixture set. Every later change adds files, and `git mv` on a large tree obscures real
  history in a way it does not on an empty one.
- **The layout is depicted, not required.** No FR, NFR, or DD constrains directory placement, and
  `02_MODULES_AND_LAYERING.md` — the document that actually governs the module graph — never mentions physical
  directories at all. The deviation is narrow.

## Considered options

- **A — Keep the flat root layout.**
- **B — `modules/` as the parent directory.**
- **C — `src/` as the parent directory.**
- **D — `code/` or `codebase/` as the parent directory.**

## Decision outcome

Chosen: **B — relocate all nine directories under `modules/`.**

`modules/` is the only candidate that introduces no new vocabulary. The project already calls these things modules
everywhere it describes them: `AGENTS.md`'s "Modules (Gradle subprojects = JPMS modules)" section, the file
`01_MODULE_INVENTORY.md`, the rule file `architecture-layering.md`, and the JPMS module names `ua.bookloom.<module>`
themselves. A reader who understands the project already knows what `modules/` contains before opening it.

`build-logic` moves with them. It is code — Kotlin convention plugins with their own functional-test suite — and
leaving it at the root to avoid three path repairs would defeat the purpose of the move for the sake of the two
cheapest fixes in it.

The root keeps everything that is not code, plus the files that tooling requires to be there: `gradlew` and
`gradle/wrapper/` (the wrapper resolves relative to the invocation root), `settings.gradle.kts` and
`build.gradle.kts` (Gradle requires them at the build root), `lombok.config`, `lefthook.yml`, `.editorconfig`,
`.gitattributes`, and the root lockfile. `config/`, `tooling/`, and `scripts/` also stay: they hold quality
configuration, git-hook shell scripts, and packaging drivers rather than compiled sources, and every reference to
them resolves from the root project directory, which does not move.

All four staging-path occurrences are superseded to read `modules/app/build/dist/libs/`. Nothing else about packaging
changes — the `Sync` task, the per-OS script set, and the jpackage flags are all unaffected by where the module
directory sits.

### Consequences

- Positive: the repository root separates cleanly into prose (`docs/`, `openspec/`, `README.md`, `AGENTS.md`) and
  code (`modules/`), with build and tooling configuration as the small remainder.
- Positive: no Gradle project name, JPMS module name, package name, or `:module/package` citation anywhere in the
  corpus changes. The archived change's task citations, which target "repo root" and `:module`-relative paths, all
  remain accurate.
- Positive: no application source file changes, so the move cannot alter behaviour. The green gate proves it.
- Negative: `01_BUILD_AND_TOOLING.md#project-layout` and all four `app/build/dist/libs/` occurrences must now be read
  through this ADR. The spec files stay unedited, as they must.
- Negative: `build-logic` gains one level of distance from the root, which breaks two relative paths inside it
  (`../gradle/libs.versions.toml` and a `rootDir.parentFile` derivation) and the three functional tests that consume
  the latter. All are repaired within the change; the `rootDir.parentFile` one is the dangerous case because it fails
  silently at configuration time and only surfaces inside the tests.
- Negative: `-p build-logic` on the command line becomes `-p modules/build-logic`, in CI, in
  `01_MODULE_INVENTORY.md`'s documented lock commands, and in local muscle memory.
- Neutral: contributors with the project open in an IDE re-import once. No tracked `.idea/` file references module
  paths, so nothing in version control needs updating for it.

## Pros and cons of the options

### Option A — keep the flat root layout

- Good: costs nothing, deviates from nothing, and needs no ADR.
- Good: matches the frozen spec's picture exactly.
- Bad: leaves fourteen root directories with no visual separation between the documentation corpus and the code, in
  a project where the documentation is read first and read most.
- Bad: the cost of moving only ever rises. Deferring is choosing the flat layout permanently.

### Option B — `modules/` (chosen)

- Good: reuses the word the project already uses for exactly these nine things — no new concept to learn or document.
- Good: unambiguous inside a Gradle build, where "module" is already the everyday name for a subproject.
- Bad: one level deeper than `src/` would be, and slightly longer to type in a `-p` flag.

### Option C — `src/`

- Good: short, and a widely recognised convention.
- Bad: every module already owns a `src/`, producing `src/api/src/main/java/...`. The doubled segment reads badly,
  is easy to mistype, and makes path globs harder to reason about.
- Bad: `src/` conventionally holds source *files*, not a set of independent builds — `build-logic` in particular is
  not "source" in that sense.

### Option D — `code/` or `codebase/`

- Good: unambiguous, collides with nothing, and needs no explanation.
- Bad: introduces a word the project's documentation never uses, so it names the same concept twice — the docs say
  "modules", the filesystem says "code".

## Links

- Spec clauses superseded — four occurrences of the staging path, plus the tree:
  `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#project-layout` (the directory tree),
  `#javafx-plugin` (the `collectDist` staging path, stated mid-sentence in a section otherwise about the JavaFX Gradle
  plugin), and `#task-surface` (the same staging path, in the `:app:collectDist` row);
  `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach` (the same staging path, and the main-jar
  discovery location). Verified exhaustive by `grep -rn 'build/dist' docs/specification/`, which returns exactly these
  four lines and no other; no clause outside them is affected.
- Spec clauses explicitly **not** affected: `02_Architecture/02_MODULES_AND_LAYERING.md` (the module graph, which
  never mentions physical directories), `#build-system`, `#version-catalog`, and `#convention-plugins`.
- Change: `restructure-module-layout` — an **unnumbered** Stage A entry running between change 1 and change 2
  (`docs/implementation_plan/CHANGE_BACKLOG.md#stage-a`). It takes no number deliberately: change numbers are cited
  across `07_ROADMAP.md`, three accepted ADRs, and an archived change, and renumbering would require editing records
  that must not be edited.
- Related: `docs/adr/ADR-0016-openspec-delivery-tracking.md` (a spec gap is remedied by an ADR, never a spec edit);
  `docs/adr/ADR-0002-build-tool-gradle.md` (the build tool and its layout conventions);
  `docs/adr/ADR-0017-infrastructure-first-delivery-order.md` (why this lands in Stage A, before any feature work)
