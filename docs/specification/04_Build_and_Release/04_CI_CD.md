**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, release management **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
`docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`

# CI/CD

Continuous integration runs on GitHub Actions as **three workflows with three distinct triggers** — the proven shape
from the reference desktop projects, adapted to this project's Gradle toolchain:

| Workflow      | Trigger                                                    | Purpose                                                                 |
|---------------|------------------------------------------------------------|-------------------------------------------------------------------------|
| `ci.yml`      | pull requests to `main`; pushes to non-`main` branches     | The **merge gate**: the full quality job, once, on Linux                |
| `build.yml`   | pushes to `main` (tags excluded — `release.yml` owns tags) | Quality job + the **snapshot packaging matrix**; artifacts kept 14 days |
| `release.yml` | tags `vX.Y.Z` / `vX.Y.Z-*`                                 | Verify tag → quality job → packaging matrix → **GitHub Release**        |

The pipeline uses **no signing or publish secrets** (`#no-secrets`).

## quality-job {#quality-job}

The same quality job anchors all three workflows: a single Linux runner, Temurin JDK 25, Gradle cache restored (keyed on
the wrapper + lockfiles + version catalog):

1. Checkout; set up JDK (`actions/setup-java`, `distribution: temurin`, Gradle cache).
2. **Start Xvfb** (`Xvfb :99 &`, `DISPLAY=:99`) — the JavaFX/TestFX suites run against the virtual display; Monocle
   headless remains the in-JVM fallback where a display is not needed.
3. `./gradlew spotlessCheck` — formatting.
4. `./gradlew check` — Error Prone + NullAway, Checkstyle, SpotBugs + FindSecBugs, all ArchUnit tests, unit tests, and
   the headless UI suites (TestFX; `liveLocal`/`promptEval`/`visual` tags excluded).
5. JaCoCo coverage threshold — exact 80% branch, per-module, production-code modules only
   (`02_QUALITY_GATES.md#coverage-gate`).
6. **License gate** — `./gradlew checkLicense` (`com.github.jk1.dependency-license-report` against the allowed-license
   policy, `05_Dependencies/03_LICENSING.md#license-gate-tool`), distinct from **OWASP dependency-check** (SCA). Either
   failing fails the job.
7. `./gradlew traceCheck` — traceability: zero orphans, fresh record.

Any failing step fails the job (`02_QUALITY_GATES.md#what-fails-where`). In `ci.yml` this job **is** the whole
workflow — packaging does not run on PRs, keeping the merge gate fast; packaging breakage is caught on the next `main`
push by `build.yml`.

## packaging-matrix {#packaging-matrix}

`build.yml` and `release.yml` share the same five-leg packaging matrix (jpackage cannot cross-compile —
`03_PACKAGING_JPACKAGE.md#no-cross-compile`). Every leg `needs:` the quality job, uses `fail-fast: false`, runs
`./gradlew :app:collectDist` and then the committed per-OS script (`03_PACKAGING_JPACKAGE.md#approach`), runs the
**headless launch smoke** on its artifact (`02_QUALITY_GATES.md#jpackage-smoke`), and uploads:

| Job / leg                  | Runner             | JDK (`setup-java`)       | Script                                   | Uploads                                        |
|----------------------------|--------------------|--------------------------|------------------------------------------|------------------------------------------------|
| `package-macos` (x86_64)   | `macos-15-intel`   | Temurin 25               | `package-macos.sh`                       | `.dmg`, `.app` **tar.gz**                      |
| `package-macos` (aarch64)  | `macos-15`         | Temurin 25               | `package-macos.sh`                       | `.dmg`, `.app` tar.gz                          |
| `package-windows` (x86_64) | `windows-latest`   | **Liberica 25 `jdk+fx`** | `package-windows.bat`                    | portable **zip** (app-image; **no installer**) |
| `package-linux` (x86_64)   | `ubuntu-latest`    | Temurin 25               | `package-linux.sh` (installs `fakeroot`) | tar.gz, `.deb`                                 |
| `package-linux` (aarch64)  | `ubuntu-24.04-arm` | **Liberica 25 `jdk+fx`** | `package-linux.sh` (installs `fakeroot`) | tar.gz, `.deb`                                 |

The Liberica "Full" legs are where jpackage needs the JavaFX **jmods**
(`03_PACKAGING_JPACKAGE.md#jdk-and-javafx-runtime`). Artifact names carry version + OS + arch
(`BookLoom-<v>-<os>-<arch>.<ext>`); the macOS `.app` is always **tarred** (never zipped) to preserve the execute bit
(EC-REL-6). In `build.yml` the artifacts are snapshots — the version resource stays **`dev`** (nothing is passed) and
jpackage receives a placeholder numeric (e.g. `0.0.0`), **14-day retention** — a rolling proof that packaging works on
every `main` commit; in `release.yml` they are staging for the release job (**1-day retention** — the GitHub Release is
the durable record).

## release-on-tags {#release-on-tags}

Pushing a semver tag (`vX.Y.Z`, optionally `-suffix`) triggers `release.yml`:

1. **`verify` job** — checks out with full history and asserts the tagged commit **is an ancestor of `main`** (a tag on
   a side branch fails the release, EC-REL-1); extracts two version strings as job outputs: `full` (tag minus `v`, e.g.
   `1.2.0-rc1`) and `jpackage` (numeric `X.Y.Z` only — jpackage rejects suffixes, EC-REL-2).
2. **Quality job** — the full gate from `#quality-job`, at the tagged commit, run with **`-PappVersion=<full>`** so the
   generated version resource (`01_BUILD_AND_TOOLING.md#version-injection`, DD-50) carries the tag version through
   tests.
3. **Packaging matrix** — all five legs from `#packaging-matrix`: Gradle steps run with **`-PappVersion=<full>`**
   (About/resource + artifact names) and the scripts receive **`APP_VERSION=<numeric>`** for `jpackage --app-version`
   (OS metadata). The per-leg launch smoke asserts the started app logs the tag version (EC-REL-7). Artifacts uploaded
   with 1-day retention.
4. **`create-release` job** — `needs` everything; downloads all `release-*` artifacts, collects `.dmg`/`.zip`/`.deb`/
   `.tar.gz`, and publishes a **GitHub Release** via a release action with: the tag name, **`prerelease:` auto-detected
   from a `-` suffix** in the tag, **generated release notes**, plus the unsigned-app "open anyway" steps
   (`03_PACKAGING_JPACKAGE.md#unsigned-distribution`). Requires only `permissions: contents: write` with the built-in
   `GITHUB_TOKEN`.

A red quality job or any red packaging leg blocks `create-release` — a tag with a failing gate publishes **nothing**.

## no-secrets {#no-secrets}

The pipeline uses **no signing or publish secrets** — no certificates, no notarization credentials, no tokens beyond the
built-in `GITHUB_TOKEN` (used only by `create-release`). The **only** optional repository secret is an `NVD_API_KEY`,
used solely by OWASP dependency-check to fetch the vulnerability feed at a higher rate limit; it grants no publish or
signing capability and is not required (CI can run against a cached/mirrored feed, keeping forks buildable without it).
No step contacts a third-party service with credentials, and the app ships with no embedded keys
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`).

## caching-and-reproducibility {#caching-reproducibility}

Gradle dependency locking (`01_BUILD_AND_TOOLING.md#dependency-locking`) plus the committed wrapper make CI resolution
reproducible; the Gradle cache is keyed on the lockfiles/catalog so a green build is deterministic across runners.
Because packaging is plain-CLI scripts over a Gradle-produced jar directory, **any contributor can reproduce their own
OS's artifact locally** with `./gradlew :app:collectDist && scripts/package-<os>` — CI adds nothing platform-magical.
