# Design — restructure-module-layout

## Context

See `proposal.md` — Why. The decision itself, including why `modules/` over `src/`/`code/` and why `build-logic`
moves with the eight subprojects, is settled in **ADR-0021** and is not re-argued here.

What shapes this design is a survey of what actually points at the old paths. The finding that matters: almost
nothing does. Every module reference in the corpus is either a **Gradle project name** (`:api`, `project(":ui")`) or a
**module-relative citation** (`:app/src/archTest/java`, `:document/ua.bookloom.document.epub`), and neither form
encodes where the directory physically sits. `config/checkstyle/suppressions.xml` matches source sets with the
depth-agnostic regex `files="[\\/]src[\\/](test|archTest)[\\/]java[\\/]"`; `.gitattributes` and `.editorconfig` use
bare globs; CI's artifact paths use `**/build/...`. All of it survives the move untouched.

That is a **negative claim**, and a negative claim is exactly what a reader cannot check by reading the diff — so it
is established by a command rather than by assertion. `git grep -nE 'ua/bookloom|src/main/java|src/test/java|/gradle\.lockfile' -- tooling config scripts .lefthook lefthook.yml`
returns exactly two lines, both in `tooling/hooks-test/`, and they are in the table below. An earlier draft of this
design asserted that `config/**` and `tooling/**` held no module-path literal at all; running the grep disproved it.

The exceptions are few and concentrated, and they divide sharply by failure mode:

| Site | Breaks how |
|---|---|
| `settings.gradle.kts` — `include(...)` with no `projectDir`, `includeBuild("build-logic")` | **Loud.** Configuration-time failure; the build does not start. |
| `build-logic/settings.gradle.kts:12` — `from(files("../gradle/libs.versions.toml"))` | **Loud.** The included build cannot resolve its own catalog. |
| `build-logic/build.gradle.kts:101` — `systemProperty("bookloom.repoRoot", rootDir.parentFile.absolutePath)` | **Silent.** Resolves to a real, existing directory — the wrong one. |
| `DependencyLockingFunctionalTest:119-123, 138-139, 155` — `repoRoot().resolve(module).resolve("gradle.lockfile")` | Fails inside the test, downstream of the silent break above. |
| `.github/workflows/ci.yml:152` — `-p build-logic` | Loud, but only in CI, which is not exercised until the project is feature-complete. |
| `tooling/hooks-test/pre-commit-formats-staged-file.sh:16` and `pre-push-blocks-checkstyle-violation.sh:22` — `HOOKS_TEST_FIXTURE='util/src/main/java/…'` | **Invisible to the gate.** Loud when run, but they are run *by hand*, never by Gradle. |

The eight subprojects are placeholder skeletons — one type plus `package-info.java` plus `module-info.java` each,
four tagged canary tests, and `:app`'s `src/archTest/java` ArchUnit suite with its violation fixtures. **No `.java`
file, no `module-info.java`, and no per-module `build.gradle.kts` needs a single edit.**

## Goals / Non-Goals

**Goals:**

- Relocate the nine directories with history preserved, and repair every path that pointed at their old locations.
- Keep every Gradle project name, JPMS module name, package name, and public type **byte-identical**, so no citation
  anywhere in the corpus — the archived change, `01_MODULE_INVENTORY.md`, the rule files, the ArchUnit rules — goes
  stale.
- Leave the layout knowledge in as few places as possible, so the next move (if there ever is one) is not another
  archaeology exercise.
- Prove the result with the standing green gate rather than with new bespoke tests: if nothing but paths changed, the
  gate that passed before must pass after, at the same task count.

**Non-Goals:**

- **No behaviour, no new capability, no new dependency.** A diff line outside build configuration, documentation, and
  file locations is a defect in this change.
- **Not moving `config/`, `tooling/`, or `scripts/`.** ADR-0021 keeps them at the root: they hold quality rulesets,
  git-hook shell scripts, and packaging drivers rather than compiled sources, and every reference to them resolves
  from the root project directory, which does not move. Moving them would add breakage for no gain. Two files *inside*
  `tooling/hooks-test/` are nonetheless edited — they hard-code a fixture path into `util/` — but the directory itself
  stays where it is.
- **Not implementing `collectDist`.** ADR-0021 settles that it will stage into `modules/app/build/dist/libs/`; writing
  the task is change 2's job (`bootstrap-app-launch-and-empty-window`).
- **Not editing `docs/specification/**`.** It is frozen. ADR-0021 supersedes the two affected clauses.
- **Not renaming projects to `:modules:api`.** See D2.

## Decisions

### D1 — `git mv` for all nine directories, in the same commit as the configuration repair

Use `git mv`, not `rm` + re-add. Git infers renames from content similarity rather than recording them, and the
inference is reliable here (files are unchanged), but `git mv` stages the move as a rename directly and removes the
question. `git log --follow` on any file then crosses the move.

The move and the configuration repair **must land together**. Between the two the repository does not configure at
all: `settings.gradle.kts` points `include(":api")` at a directory that no longer exists. Splitting them would put a
commit in history that cannot build, which breaks `git bisect` for everyone afterwards.

*Alternative considered:* stage the move behind a transitional `settings.gradle.kts` that accepts both layouts.
Rejected — it doubles the configuration surface to protect a window measured in seconds, and the transitional code
would then need its own removal commit.

### D2 — Explicit `projectDir` per subproject; project **paths stay `:api`, not `:modules:api`**

Gradle offers two ways to nest subprojects, and they are not equivalent:

```kotlin
// Chosen — the directory moves, the project path does not.
include(":api")
project(":api").projectDir = file("modules/api")

// Rejected — Gradle derives the path from the directory structure.
include(":modules:api")   // project path becomes :modules:api
```

The second form is shorter and needs no `projectDir` line, but it renames every project. `:api` would become
`:modules:api`, and with it: every `project(":api")` dependency declaration in the eight build scripts, every
`./gradlew :app:run` / `:app:collectDist` / `:app:test` invocation in the specification's task surface, the
`:module/package` citation convention that `01_MODULE_INVENTORY.md#how-to-cite-a-module` defines and that the whole
corpus uses, and every task citation in the archived change. ADR-0021's second decision driver rules this out: the
Gradle project name is the join key for the documentation, and the move is worth doing precisely because it costs
nothing that anything else depends on.

So: eight explicit `projectDir` assignments, and `includeBuild("modules/build-logic")` in `pluginManagement`.

### D3 — One place owns the `modules/` path segment inside `build-logic`'s test fixture

`build-logic/build.gradle.kts:101` publishes the repository root to its functional tests:

```kotlin
// `rootDir` here is `build-logic/`, so its parent is the repository root.
systemProperty("bookloom.repoRoot", rootDir.parentFile.absolutePath)
```

After the move `rootDir` is `modules/build-logic`, so `rootDir.parentFile` is `modules/` — **an existing directory,
so nothing throws.** `BuildFixture.REPO_ROOT` then normalizes a valid path to the wrong place, and the failures
surface far away: `qualityConfig()` cannot find `config/checkstyle/checkstyle.xml` or `lombok.config`,
`LicenseGateFunctionalTest:138` cannot find `config/license/allowed-licenses.json`, and every lockfile assertion
misses. This is the one genuinely dangerous edit in the change, because the symptom does not name the cause.

The fix is `rootDir.parentFile.parentFile`, with the comment corrected to say why there are now two hops.

For the module lockfile paths, do **not** sprinkle `.resolve("modules")` across the three call sites in
`DependencyLockingFunctionalTest` (lines 119-123, 138-139, 155). Add one accessor to `BuildFixture` beside the
existing `repoRoot()`:

```java
/** The directory holding the nine code directories (ADR-0021), so the layout is named in exactly one place. */
static Path moduleDir(String module) {
    return REPO_ROOT.resolve("modules").resolve(module);
}
```

and call it from all three sites. The tests then assert about *a module* rather than about *a path*, and the string
`"modules"` appears once in the whole `build-logic` test tree.

*Alternative considered:* publish a second system property `bookloom.modulesRoot` from `build.gradle.kts`, derived as
`rootDir.parentFile` — which post-move is exactly `modules/`. Rejected as too clever: it re-derives the layout from
`build-logic`'s own accidental position, so the property is correct only as long as `build-logic` is itself a direct
child of `modules/`. A plain `resolve("modules")` states the assumption instead of encoding it in a coincidence.

### D4 — The gate is the proof; no new test asserts the layout

The change adds no test that asserts "the modules live under `modules/`". Such a test would be a tautology over the
build configuration — it can only pass, since `settings.gradle.kts` had to be correct for the build to configure at
all. The real proof is the standing gate, `./gradlew clean build check spotlessCheck`, whose task count is a known
quantity: change 1 closed at **91 actionable tasks: 91 executed**. If nothing but paths changed, that number and that
result must reproduce.

The `build-logic` functional suite already covers the interesting half — `DependencyLockingFunctionalTest` asserts
that every one of the nine modules carries committed lock state and that `:ui`/`:app` exclude the JavaFX classpaths.
Those tests fail loudly if the move loses a lockfile or mis-resolves a path, which is precisely the coverage this
change needs. Repointing them at `moduleDir(...)` keeps that guarantee rather than adding a new one.

**`./gradlew :build-logic:clean` must run before the gate.** `build-logic` is an *included build*, and the root
`clean` does not reach into it — its functional tests can otherwise report `UP-TO-DATE` from a cache populated before
the move, which is exactly the false green this change must not accept. The gate command for this change is
therefore `./gradlew :build-logic:clean && ./gradlew clean build check spotlessCheck`.

### D5 — An unnumbered Stage A entry; changes 1-28 keep their numbers

This change is added to the Stage A table as an **unnumbered row** (number column `—`) between change 1 and change 2.
`bootstrap-app-launch-and-empty-window` stays **change 2**, every Stage B-E entry keeps its number, and not one prose
cross-reference moves.

An earlier draft renumbered instead: this change became Stage A change 2 and everything after it shifted by one. That
was rejected on a fact the draft never checked — **change numbers are cited well outside `CHANGE_BACKLOG.md`, in
documents that must not be edited:**

| Site | Citations | May it be edited? |
|---|---|---|
| `07_ROADMAP.md:69-77` (the F1-F9 seam table) plus prose at 94, 96, 97, 103 | ~13 | Yes, but it is the binding roadmap and would have to move in lockstep |
| `ADR-0017:71, 99, 148` | change 2 ×3 | **No** — an accepted ADR is an immutable record |
| `ADR-0018:139, 186, 188` | changes 6, 18 | **No** |
| `ADR-0019:31, 75, 101` | change 27 ×3 | **No** |
| `lefthook.yml:63` | change 27 | Yes |
| archived `bootstrap-gradle-and-quality-toolchain/design.md:13, 28, 30, 31, 98` | changes 2, 27 | **Never** — an archived change is not reopened (`spec-authoring.md`) |

Renumbering is therefore not merely expensive, it is **unresolvable**: edit those records and the change violates ADR
immutability and the never-reopen rule; leave them and the corpus permanently contradicts itself — which is precisely
the outcome the draft's own task 6.2 called "worse than none". The `28 changes` headline count in `AGENTS.md:96`,
`07_ROADMAP.md:9`, and `ADR-0017:56,123` compounds it, two of those four sites being immutable as well.

The argument that forced the renumber does not survive either. `#how-to-use-this` says "take the lowest-numbered
change whose stage dependencies are satisfied", and that is claimed to require a gap-free sequence — but the reader is
**already** required to filter, because change 1 is archived and so the lowest-numbered eligible change is already not
literally 1. A rule that already needs a filter tolerates one entry whose position is stated outright.

One honest consequence, recorded in the backlog rather than hidden: there are now **29 changes in 28 numbered slots**.
ADR-0017's "28 changes" counts the *planned delivery sequence*, which this interstitial infrastructure change does not
join. That is one sentence of explanation against roughly forty edits across documents designed to be permanent.

### D6 — Prune `.gitignore`'s `*/build/`, keep the unanchored `build/`

`.gitignore:2` is `build/` — unanchored, so per gitignore semantics it matches a directory named `build` at any
depth, including `modules/api/build/`. `.gitignore:3` is `*/build/`, which matches exactly one path segment before
`build/` and therefore stops matching after the move.

Nothing breaks: line 2 already covers every case line 3 did, so no build output becomes tracked. But a pattern that
matches nothing is a trap for the next reader, who will reasonably assume it is load-bearing. Delete it.

## Risks / Trade-offs

- **The silent `rootDir.parentFile` mis-resolution (D3) is applied but not noticed** → It cannot survive the gate:
  `LicenseGateFunctionalTest` and `DependencyLockingFunctionalTest` both dereference `repoRoot()` against files that
  exist only at the true root, so a wrong value fails them. The risk is misdiagnosis, not escape — hence D3 records
  the symptom-to-cause mapping explicitly, so whoever sees "cannot find `checkstyle.xml`" looks at line 101 rather
  than at Checkstyle.
- **A stale Gradle cache or daemon reports a false green** → The gate runs `:build-logic:clean` first (D4). Beyond
  that, `.gradle/` and `.kotlin/` are ignored and untracked, so a fresh clone is unaffected regardless of local state.
- **The two `tooling/hooks-test/` fixture paths are the one breakage the gate cannot see** → Every other site in this
  change fails loudly inside `./gradlew clean build check spotlessCheck`. These two do not: the hook checks are run by
  hand (`tooling/hooks-test/README.md` — "run by hand, not by Gradle"), so a green gate proves nothing about them. They
  are repaired in task 4.3 and verified by running `sh tooling/hooks-test/run-all.sh` in task 7.6, which is added to
  the gate group precisely because the standing gate does not cover it. This is the change's blind spot, so it is
  closed by an explicit step rather than by the gate.
- **CI is not exercised, so the `-p modules/build-logic` fix is unverified** → Accepted, and consistent with the
  standing decision that CI is validated once at the end of the project rather than per change (recorded under the
  archived change's task 7.7). The edit is one token on one line; it is carried as documented debt, not as a claim.
- **A contributor's IDE breaks after pulling** → One re-import. No tracked `.idea/` file exists (`git ls-files`
  confirms the directory is entirely ignored), so nothing in version control needs updating and no one else's state
  is affected.
- **The frozen spec now describes a layout the repository does not have** → Unavoidable, and the sanctioned cost of a
  frozen catalog. ADR-0021 supersedes all five passages explicitly and names each by anchor, so the divergence is
  recorded rather than discovered. `01_BUILD_AND_TOOLING.md#project-layout` additionally still calls the repository
  root `tranlator_app/`, a pre-existing drift this change neither introduces nor repairs.

## Migration Plan

There is nothing deployed and no data to migrate; "migration" here is the working-copy transition.

1. **Move** — `git mv` the nine directories into `modules/` (task group 1).
2. **Repair the build** — `settings.gradle.kts`, then the three `build-logic` sites, in that order: the build cannot
   configure until the first is done, and `build-logic` cannot configure until the second (task groups 2-3).
3. **Repair the periphery** — CI, the two `tooling/hooks-test/` fixture paths, `.gitignore` (task group 4).
4. **Update the documentation** — ADR-0021 is already written; `AGENTS.md`, `README.md`, `01_MODULE_INVENTORY.md`,
   and the backlog's new unnumbered Stage A row follow (task groups 5-6).
5. **Gate** — `./gradlew :build-logic:clean` then `./gradlew clean build check spotlessCheck`, and confirm the task
   count matches change 1's baseline (task group 7).

**Rollback** is `git revert` of the single commit. Because the move and the repair are one commit (D1), the revert
restores a configuring build in one step; there is no partial state to unwind and no external system to reconcile.
