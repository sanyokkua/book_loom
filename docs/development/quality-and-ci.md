# Quality gate, coverage and CI

*What `check` enforces, coverage, the opt-in SonarQube scan, git hooks and the CI workflow.*

## Coverage {#coverage}

```bash
./gradlew :document:jacocoTestReport               # HTML + XML report for one module
./gradlew :document:jacocoTestCoverageVerification # the gate for one module (also run by check)
```

Reports land at `modules/<module>/build/reports/jacoco/test/html/index.html` (and `jacocoTestReport.xml` beside it).
The gate is **branch coverage ≥ 0.80 per module**, applied to `:api`, `:util`, `:document`, `:llm`, `:pipeline`,
`:persistence`; `:ui` and `:app` are covered behaviourally and exempt. A module whose only sources are
`module-info.java`/`package-info.java` skips the check; a module with sources and no tests fails at 0%.

### Local SonarQube analysis

With the local SonarQube server running and a token supplied in the environment, run from the repository root:

```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN='your-token'
./gradlew sonar -PsonarLocal
```

The Gradle scanner compiles the eight application modules, runs their normal offline tests, and imports JaCoCo XML
coverage. Coverage includes execution across modules and also reports `:ui` and `:app`, without adding a branch
threshold for them. Local-only model, corpus, visual, and soak tests are not part of this run. The included
`modules/build-logic` build is outside the analysis. The server's installed analyzers and quality profile determine
which issues are reported.

The project key is `local:bookloom`. To export findings and the quality-gate result using the separately installed
`sonar-local` helper:

```bash
SONAR_PROJECT_KEY=local:bookloom sonar-local report
```

Keep the token out of repository files. Run the Gradle scanner rather than `sonar-local scan`, which uses the generic
CLI scanner. Sonar analysis is opt-in: `build`, `check`, hooks, and CI do not run it or require the local server.

The repository is held at 0 open Sonar issues with the quality gate OK. To keep it there, run
`./gradlew sonar -PsonarLocal` at the end of each change group (after the whole gate is green) and fix what it reports
before the group is called done; the scan is not part of the gate only because it needs the local server.

---

## Quality gate, hooks and CI {#quality-gate}

`./gradlew check` runs Spotless (`spotlessCheck`), Checkstyle (`config/checkstyle/`, zero warnings allowed), Error
Prone + NullAway (as javac plugins; every package is `@NullMarked`), SpotBugs + FindSecBugs (`config/spotbugs/`),
ArchUnit (`:app:archTest`), the tests, and the JaCoCo verification. `./gradlew spotlessApply` fixes formatting;
`spotlessCheck` only reports it.

The fit tests measure text with the platform's font, so they can pass on macOS and fail on the Ubuntu runner. Run them
there before pushing a UI change (needs a Docker daemon, e.g. `colima start --cpu 2 --memory 4`; the first run builds a
small image and compiles, later runs reuse both):

```bash
scripts/ci-linux-ui.sh     # the fit tests on Ubuntu; add --tests '*SomeTest' for another :ui selection
```

CI-only, because it needs the network: the licence gate.

```bash
./gradlew checkLicense     # every runtime dependency's licence must be in config/license/allowed-licenses.json
```

**Git hooks** (Lefthook; inactive until installed once per clone):

```bash
brew install lefthook gitleaks      # or scoop install … on Windows
lefthook install                    # writes .git/hooks
lefthook validate                   # optional: does lefthook.yml parse
```

| Stage | Runs |
|---|---|
| `pre-commit` | `spotlessApply` on staged Java (re-stages), `gitleaks` on the staged diff, a 4 MB file-size guard (`BOOKLOOM_MAX_FILE_SIZE_KB` overrides) |
| `commit-msg` | Conventional Commits check |
| `pre-push` | `.lefthook/pre-push/quality-gate.sh` = the full gate, and `agent-config-sync.sh` = `scripts/sync-agent-files.py --check` |

Pre-push runs **exactly** the CI quality command, so a green push implies a green CI quality job. Escape hatches
(`git push --no-verify`, `LEFTHOOK=0`) exist and are a decision, not a habit; agents may not use them.

**OpenSpec agent files** are the `openspec-*` skills in `.agents/skills/` and the `/opsx:*` commands in
`.claude/commands/opsx/`. The OpenSpec CLI generates them; never edit them by hand. Claude and Codex share one copy of
each skill: Codex reads `.agents/skills/` directly, and Claude reads it through the `.claude/skills` symlink. That copy
is the Codex rendering, whose references work in both tools.

```bash
OPENSPEC_TELEMETRY=0 openspec config profile core           # once per machine: propose, explore, apply, update, sync, archive
OPENSPEC_TELEMETRY=0 openspec update                        # after upgrading the CLI: npm install -g @fission-ai/openspec@latest
OPENSPEC_TELEMETRY=0 openspec init --tools claude,codex     # first set-up; codex comes last so its rendering is the one kept
```

**CI** (`.github/workflows/ci.yml`, on pull requests to `master` and on pushes to every other branch): a *quality* job
(Xvfb, strict lock verification, the gate `./gradlew clean build check spotlessCheck`, the agent-file sync check, the
licence gate; it takes 15–27 minutes on the runner, with a 45-minute ceiling) and, after it, a five-leg *packaging*
matrix (macOS x86_64 and aarch64, Linux x86_64 and aarch64, Windows) that stages `:app:collectDist`, builds each image
and, on POSIX, launch-smokes it (see [Packaging](getting-started.md#packaging)). Test reports are uploaded only on
failure. The packaging legs are written from the spec and have not yet been observed running in CI; the scripts
themselves were verified locally.

---
