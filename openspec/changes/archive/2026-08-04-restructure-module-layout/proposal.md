# restructure-module-layout

## Why

Change `bootstrap-gradle-and-quality-toolchain` created the build with all nine code directories — the eight Gradle
subprojects `api util document llm pipeline persistence ui app` plus the `build-logic` included build — as direct
children of the repository root, exactly as `04_Build_and_Release/01_BUILD_AND_TOOLING.md#project-layout` draws them.
The root now holds fourteen directories and eleven files, with code and prose interleaved alphabetically and nothing
distinguishing them.

BookLoom is unusually documentation-heavy: roughly forty frozen specification files, twenty-one ADRs, the
implementation plan, and the OpenSpec delivery ledger. That corpus is read first and read most — every unit of work
begins in `AGENTS.md` and the backlog — yet a reader landing at the root cannot tell at a glance which siblings are
prose and which are Java.

Doing it now rather than later is the whole point of the timing. The tree today is eight placeholder types, four
canary tests, and the ArchUnit fixture set; there is almost nothing to move. Every later change adds files, and a
`git mv` across a populated tree obscures real history in a way it cannot on an empty one. This is also the last
moment before the packaging path `app/build/dist/libs/` gets written into a build script — settling it now means
change 2 (`bootstrap-app-launch-and-empty-window`) writes the correct path once, instead of writing it and then
changing it.

## What Changes

- **Nine directories move under a new `modules/` parent** — `api`, `util`, `document`, `llm`, `pipeline`,
  `persistence`, `ui`, `app`, and `build-logic` — via `git mv`, so history follows each file.
- **`settings.gradle.kts` gains an explicit `projectDir` per subproject.** Gradle derives a subproject's directory
  from its name by default (`include(":api")` → `rootDir/api`), and that default is now wrong for all eight. The
  include list and `includeBuild("build-logic")` are repointed at `modules/`.
- **Three path derivations inside `build-logic` are repaired.** Its `settings.gradle.kts` reaches the version catalog
  with `../gradle/libs.versions.toml`, which is now one level short; its `build.gradle.kts` derives the repository
  root as `rootDir.parentFile`, which now yields `modules/` instead — a silent mis-resolution that surfaces only
  inside the functional tests that consume it.
- **The `build-logic` functional tests that hard-code module paths are updated** — `DependencyLockingFunctionalTest`
  resolves `<repoRoot>/<module>/gradle.lockfile` for all nine modules in three separate places.
- **CI and the documented lock commands learn the new `build-logic` path** — `-p build-logic` becomes
  `-p modules/build-logic` in `.github/workflows/ci.yml` and in `01_MODULE_INVENTORY.md`'s worked examples.
- **Two hand-run hook checks learn the new fixture path** — `tooling/hooks-test/pre-commit-formats-staged-file.sh`
  and `pre-push-blocks-checkstyle-violation.sh` each write a throwaway Java fixture to a hard-coded
  `util/src/main/java/...` path. They are run by hand, never by Gradle, so the green gate cannot catch them.
- **A dead `.gitignore` pattern is pruned** — `*/build/` matches exactly one path segment and stops matching after
  the move; it is already redundant with the unanchored `build/` on the line above.
- **ADR-0021 records the decision** and supersedes the frozen-spec clauses the move invalidates — the directory tree,
  and all four occurrences of the `app/build/dist/libs/` staging path.
- **The backlog gains one unnumbered Stage A entry**, placed between change 1 and change 2. Changes 1-28 keep their
  numbers and every prose cross-reference is left untouched: the numbers are cited in `07_ROADMAP.md`, in three
  accepted ADRs, and in an archived change, and renumbering would mean editing records that must not be edited.

**BREAKING:** for contributors, not for the product. `-p build-logic` no longer resolves, and an IDE with the project
open needs one re-import. No Gradle project name, JPMS module name, package name, or public type changes, so nothing
that any other change or document cites by name is affected.

## Capabilities

**New Capabilities:** none.
**Modified Capabilities:** none.

This change sets `skip_specs: true` in `.openspec.yaml`. It relocates directories and repairs the build configuration
that pointed at their old locations. Not one line of application code changes — the verification that the move is
correct is precisely that nothing *but* paths changed. Nothing a reader of the book-translation product can do,
observe, or depend on is added, removed, or altered; a directory layout is *how the system is built*, never *what the
system does*. Writing a requirement such as "the system SHALL organise its modules under `modules/`" to satisfy
validation would put developer mechanics into a ledger whose job is to answer "what does BookLoom do for its user"
(ADR-0016), and `spec-authoring.md` names that invention as a defect outright. The verification that would otherwise
live in a spec lives in `tasks.md`, where each task states its own check.

## Impact

- **Modules:** all eight (`:api :util :document :llm :pipeline :persistence :ui :app`) plus the `build-logic`
  included build change location. None changes name, contents, dependency edges, or `module-info.java`. The
  `arch-test` source set stays where it is relative to its owner — `:app/src/archTest/java` — because that citation
  is module-relative and survives the move untouched.
- **Files moved:** the nine directories in full, including eleven `gradle.lockfile` / `settings-gradle.lockfile`
  files, which move with their owners and need no content edit — Gradle re-derives the lock path from the corrected
  `projectDir`.
- **Files edited:** `settings.gradle.kts`, `modules/build-logic/settings.gradle.kts`,
  `modules/build-logic/build.gradle.kts`,
  `modules/build-logic/src/test/java/ua/bookloom/buildlogic/BuildFixture.java`,
  `modules/build-logic/src/test/java/ua/bookloom/buildlogic/DependencyLockingFunctionalTest.java`,
  `tooling/hooks-test/pre-commit-formats-staged-file.sh`,
  `tooling/hooks-test/pre-push-blocks-checkstyle-violation.sh`,
  `.github/workflows/ci.yml`, `.gitignore`, `docs/implementation_plan/01_MODULE_INVENTORY.md`,
  `docs/implementation_plan/CHANGE_BACKLOG.md`, `AGENTS.md`, `README.md`.
- **Files added:** `docs/adr/ADR-0021-modules-under-a-single-parent-directory.md`.
- **Verified unaffected, deliberately untouched:** root `build.gradle.kts` (it references subprojects through the
  Gradle object graph and `config/` through the root project directory, neither of which moves); `config/**`
  (`config/checkstyle/suppressions.xml` matches source sets with a depth-agnostic regex, and nothing under `config/`
  names a module directory); the rest of `tooling/**` beyond the two hook-check fixture paths listed above —
  `git grep -nE 'ua/bookloom|src/main/java|src/test/java|/gradle\.lockfile' -- tooling config scripts .lefthook
  lefthook.yml` returns exactly those two lines and nothing else; `lefthook.yml` and
  `.lefthook/pre-push/quality-gate.sh`; `.gitattributes` and
  `.editorconfig` (bare globs, no directory anchor); `scripts/fr-coverage.sh` (touches only `docs/` and `openspec/`);
  and every `.java`, `module-info.java`, and per-module `build.gradle.kts`.
- **Dependencies introduced:** none. No coordinate is added, removed, or re-versioned, so no lockfile content changes
  and the license and SCA gates see an identical graph.
- **Runtime behaviour:** none — there is none yet. `./gradlew :app:run` still opens no window; that is change 2.
- **Downstream:** every subsequent change writes files under `modules/` instead of the root. Change 2
  (`bootstrap-app-launch-and-empty-window`) is the first to feel it: `:app:collectDist` must stage into
  `modules/app/build/dist/libs/`, and the `scripts/package-<os>` drivers must discover the main jar there.
- **Frozen spec:** unedited, as required. Five passages are superseded by ADR-0021 rather than changed —
  `01_BUILD_AND_TOOLING.md#project-layout` (the directory tree), and the `app/build/dist/libs/` staging path at all
  four of its occurrences: `01_BUILD_AND_TOOLING.md#javafx-plugin` and `#task-surface`, and
  `03_PACKAGING_JPACKAGE.md#approach` (twice). `grep -rn 'build/dist' docs/specification/` returns exactly those four
  staging-path lines, so the set is exhaustive rather than assumed.
- **ADRs:** ADR-0021 (new, this decision), ADR-0002 (Gradle Kotlin DSL — unchanged), ADR-0016 (a spec gap is remedied
  by an ADR, never a spec edit), ADR-0017 (this change is Stage A, before any feature work).
