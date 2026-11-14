# CI / release pipeline research (2026-11-14)

Read-only research. Nothing in the repository was changed. Every platform fact carries its source; a line marked
**[inference]** is my reasoning, not a documented fact, and a line marked **[not verified]** could not be confirmed from
an official page.

## 0. What exists today (from the repository)

| Item | Fact | Where |
|---|---|---|
| Workflows | One: `ci.yml`. `build.yml` and `release.yml` were specified, never built | `.github/workflows/ci.yml`, `docs/spec_baseline/specification/04_Build_and_Release/04_CI_CD.md` |
| Triggers | `pull_request` to `master`; `push` to every branch except `master` | `ci.yml` |
| Concurrency | `group: ci-<workflow>-<ref>`, `cancel-in-progress: true` — already there. A branch push and its PR are different refs, so both run | `ci.yml` |
| Quality job | `ubuntu-24.04`, setup-java `cache: gradle` (keyed on wrapper, catalog, lockfiles), Xvfb, `verifyLocks` twice (`--no-daemon`), `./gradlew clean build check spotlessCheck`, agent-file sync, `checkLicense` | `ci.yml` |
| Packaging | 5 legs, `needs: quality`: macOS aarch64 (`macos-15`), macOS x86_64 (`macos-15-intel`), Linux x86_64, Linux aarch64 (`ubuntu-24.04-arm`), Windows x86_64 (`windows-2025`, Liberica `jdk+fx`). Launch smoke on all but Windows | `ci.yml` |
| Measured on the runner | Last PR run (43389463240): quality job 32.6 min, of which the gate step 30.5 min and the lock step 98 s; packaging legs 3.3–8.2 min (macOS Intel slowest); wall clock 41 min. The push run before it: 36 min quality, 67 min wall (a macOS leg queued) | `gh run view` |
| Local gate | 17 m 45 s – 20 m 17 s on a 10-core Apple Silicon machine | `docs/development/getting-started.md`, `testing.md` |
| Test forks | `maxParallelForks = min(4, cores/2)` for `test`/`fastTest`; `-Pbookloom.forks=N` overrides. A 4-vCPU runner therefore uses **2** forks | `modules/build-logic/.../bookloom.test-conventions.gradle.kts` |
| Off on purpose | No `gradle.properties`. `org.gradle.parallel` off (Spotless `NoClassDefFoundError` when `clean` closes shared formatter class loaders); build cache off (it would let `clean build check` load test results instead of running them) | `docs/development/testing.md` |
| Version | Root `version = -PappVersion` else `dev`; `:app:generateVersionResource` writes it to `version.properties`. Scripts read `APP_VERSION` from the environment, else `dev` | `build.gradle.kts`, `modules/app/build.gradle.kts`, `scripts/jpackage-common.sh` |
| Repository | Public. Squash, merge-commit and rebase merging are all allowed. No classic branch protection and no rulesets on `master` (API answers "Branch not protected"; the rulesets list is empty) | `gh repo view`, `gh api` |
| Design rule D8 | CI's gate command is character-for-character the pre-push command. The owner's new requirements change this on purpose (see decision 1) | `ci.yml` header, `lefthook.yml` |

## A. Triggers

### A1. Feature-branch push: build only

- `on: push: branches-ignore: [master]` (as today). A workflow with only `branches-ignore` does not run for tag pushes
  ("the workflow won't run for events affecting the undefined Git ref") —
  <https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax>.
- One job: compile and assemble, no `test` task.

### A2. Pull request to master: full verification

- `on: pull_request: branches: [master]`. Default activity types are `opened`, `synchronize`, `reopened`; the run builds
  the merge ref `refs/pull/N/merge`; it does not run while the PR has a merge conflict —
  <https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows>.

### A3. Push to master: nothing after a merged PR, a build after a direct commit

`on:` filters cannot express "came from a PR". A workflow must start and a first small job must decide. Three ways:

| Way | How | Robust? | Needs |
|---|---|---|---|
| **REST "list pull requests associated with a commit"** | `gh api repos/{owner}/{repo}/commits/{sha}/pulls`; non-empty with a `merged_at` means the commit came from a PR | **Yes.** The endpoint "Lists the merged pull request that introduced the commit to the repository". Checked on this repository: squash commit `8d507357` returns PR 4, squash commit `79ffede6` (whose title has **no** `(#N)`) returns PR 3, a direct commit returns an empty list | `GH_TOKEN: ${{ github.token }}`. **[not verified]** the docs page did not state the permission; from the REST permission tables I recall `pull-requests: read` or `contents: read`. Grant both `contents: read` and `pull-requests: read` to the job. Docs: <https://docs.github.com/en/rest/commits/commits#list-pull-requests-associated-with-a-commit> |
| `pull_request` `closed` + `merged == true` instead of `push` | A separate event; documented pattern `if: github.event.pull_request.merged == true` | It tells you a PR merged; it does **not** tell a `push` run to stay quiet, and it never fires for a direct commit. Not a solution for requirement 3 by itself | nothing |
| Commit message `(#N)` / `Merge pull request #N` | String test on `github.event.head_commit.message` | **No.** The title is editable at merge time (this repository already has a squash commit without `(#N)`), a direct commit can contain `(#12)`, and rebase merges carry no PR reference | nothing |

How the three merge strategies look to a `push` run:

- **Squash:** one new single-parent commit; default title `<PR title> (#N)`. The API maps it to the PR.
- **Merge commit:** one two-parent commit `Merge pull request #N from …`. The API maps it to the PR.
- **Rebase:** N new commits with new SHAs and unchanged messages. **[inference]** the API still maps each to the PR
  (GitHub shows the PR on such commits), but I did not test it here; if the owner keeps only squash merging the question
  disappears.

Recommended: a `detect` job (about 10 s on `ubuntu-24.04`) that calls the API for `github.sha` and sets an output; the
`build` job has `needs: detect` and `if: needs.detect.outputs.from_pr != 'true'`. If the API call fails, treat it as
"not from a PR" and build (fail towards building). Honest wording for the spec: after a merged PR the workflow run
exists, shows the build job as skipped, and costs one short job — "nothing extra" cannot be literally zero runs.

**[inference]** The PR association is present when the `push` event is handled, because GitHub creates the commit as
part of merging the PR. If that ever proves racy, one retry after a few seconds is enough.

### A4. Release on a semver tag on master

**Tag filter.** `on.push.tags` takes globs, not regular expressions. Documented characters: `*` (any characters except
`/`), `?` (zero or one of the preceding character), `+` (one or more of the preceding character), `[]` (one listed
character; ranges `a-z`, `A-Z`, `0-9` only). The docs' own example is `v[12].[0-9]+.[0-9]+`. A pattern that starts with
`*`, `[` or `!` must be quoted —
<https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#filter-pattern-cheat-sheet>.

Patterns that accept exactly the four examples:

```
'v[0-9]+.[0-9]+.[0-9]+'             # v1.0.0
'v[0-9]+.[0-9]+.[0-9]+-alpha[0-9]+' # v1.0.0-alpha1
'v[0-9]+.[0-9]+.[0-9]+-beta[0-9]+'  # v1.0.0-beta2
'v[0-9]+.[0-9]+.[0-9]+-RC[0-9]+'    # v1.0.0-RC1
```

These reject `v1.0`, `1.0.0`, `v1.0.0-foo`, `v1.0.0-rc1` (lower case) and `release-1`. **[inference]** the match is on
the whole tag name (the docs do not say "anchored" in words), so the first job must repeat the check with a real regular
expression — `^v[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|RC)[0-9]+)?$` — and fail otherwise. Two more documented facts: path
filters are not evaluated for tag pushes, and no event is created when more than three tags are pushed at once
(<https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#push>).

**Tag must be on master.** Check out with `fetch-depth: 0` (a default checkout of a tag is one commit deep and has no
`origin/master`), then `git merge-base --is-ancestor "$(git rev-parse "$GITHUB_REF_NAME^{commit}")" origin/master`; a
non-zero exit fails the release. `git branch -r --contains` works too but prints text that must be parsed; the
`merge-base` exit code is simpler. The spec already requires this check (EC-REL-1 in `04_CI_CD.md`).

**Prerelease.** Shell test: a tag that contains `-` gets `--prerelease`. `gh release create` flags, from
<https://cli.github.com/manual/gh_release_create>: `--prerelease` "Mark the release as a prerelease"; `--verify-tag`
"Abort in case the git tag doesn't already exist in the remote repository"; `--generate-notes`; `--draft`;
`--notes-file`; `--latest=false`. Without `--verify-tag`, `gh` would create a missing tag from the default branch.

**Permissions.** Workflow level `contents: read`; only the publishing job gets `contents: write`. No other secret.

**How to publish.** `gh release create` with the built-in token (`GH_TOKEN: ${{ github.token }}`) is the simplest and
safest: `gh` is preinstalled on GitHub-hosted runners, there is no third-party code to pin by SHA, and the flags are
explicit. A release action (the spec's wording, "a release action") adds a dependency for nothing. Recommended order:
create the release as a **draft** with all files and a `SHA256SUMS` file, then `gh release edit --draft=false`, so a
failed upload never leaves a half-published release. Events caused by the built-in token do not start other workflows
(same events page), so publishing cannot loop.

## B. "Do not rebuild in the PR what the branch already built"

What GitHub-hosted runners really allow:

1. **Every job starts on a fresh machine.** There is no `build/` directory to reuse; `clean` in CI deletes nothing.
   **[inference]** So the `clean` in the CI gate changes no result there; its only effect is the task itself. The D8
   argument for it ("CI and pre-push disagree about incremental state") is about the developer's machine.
2. **Actions cache scope.** "Workflow runs can restore caches created in either the current branch or the default
   branch"; a pull-request run "can also restore caches created in the base branch"; the docs' example says a PR run for
   `feature-b` with base `feature-a` can use caches from `main`, `feature-a` and `feature-b`. So a cache written by the
   feature-branch push **is readable** by the PR run for that branch. A cache written by a PR run is scoped to
   `refs/pull/N/merge` and is readable only by later runs of the same PR. Limits: 10 GB per repository, entries unused
   for 7 days are removed, oldest-accessed evicted first —
   <https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching>.
3. **Gradle build cache through `gradle/actions/setup-gradle`.** The action saves the Gradle User Home, including the
   local build cache (`caches/build-cache-1`), downloaded dependencies, wrapper distributions and compiled build
   scripts. By default it "will only write to the cache from Jobs on the default (`main`/`master`) branch"; other
   branches and pull requests only read. To let a feature-branch push feed its PR, that job needs
   `cache-read-only: false` — <https://github.com/gradle/actions/blob/main/docs/setup-gradle.md>. Gradle's cacheable
   tasks include `JavaCompile`, `Checkstyle`, `Test` and `JacocoReport`; after `clean`, a build with the cache on shows
   tasks as `FROM-CACHE` — <https://docs.gradle.org/current/userguide/build_cache.html>.
4. **Artifacts across workflows.** `actions/download-artifact` can fetch from another run with `run-id` plus
   `github-token` ("required when downloading artifacts from a different repository or from a different workflow run",
   needs `actions: read`) — <https://github.com/actions/download-artifact>. **[inference]** Passing `build/` folders
   this way does not make Gradle skip work: its up-to-date history lives in the project's `.gradle/` folder, and the PR
   builds the merge commit, not the branch tip. Not recommended.
5. **`workflow_run`.** Runs only from the workflow file on the default branch, with `GITHUB_SHA` of the default branch,
   at most three levels deep (events page). It chains workflows; it does not share compiled output. Not useful here.

What is honest to promise:

- With the Gradle build cache on in CI and the branch-push job allowed to write, the PR run **loads compile and static
  analysis outputs for every module whose inputs are identical** to what the branch push built. They are identical when
  `master` has not moved under the branch; when it has, only the modules touched by the difference recompile.
- **Tests are not saved by the branch push** (it runs none). If PR runs may write the cache too, a second push to the
  same PR reuses the test results of modules the new commit does not reach (a `:ui`-only change skips the `:pipeline`,
  `:document`, `:llm`, `:persistence` tests; an `:api` change skips nothing).
- **[inference]** The saving on a first PR run is the compile and analysis time, a few minutes of about thirty; the
  larger saving is on fix-up pushes to an open PR.

What changes if the PR job drops `clean` and turns the cache on:

- In CI, dropping `clean` alone changes nothing (point 1). Turning the cache on is the real decision: a task with
  unchanged inputs is not run again. That is correct only as far as the tasks declare their inputs. Known undeclared
  input here: the **platform font** the fit tests measure with (AGENTS.md "What will bite you") — a runner image update
  changes fonts while the cache key stays the same, so a stale green result is possible for `:ui`.
- The pre-push hook is untouched as long as the cache is switched on by a CI-only flag (`--build-cache` on the command
  line, no `gradle.properties`), so the local gate still runs everything.
- Safe middle: cache on for compile and analysis, and `:ui:test` (and optionally every `test` task) always run in the PR
  (`--rerun` on those tasks, or `outputs.cacheIf { false }` under a CI flag). Then the promise is exactly "compilation
  is not repeated; every test runs on every PR commit".

## C. Speed

Facts that set the frame:

- Standard GitHub-hosted runners are free for public repositories; larger runners are billed even there —
  <https://docs.github.com/en/billing/concepts/product-billing/github-actions>. So for this repository extra parallel
  jobs cost nothing; the per-minute rates listed there (Linux $0.006, Windows $0.010, macOS $0.062 per minute) apply to
  private repositories only.
- Public-repository runner sizes: Linux x64 and arm64 and Windows 4 vCPU / 16 GB; macOS arm64 3 cores / 7 GB; macOS
  Intel 4 cores / 14 GB — <https://docs.github.com/en/actions/reference/runners/github-hosted-runners>.

| Measure | Expected effect | Risk |
|---|---|---|
| **Split the PR verification into parallel jobs** (static analysis · core tests · `:ui` tests · packaging), each on its own 4-vCPU machine | The biggest lever. Wall clock falls from the sum to the longest job. **[inference]** about 41 min → 15–18 min | Each job compiles again unless the build cache is on (2–4 min each **[inference]**). The gate is no longer one command (decision 1). Excluding `test` from the static job must also exclude `jacocoTestCoverageVerification`, because the coverage convention fails a module at 0 % when no execution data exists |
| **Packaging no longer waits for quality** (drop `needs: quality`) | Saves the whole serial packaging stage, 4–8 min of wall clock | A red quality job no longer stops the legs; free on a public repository |
| **`gradle/actions/setup-gradle` instead of setup-java `cache: gradle`** | Also caches compiled build scripts and the `build-logic` plugin build, restores by fallback keys instead of one exact key. **[inference]** 0.5–1.5 min per job; it should cut most of the 98 s lock step. The docs say to avoid combining it with setup-java's `cache: gradle` | The default "enhanced" caching uses a closed-source library under Gradle's terms, free for public repositories; `cache-provider: basic` is the MIT implementation with fewer features (<https://github.com/gradle/actions/blob/main/DISTRIBUTION.md>). Decision 6. Pin by SHA like every action |
| **Build cache in CI only** (section B) | Compile and analysis reused between branch push, PR and fix-up pushes; unchanged modules' tests reused on fix-ups if allowed | Undeclared test inputs (fonts). Cache storage churn against the 10 GB limit |
| **More test forks on the `:ui` job** (`-Pbookloom.forks=3` or `4`) | Runner has 4 vCPU, the build uses 2 forks. **[inference]** 20–35 % off `:ui:test` | TestFX in software rendering is CPU-bound; timing-sensitive tests may flake. Try on one job, keep if three runs are green |
| **Drop `clean` in CI** | Seconds | None in CI (fresh machine). Text of D8 must change |
| **One Gradle invocation less** (lock check without `--no-daemon`, or in the static job) | 20–40 s **[inference]** | None |
| **Path filter for documentation-only changes** (`paths-ignore`) | Skips the whole run for such pushes | Must be narrow: tests read files under `docs/` (`EpubFixtures`, `EpubCanonicalAssert`, the arch tests), the packaging icons live under `docs/spec_baseline/…/assets/icon`, and the agent-file sync check reads `AGENTS.md`, `.claude`, `.agents`. If a skipped workflow is ever a required check it stays "Pending" and blocks the merge (workflow-syntax page). Safe list: `docs/development/**`, `docs/project/**`, `docs/screens/**`, `README.md`, `openspec/**` |
| **Cancel superseded runs** | Already there | — |
| **Fewer packaging legs on PRs** | Not needed for wall clock once the legs run beside the tests; saves queueing for macOS runners (one run waited 26 min for a macOS leg) | Fewer legs = an OS-specific break is found at release time. Decision 5 |
| **Configuration cache** | Saves configuration time only; on a fresh runner there is no stored entry unless the action caches it, which needs an encryption-key secret (setup-gradle docs) — the pipeline's "no secrets" rule. **[inference]** near zero gain in CI | Core plugins `java`, `checkstyle`, `jacoco` are supported (<https://docs.gradle.org/current/userguide/configuration_cache_status.html>); Gradle's adoption guide names Spotless as supported. The build's own `verifyLocks`/`resolveAndLockAll` are already marked incompatible. Error Prone, SpotBugs, the license-report and Sonar plugins: **[not verified]**. It also runs tasks in parallel, the same class of risk as `org.gradle.parallel`. Not recommended now |
| **`org.gradle.parallel`** | Docs here: "seconds, not minutes" | Known Spotless failure with `clean`. Leave off |
| **Larger runners** | Faster | Billed even for public repositories. No |

### Recommended job graph

Pull request to `master` (all jobs start together; a last `verified` job `needs` them all and is the one check to
require):

| Job | Runs | Runner | Estimate **[inference]** |
|---|---|---|---|
| `static` | lock check, `spotlessCheck`, compile with Error Prone + NullAway, Checkstyle, SpotBugs, `:app:archTest`, agent-file sync, `checkLicense` | `ubuntu-24.04` | 6–9 min |
| `test-core` | `test` + coverage verification for `:api :util :document :llm :persistence :pipeline`, and `:app:test` | `ubuntu-24.04` | 9–13 min |
| `test-ui` | `:ui:test` (Xvfb as today) | `ubuntu-24.04` | 12–16 min |
| `package` ×5 (or ×3) | `collectDist`, script, launch smoke | per OS | 4–8 min each |
| `verified` | nothing; fails if any needed job failed | `ubuntu-24.04` | seconds |

Wall clock about 15–18 min against 41 min today. These are estimates: the runner has never reported per-task times.
Measure once with `./gradlew … --profile` (a local HTML report, nothing is sent anywhere) before fixing the split.

Feature-branch push: one `build` job — `./gradlew assemble` plus test compilation (decision 2), about 3–5 min
**[inference]**; with the cache warm, less.

Push to `master`: `detect` (10 s) → `build` only for a direct commit.

## D. Packaging facts

**What the scripts produce today**

| OS | Script | Output in `build/package/` | Uploaded by CI |
|---|---|---|---|
| macOS | `package-macos.sh` | `BookLoom.app`, `BookLoom-<v>-macos-<uname -m>.tar.gz`, a `.dmg` (jpackage's own name, `BookLoom-<numeric>.dmg`) | tar.gz, dmg |
| Linux | `package-linux.sh` | `BookLoom/`, `BookLoom-<v>-linux-<uname -m>.tar.gz`, a `.deb` when `fakeroot` exists | tar.gz, deb |
| Windows | `package-windows.bat` | `BookLoom\` with `BookLoom.exe` — nothing else | **nothing** (the upload globs match no file) |

`uname -m` gives `arm64` on Apple Silicon, `x86_64` on Intel macOS, `x86_64`/`aarch64` on Linux, so macOS files are
named `macos-arm64` while the CI matrix says `macos-aarch64`. The release needs one naming.

The requirement says "no installers". The `.dmg` and `.deb` are installer-type packages (jpackage types `dmg`, `deb`).
A release that follows the requirement ships only the app-image archives (decision 4).

**Windows portable zip.** Needed: zip `build\package\BookLoom\` into `BookLoom-<v>-windows-x86_64.zip` after jpackage
(PowerShell `Compress-Archive`, or `tar -a -c -f` which Windows ships), upload it, add `*.zip` to the upload paths.
Unlike macOS, a Windows zip loses nothing (no execute bit). There is no launch smoke on Windows (`launch-smoke.sh` is
POSIX only); a release publishes that leg unlaunched (decision 8).

**Icons.** jpackage takes `--icon <path>`; the expected formats are `.icns` on macOS, `.ico` on Windows, `.png` on Linux
(resource names `<launcher>.icns` / `.ico` / `.png`), and a Windows icon that is not an ICO "will not be used. The
default icon will be used" — <https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html>,
<https://docs.oracle.com/en/java/javase/25/jpackage/override-jpackage-resources.html>, JDK `WinResources.properties`.
All three scripts already pass `--icon`: `dist/macos/BookLoom.icns`, `dist/linux/BookLoom.png`,
`dist/windows/BookLoom.ico`. So the macOS bundle and the Windows `.exe` get the icon; the Linux app-image keeps the PNG
at `lib/BookLoom.png` **[inference from jpackage's layout, not checked on a built image]**.

Icon files in the repository (`git ls-files`): only under `docs/spec_baseline/specification/assets/icon/` — the source
`BookLoom-source.png`, the master `appicon.png`, `dist/macos/BookLoom.icns`, `dist/windows/BookLoom.ico`,
`dist/linux/BookLoom.png` (512 px) and `dist/linux/hicolor/<size>/apps/bookloom.png` (7 sizes), plus the two generator
scripts. Used by packaging: the `.icns`, the `.ico`, `dist/linux/BookLoom.png`. Unused: the hicolor set, `appicon.png`
(except as generator input). No duplicate anywhere else. Two gaps:

1. The packaging input lives in a documentation tree; a doc move already broke the Windows script once (commit
   "fix(packaging): the Windows script pointed at the icon folder's old path", 2026-11-12).
2. No code calls `Stage.getIcons()` and no icon is in a module's resources, so the running window has no icon of its
   own. **[inference]** On Linux and Windows the title bar and task bar then show the toolkit's default icon, whatever
   jpackage put on the launcher; check by hand.

**Unsigned macOS app — facts for the README.**

- The app is not signed with a Developer ID and not notarised, so Gatekeeper blocks the first launch of a downloaded
  copy. Apple's documented way: try to open it, then System Settings → Privacy & Security → Security section → **Open
  Anyway** (offered for about an hour), enter the login password —
  <https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unknown-developer-mh40616/mac>. The spec's "right-click
  → Open" wording is not what Apple documents for current macOS.
- The alternative the spec names, `xattr -d com.apple.quarantine <path>` (or `xattr -dr` on the `.app`), removes the
  download flag; it is a common practice, not an Apple-documented procedure.
- Ad-hoc signing: **[not verified from an official page]**. The jpackage manual does not mention it. An OpenJDK
  core-libs-dev message says jpackage signs the app image ad hoc when `--mac-sign` is absent, since JDK 19 on Apple
  Silicon (JDK-8277493) and JDK 20 on Intel (JDK-8298488)
  (<https://mail.openjdk.org/pipermail/core-libs-dev/attachments/20230811/a19de894/attachment-0001.htm>). No built image
  was on this machine to check. Verify once in CI with `codesign -dv BookLoom.app`. An ad-hoc signature lets the binary
  run on Apple Silicon; it does not satisfy Gatekeeper.
- Windows: SmartScreen warns on the unsigned `.exe` ("More info → Run anyway", spec `03_PACKAGING_JPACKAGE.md`).

**Version flow.**

- jpackage on macOS: "'app-version' may have between 1 and 3 numbers: 1, 1.2, 1.2.3." and "The first number in an
  app-version cannot be zero or negative." (JDK 25 `MacResources.properties`,
  <https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/jdk.jpackage/macosx/classes/jdk/jpackage/internal/resources/MacResources.properties>).
  So `1.0.0-RC1` is rejected there. The Windows rules found (2–4 components, major and minor 0–255, build 0–65535) are
  the MSI ProductVersion rules (`WinResources.properties`); whether a suffix is rejected for a Windows **app-image** is
  **[not verified]** — pass numbers only and the question does not arise.
- The scripts already map it: `numeric_version` strips a leading `v` and everything after `X.Y.Z`; the `.bat` cuts at
  the first `-` or `+`. `1.0.0-RC1` → jpackage `1.0.0`; the file name and the version the app reports keep `1.0.0-RC1`.
- **Trap:** a version whose first number is 0 (`v0.9.0`) silently becomes the placeholder `1.0.0` in the OS metadata on
  every OS (the script's check `^[1-9]`). Fine if the first release is 1.0.0; otherwise the release job must refuse a
  `v0.*` tag.
- Flow in the release workflow: tag `v1.0.0-RC1` → `full=1.0.0-RC1` (tag without `v`) → `./gradlew :app:collectDist
  -PappVersion=$full` (the version resource, so About and the log say `1.0.0-RC1`) → `APP_VERSION=$full` in the
  environment of the script (it names the files and derives the numeric part itself). The spec text says the script
  receives the numeric string; the scripts as built want the full one. The `.bat` does not strip a leading `v`, so pass
  the version without it. The smoke can then assert `app started version=1.0.0-RC1`.
- Two prereleases of one version (`-RC1`, `-RC2`) have the same OS-level version `1.0.0`. Harmless for portable
  archives.

**Release file names** (keep the scripts' pattern, fix the arch word):

```
BookLoom-1.0.0-macos-aarch64.tar.gz
BookLoom-1.0.0-macos-x86_64.tar.gz
BookLoom-1.0.0-linux-x86_64.tar.gz
BookLoom-1.0.0-linux-aarch64.tar.gz
BookLoom-1.0.0-windows-x86_64.zip
SHA256SUMS
```

Linux icon as a file: the PNG is already inside the archive; the hicolor set and a sample `.desktop` file can be added
to the archive if wanted (decision 7).

## E. Proposed minimal file set (description only)

| File | Trigger | Jobs |
|---|---|---|
| `.github/workflows/build.yml` | `push` to every branch (no tags) | `detect` (only meaningful on `master`: commit → PR lookup) → `build` (`assemble` + test compilation; on `master` only when the commit did not come from a PR). Concurrency per ref, cancel in progress. `contents: read`, `pull-requests: read` |
| `.github/workflows/verify.yml` (today's `ci.yml`, renamed or kept) | `pull_request` to `master` | `static`, `test-core`, `test-ui`, `package` matrix (launch smoke, artifacts kept 14 days), `verified`. `contents: read` |
| `.github/workflows/release.yml` | `push` of the four tag patterns | `check-tag` (strict regex, refuse `v0.*` if decided, ancestor of `origin/master`, outputs `full` and `prerelease`) → `package` matrix ×5 (`collectDist -PappVersion`, script with `APP_VERSION`, launch smoke, upload with 1-day retention) → `publish` (`contents: write`; download, `SHA256SUMS`, `gh release create --draft --verify-tag --generate-notes [--prerelease]` with a notes file holding the "open anyway" steps, then publish). No tests, no secrets |
| `.github/workflows/package.yml` (optional, reusable, `workflow_call`) | called by `verify.yml` and `release.yml` | the 5-leg matrix once, with inputs `version` and `retention-days`. Removes the duplicated matrix; a reusable workflow in the same repository needs no pinning |

Script changes:

- `scripts/package-windows.bat`: zip the image as `BookLoom-<v>-windows-x86_64.zip`.
- `scripts/package-macos.sh`, `package-linux.sh`: one architecture word (`aarch64`, not `arm64`); a switch to skip the
  `.dmg` / `.deb` (or remove them, decision 4).
- `scripts/jpackage-common.sh` and the `.bat`: refuse, or handle on purpose, a version that starts with `0`.
- `scripts/launch-smoke.sh`: optional expected-version argument for the release.
- Optional: move `assets/icon/dist` to a packaging folder outside `docs/`; set the window icon in the app.
- Documents to change with it: `ci.yml` header and `lefthook.yml` comments (D8), `docs/development/quality-and-ci.md`,
  `docs/project/ci-and-hooks.md`, `docs/project/release-and-packaging.md`, `04_CI_CD.md`, `03_PACKAGING_JPACKAGE.md`
  (macOS steps), README (download and first-launch steps).

## Open decisions for the owner

1. **What "the gate" means after the split.** Today CI runs the pre-push command unchanged (D8), so a green push implies
   a green PR. With parallel jobs the PR runs the same tasks in three commands. *Default:* keep the pre-push hook as it
   is (one command, everything), restate D8 as "the PR jobs together run exactly the tasks of the gate command", and add
   a comment listing the mapping. Alternative: one job, same command, 30+ min — keeps D8 word for word, loses most of
   the speed.
2. **What a branch push compiles.** Only `assemble` (application code), or also the test sources. *Default:* also the
   test sources — one or two minutes more, and a test that no longer compiles is seen before the PR.
3. **A direct commit to `master`: build only, or the full verification.** Nothing else ever verifies such a commit.
   *Default:* build only, as stated in the requirement; say so in the docs.
4. **Release contents.** Only portable archives (5 files), or also the `.dmg` and `.deb` the scripts build today.
   *Default:* archives only — it matches "no installers"; the scripts keep the `.dmg`/`.deb` behind a switch for local
   use.
5. **Packaging legs on a PR.** All five, or one per OS (macOS aarch64, Linux x86_64, Windows). *Default:* all five,
   started together with the tests — free on a public repository, and the release then holds no surprise.
6. **Caching.** (a) setup-gradle "enhanced" (closed-source library, free for public repositories) or `basic` (MIT).
   (b) Gradle build cache on in CI or not. *Default:* setup-gradle with `basic` first; build cache on for compile and
   analysis only, every test task always run in the PR; revisit after measuring.
7. **Linux icon.** Leave the PNG inside the archive, or add the hicolor set and a `.desktop` sample. *Default:* PNG in
   the archive plus one README line; nothing more.
8. **Launch smoke in the release.** The requirement says the release runs no tests; the smoke is a 1–2 minute start of
   the built image and is the only proof the published file launches. *Default:* keep it on the four POSIX legs; Windows
   stays unsmoked as today.
9. **Accepted tag spellings.** Exactly `alphaN`, `betaN`, `RCN` (upper-case RC), or also `rc1`, `alpha.1`. *Default:*
   exactly the four forms given; anything else does not start the workflow.
10. **Merge strategy.** All three are allowed today. *Default:* allow squash only (repository setting) — one commit per
    PR on `master`, and the PR lookup in A3 is then the tested case.
11. **Protect `master` and the `v*` tags on the server.** Neither is protected now, so "never commit to master" is a
    convention. *Default:* a ruleset that requires the `verified` check for `master` and limits who may create `v*`
    tags; the path filter must then stay off the required workflow.
12. **First version number.** jpackage cannot carry `0.x` on macOS. *Default:* first release is `1.0.0-alpha1` or later;
    the release job refuses `v0.*`.
13. **Duplicate work while a PR is open.** Each push then starts the build-only run and the PR run. *Default:* accept it
    (3–5 minutes, free); skipping it needs one more API lookup.
