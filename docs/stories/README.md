# Stories

Implementation work is tracked here as **stories** — one Markdown file per story, **one story per coding session**.
Stories are the mutable working unit; the specification under `../specification/` is frozen and read-only during
implementation.

- **Format:** every story follows `../implementation_plan/02_STORY_FORMAT.md` (front-matter + fixed body order). Start
  from `STORY_TEMPLATE.md`.
- **Naming:** `story-NNN-<short-slug>.md`. `STORY-NNN` ids are globally unique, monotonic, and permanent — never reuse
  or renumber.
- **Creation:** stories are planned by the `/plan-phase-stories-creation <PHASE>` command and written by the `architect`
  agent after the plan is approved.
- **Implementation:** each story is planned by `/plan-user-story-implementation <STORY>` and executed by the `coder` +
  `tester` agents after approval.
- **Lifecycle:** `draft → ready → in-progress → done → superseded`. A `done` story is immutable; a later change spawns a
  **new** story (and a new ADR if architecturally significant).
- **Traceability:** every acceptance criterion needs a proving test that names it on its first line
  (`// Proves: STORY-NNN-AC-N`). Run `./gradlew trace` then `./gradlew traceCheck` before marking a story `done`.

## Index

| Story                                                                   | Title                                                     | Phase             | Status |
|-------------------------------------------------------------------------|-----------------------------------------------------------|-------------------|--------|
| [STORY-001](story-001-scaffold-gradle-multimodule-jpms-skeleton.md)     | Scaffold Gradle multi-module + JPMS skeleton              | PHASE_00_SCAFFOLD | draft  |
| [STORY-002](story-002-result-apperror-errorcode-envelope.md)            | `Result` / `AppError` / `ErrorCode` envelope in `:api`    | PHASE_00_SCAFFOLD | draft  |
| [STORY-003](story-003-app-paths-resolver-dev-prod.md)                   | App-paths resolver (per-OS, dev/prod)                     | PHASE_00_SCAFFOLD | draft  |
| [STORY-004](story-004-quality-toolchain-format-lint-static-analysis.md) | Quality toolchain — format, lint, static analysis         | PHASE_00_SCAFFOLD | draft  |
| [STORY-005](story-005-archunit-boundary-suite.md)                       | ArchUnit boundary suite                                   | PHASE_00_SCAFFOLD | draft  |
| [STORY-006](story-006-logging-bootstrap-programmatic-logback.md)        | Logging bootstrap — programmatic Logback                  | PHASE_00_SCAFFOLD | draft  |
| [STORY-007](story-007-version-injection-resource.md)                    | Version injection — `-PappVersion` → `version.properties` | PHASE_00_SCAFFOLD | draft  |
| [STORY-008](story-008-launcher-composition-root-lifecycle.md)           | Launcher, composition root, lifecycle                     | PHASE_00_SCAFFOLD | draft  |
| [STORY-009](story-009-test-conventions-tags-coverage-gate.md)           | Test conventions, tags, coverage gate                     | PHASE_00_SCAFFOLD | draft  |
| [STORY-010](story-010-trace-and-tracecheck-tooling.md)                  | `trace` / `traceCheck` traceability tooling               | PHASE_00_SCAFFOLD | draft  |
| [STORY-011](story-011-lefthook-git-hooks.md)                            | Lefthook git hooks                                        | PHASE_00_SCAFFOLD | draft  |
| [STORY-012](story-012-ci-quality-workflow-license-sca.md)               | CI quality workflow — license + SCA gates                 | PHASE_00_SCAFFOLD | draft  |
| [STORY-013](story-013-packaging-scripts-collectdist-launch-smoke.md)    | Packaging scripts, `collectDist`, launch smoke            | PHASE_00_SCAFFOLD | draft  |
