# Tasks — restructure-module-layout

Relocates the nine code directories under `modules/` and repairs everything that pointed at their old locations. The
decision is **ADR-0021**; the approach and the one dangerous edit are `design.md` (D1-D6).

**The whole change is one commit.** Task groups 1-6 leave the repository unbuildable in between — `settings.gradle.kts`
points at directories that no longer exist the moment group 1 lands — so nothing is committed until group 7 is green
(design.md D1). Work through the groups in order; the ordering is a dependency chain, not a preference.

**Scope discipline:** the only files that may change are build configuration, documentation, and file *locations*. A
diff line inside any `.java`, `module-info.java`, or per-module `build.gradle.kts` is a defect in this change — the
proof that the move is safe is precisely that nothing else moved with it.

## 1. Move the nine directories

- [x] 1.1 Create `modules/` at the repository root and `git mv` all eight subproject directories into it — `api`,
      `util`, `document`, `llm`, `pipeline`, `persistence`, `ui`, `app`. Use `git mv` rather than a shell `mv` plus
      re-add: it stages each move as a rename outright instead of leaving git to infer one, so `git log --follow` on
      any file crosses the move and the diff reads as nine renames rather than as a delete-and-create of the entire
      tree. → repo root · ADR-0021 (Decision outcome), design.md D1
- [x] 1.2 `git mv build-logic modules/build-logic`, so the included build sits beside the subprojects it configures.
      It is code — Kotlin convention plugins with their own functional-test suite — and leaving it at the root to dodge
      the three path repairs in group 3 would defeat the point of the move for the sake of its two cheapest fixes.
      → repo root · ADR-0021 (Decision outcome, "`build-logic` moves with them")
- [x] 1.3 Confirm nothing was left behind and nothing else moved: `git status` shows exactly nine renamed directories
      and no untracked leftovers, and `git diff --cached --stat -- '*.java'` is empty. The change's entire safety
      argument rests on no source file having been touched, so prove it here rather than inferring it from a green
      gate later. → repo root · design.md "Goals / Non-Goals"

## 2. Repoint the root build at the new locations

- [x] 2.1 In `settings.gradle.kts`, change `includeBuild("build-logic")` to `includeBuild("modules/build-logic")`
      inside the `pluginManagement` block. Until this is right the main build cannot resolve the `bookloom.*`
      convention plugins at all, so this is the first thing that must be true.
      → repo root · `settings.gradle.kts:10`, ADR-0021
- [x] 2.2 Add an explicit `project(":<name>").projectDir = file("modules/<name>")` for each of the eight subprojects,
      keeping the existing `include(...)` list exactly as it is. Gradle derives a subproject's directory from its name
      by default (`include(":api")` → `rootDir/api`), and that default is now wrong for all eight — without these
      lines the build fails at configuration time. Do **not** switch to `include(":modules:api")`: that form renames
      every project path, invalidating every `project(":api")` dependency declaration, every `./gradlew :app:run`
      invocation, and the `:module/package` citation convention the whole corpus uses.
      → repo root · `settings.gradle.kts:30-39`, design.md D2, `01_MODULE_INVENTORY.md#how-to-cite-a-module`
- [x] 2.3 Verify the graph resolves before going further: `./gradlew projects` lists all eight subprojects with
      their paths still `:api` … `:app` (not `:modules:api`), and `./gradlew help` completes. Catching a settings
      mistake here costs seconds; catching it after group 3 means two candidate causes instead of one.
      → repo root · design.md D2

## 3. Repair `build-logic`'s internal path derivations

- [x] 3.1 In `modules/build-logic/settings.gradle.kts`, change the version-catalog reference from
      `from(files("../gradle/libs.versions.toml"))` to `from(files("../../gradle/libs.versions.toml"))`. `gradle/`
      stays at the repository root while `build-logic` is now one level deeper, so the single `..` no longer reaches
      it and the included build cannot resolve its own catalog — every `libs.*` accessor in the convention plugins
      fails. → `modules/build-logic` · `modules/build-logic/settings.gradle.kts:12`, `01_BUILD_AND_TOOLING.md#version-catalog`
- [x] 3.2 In `modules/build-logic/build.gradle.kts`, change `systemProperty("bookloom.repoRoot",
      rootDir.parentFile.absolutePath)` to `rootDir.parentFile.parentFile.absolutePath`, and correct the comment
      above it — which currently states "`rootDir` here is `build-logic/`, so its parent is the repository root" — to
      say why there are now two hops. **This is the one silent failure in the change:** post-move `rootDir.parentFile`
      is `modules/`, a directory that genuinely exists, so nothing throws; `BuildFixture` normalizes a valid path to
      the wrong place and the damage surfaces far away as "cannot find `checkstyle.xml`" and "cannot find
      `allowed-licenses.json`" inside unrelated tests.
      → `modules/build-logic` · `modules/build-logic/build.gradle.kts:101`, design.md D3
- [x] 3.3 Add a `moduleDir(String module)` accessor to `BuildFixture` beside the existing `repoRoot()`, returning
      `REPO_ROOT.resolve("modules").resolve(module)`, with a Javadoc line citing ADR-0021. Naming the layout once
      means the next layout change edits one line instead of hunting call sites — and it lets the lockfile tests
      assert about *a module* rather than about *a path*.
      → `modules/build-logic` · `modules/build-logic/src/test/java/ua/bookloom/buildlogic/BuildFixture.java:22-30`,
      design.md D3
- [x] 3.4 Repoint all three module-path call sites in `DependencyLockingFunctionalTest` at `moduleDir(...)`:
      `committedLockState_coversEveryModuleAndBuildLogic` (the nine-module loop), the `List.of("ui", "app")` loop in
      `committedLockState_excludesJavaFxClasspathsButKeepsTheQualityPathLocked`, and the literal
      `"document/gradle.lockfile"` read in the same test. These assertions are what actually prove the move preserved
      every lockfile, so they must resolve correctly or the change's main safety net silently stops catching anything.
      → `modules/build-logic` ·
      `modules/build-logic/src/test/java/ua/bookloom/buildlogic/DependencyLockingFunctionalTest.java:119-123,138-139,155`,
      design.md D3
- [x] 3.5 Confirm no other `build-logic` source names a module directory: `git grep -n '"\(api\|util\|document\|llm\|pipeline\|persistence\|ui\|app\|build-logic\)/' -- modules/build-logic`
      returns only the sites fixed above. `BuildFixture.qualityConfig()` and `LicenseGateFunctionalTest` reach
      `config/` and `lombok.config` through `repoRoot()`, which 3.2 makes correct again — they need no edit of their
      own, and confirming that is what makes 3.2 provably sufficient.
      → `modules/build-logic` · design.md "Context" (the breakage table)

## 4. Repair the periphery

- [x] 4.1 In `.github/workflows/ci.yml`, change the lock-verification step's `-p build-logic` to
      `-p modules/build-logic`, and update the `util/gradle.lockfile` path in the explanatory comment eleven lines
      above it to `modules/util/gradle.lockfile`. `-p` is a Gradle project-directory flag resolved relative to the
      working directory, so it is one of the few CI paths the move actually breaks — the artifact globs and cache
      paths all use `**` and survive untouched; the comment is prose, but a stale path in the one note explaining
      *why* the step exists misleads whoever next has to reason about it. Note in the change that CI is not exercised
      until the project is feature-complete, so this edit ships as documented debt rather than as a verified claim.
      → repo root · `.github/workflows/ci.yml:143,152`, design.md "Risks"
- [x] 4.2 Update the three `-p build-logic` occurrences in `modules/build-logic/build.gradle.kts`'s comments (the
      lock-rationale note, the `resolveAndLockAll --write-locks` recipe, and the CI-command note) to
      `-p modules/build-logic`. They are comments, not code, but they are the copy-paste source for anyone
      regenerating lock state — a stale recipe here produces a confusing failure at exactly the moment someone is
      already debugging dependencies.
      → `modules/build-logic` · `modules/build-logic/build.gradle.kts:44,53,69`
- [x] 4.3 Repoint the hard-coded fixture path in both hand-run hook checks:
      `tooling/hooks-test/pre-commit-formats-staged-file.sh` and
      `tooling/hooks-test/pre-push-blocks-checkstyle-violation.sh` each set
      `HOOKS_TEST_FIXTURE='util/src/main/java/ua/bookloom/util/paths/Hook*Probe.java'`, which must become
      `modules/util/src/main/java/...`. Each script writes its fixture with `printf … > "$HOOKS_TEST_FIXTURE"`, and a
      shell redirect does not create parent directories, so post-move both fail outright at the first write. **These
      are the only two breakages in this change that the green gate cannot detect** — the hook checks are run by hand,
      never by Gradle (`tooling/hooks-test/README.md`), so task 7.2 passing says nothing about them.
      → repo root · `tooling/hooks-test/pre-commit-formats-staged-file.sh:16`,
      `tooling/hooks-test/pre-push-blocks-checkstyle-violation.sh:22`, design.md "Context" (the breakage table)
- [x] 4.4 Delete the `*/build/` line from `.gitignore`, keeping the unanchored `build/` above it. `*/build/` matches
      exactly one path segment before `build/` and therefore stops matching `modules/api/build/` entirely; the
      unanchored `build/` on the previous line already ignores a `build` directory at any depth, so nothing becomes
      tracked. Leaving a pattern that matches nothing is a trap for the next reader, who will assume it is
      load-bearing. → repo root · `.gitignore:2-3`, design.md D6

## 5. Update the documentation that describes the layout

- [x] 5.1 Confirm `docs/adr/ADR-0021-modules-under-a-single-parent-directory.md` is present and accurate against what
      groups 1-4 actually did — in particular that its "Supersedes" header and Links section name all five superseded
      passages: `01_BUILD_AND_TOOLING.md#project-layout` (the directory tree) plus the `app/build/dist/libs/` staging
      path at each of its four occurrences — `01_BUILD_AND_TOOLING.md#javafx-plugin` and `#task-surface`, and
      `03_PACKAGING_JPACKAGE.md#approach` twice. Re-run `grep -rn 'build/dist' docs/specification/` and check it
      returns exactly those four staging-path lines: an earlier draft of the ADR named only two of them while
      asserting "no other clause in either file is affected", and `#javafx-plugin` is the easy one to miss because the
      path sits mid-sentence in a section otherwise about the JavaFX Gradle plugin. The ADR is the only sanctioned
      record of the divergence from the frozen spec; a clause it fails to name is a divergence nobody recorded.
      → `docs/adr/` · ADR-0016 (a spec gap is remedied by an ADR, never a spec edit)
- [x] 5.2 Update the "Modules (Gradle subprojects = JPMS modules …)" section of `AGENTS.md` to state that the nine
      code directories live under `modules/` while the Gradle project names stay `:api` … `:app`, and cite ADR-0021.
      `AGENTS.md` is the first file every agent and contributor reads; if it does not say where the code is, the move
      has made the repository harder to navigate rather than easier.
      → repo root · `AGENTS.md` ("Modules" section), ADR-0021
- [x] 5.3 Add a `modules/` block to the "Repository layout" tree in `README.md`, listing the nine directories and
      noting that project names are unchanged. The tree currently omits the code directories entirely — it documents
      `docs/`, `openspec/`, `scripts/`, and `tooling/` but not the eight subprojects — so this both records the move
      and closes a gap that predates it. → repo root · `README.md:20-47`, ADR-0021
- [x] 5.4 Update the `-p build-logic` reference in `01_MODULE_INVENTORY.md`'s dependency-lock-gate paragraph to
      `-p modules/build-logic`, and add one sentence recording that module directories now live under `modules/`
      while the `:module/package` citation convention is unaffected. That convention is the join key the whole corpus
      uses; readers need it stated explicitly that it did *not* change, or they will assume it did.
      → `docs/implementation_plan/` · `01_MODULE_INVENTORY.md:96-101`, `#how-to-cite-a-module`, ADR-0021

## 6. Add the backlog entry, changing no existing number

- [x] 6.1 In `docs/implementation_plan/CHANGE_BACKLOG.md`, add this change to the Stage A table as an **unnumbered
      row** (number column `—`) placed between change 1 and change 2, with the exit gate "`./gradlew clean build check
      spotlessCheck` green from the new layout" and a one-line note that it runs after change 1 and before change 2.
      Change **1-28 keep their numbers and every prose cross-reference is left exactly as it is** — including the
      "Change 2 — covers" paragraph, the Stage B/C/D seam sentences, and the `Introduced by` column of
      `#capability-map`. Renumbering was rejected because change numbers are cited in `07_ROADMAP.md`'s F1-F9 seam
      table, in ADR-0017/0018/0019, and in the archived change's `design.md`: accepted ADRs are immutable records and
      an archived change is never reopened, so a renumber could only either violate those rules or leave the corpus
      permanently self-contradicting. Add one sentence recording honestly that there are now **29 changes in 28
      numbered slots**, because ADR-0017's "28 changes" counts the planned delivery sequence that this interstitial
      infrastructure change does not join — so a reader who counts rows and gets 29 is not left guessing. Update
      nothing in `07_ROADMAP.md`, `AGENTS.md`, `lefthook.yml`, or any ADR: leaving all of them untouched is the point.
      → `docs/implementation_plan/` · `CHANGE_BACKLOG.md#stage-a`, `#how-to-use-this`, design.md D5

## 7. Green gate

- [x] 7.1 Run `./gradlew :build-logic:clean` **before** the gate. `build-logic` is an included build, so the root
      `clean` does not reach into it and its functional tests can report `UP-TO-DATE` from a cache populated before
      the move — a false green that would hide exactly the mis-resolution task 3.2 exists to prevent.
      → repo root · design.md D4
- [x] 7.2 Run `./gradlew clean build check spotlessCheck` and confirm it is green across the whole project — build,
      Spotless, Error Prone/NullAway, Checkstyle, SpotBugs, the eight ArchUnit rules, the `build-logic` functional
      suite, and every test. No "pre-existing failure" exemption: a mechanical check that is red anywhere is fixed
      before this change is archived. → whole project · `06_DEFINITION_OF_DONE.md#per-change-checklist`,
      `.claude/rules/gradle-build-and-quality.md`
- [x] 7.3 Compare the actionable-task count against change 1's recorded baseline of **91 actionable tasks: 91
      executed**. This change adds no task, no source file, and no dependency, so the count must reproduce; a
      *different* number means something moved that should not have, and a count of `0 executed` means the gate ran
      against a stale cache rather than the new layout. Report the real figure — never assert the baseline.
      → whole project · design.md D4
- [x] 7.4 Run `./gradlew -PstrictLocks verifyLocks` and `./gradlew -p modules/build-logic -PstrictLocks verifyLocks`
      to confirm all eleven committed lockfiles still resolve from their new locations. The gate deliberately does not
      include these (they need the network and are run as their own CI step), so a lockfile that failed to move with
      its module would otherwise go unnoticed until someone else's build broke.
      → whole project · `01_MODULE_INVENTORY.md` (dependency-lock gate), `01_BUILD_AND_TOOLING.md#dependency-locking`
- [x] 7.5 Confirm the scope discipline held: `git diff --stat` against the pre-change commit shows changes only in
      build configuration, documentation, and file locations — zero content diff in any `.java`, `module-info.java`,
      or per-module `build.gradle.kts`. This is the change's central claim, and it is cheap to check and expensive to
      get wrong. → whole project · design.md "Goals / Non-Goals"
- [x] 7.6 Run `sh tooling/hooks-test/run-all.sh` with hooks installed and confirm all three checks pass. This is the
      **only** verification in group 7 that the standing gate does not already provide: the hook checks are run by
      hand, never by Gradle, so task 7.2 going green says nothing about the two fixture paths repaired in task 4.3. A
      failure here reads as "no such file or directory" on the `printf` redirect, which names the cause directly.
      → repo root · `tooling/hooks-test/README.md`, task 4.3, design.md "Risks"
- [x] 7.7 Confirm no new module or package was added, so `01_MODULE_INVENTORY.md` needs no inventory edit beyond the
      layout sentence added in task 5.4 — the eight modules, their packages, and the `arch-test` source set at
      `:app/src/archTest/java` are all unchanged. → `docs/implementation_plan/` · `01_MODULE_INVENTORY.md`
- [x] 7.8 Confirm the offline invariant is untouched: this change adds no dependency and no code, so no new network
      path exists. The only network access remains Gradle dependency resolution and CI, both build-time.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01
- [x] 7.9 Run `openspec validate restructure-module-layout --strict`, confirm it is clean, then archive with
      `openspec archive`. Because `skip_specs: true` is set, nothing folds into `openspec/specs/` — correct, since
      this change added no user-observable behaviour. → `openspec/` · ADR-0016
