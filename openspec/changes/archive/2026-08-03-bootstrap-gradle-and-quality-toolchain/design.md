# Design — bootstrap-gradle-and-quality-toolchain

## Context

See `proposal.md` — Why. The state to design against is: an empty repository with a complete frozen specification, and
`docs/implementation_plan/01_MODULE_INVENTORY.md` already describing eight modules and their packages down to the class
level. Nothing here is being discovered; it is being assembled from decisions already taken in ADR-0001 (Java 25 +
JavaFX 25), ADR-0002 (Gradle Kotlin DSL), ADR-0014 (Lombok hybrid), and `04_Build_and_Release/01_BUILD_AND_TOOLING.md`.

A design document is warranted here despite that, on three of the four criteria: this change is **maximally
cross-cutting** (it touches every module simultaneously), it introduces **every build-time external dependency at
once**, and it establishes **two forward-compatibility seams** — F2 (the `Result`/`AppError` envelope's *home* in
`:api`; its contents arrive in change 2) and F9 (the offline invariant, seeded structurally by the
`no-http-in-core-except-llm` ArchUnit rule).

## Goals / Non-Goals

**Goals**

- `./gradlew clean build check spotlessCheck` is green across eight empty modules.
- Every rule that is cheap now and expensive later is active from the first line of production code: JPMS edges,
  ArchUnit boundaries, NullAway, records-first, formatting.
- A boundary violation, a null defect, an unformatted file, a banned license, or a drifted lockfile each fails the build
  **red**, and that is demonstrated with a fixture rather than assumed.

**Non-Goals**

- No runtime behaviour. `./gradlew :app:run` opening a window is change 2.
- No production logic in any module — each gets exactly one placeholder type so `module-info.java` compiles.
- No `build.yml` / `release.yml` workflows and no `jpackage` scripts (change 2 and change 27).
- No `Result`/`AppError` implementation. This change creates `:api` and the ArchUnit rule that will police it; change 2
  fills it in.

## Decisions

### D1 — `skip_specs: true` rather than synthesized requirements

**Chosen:** declare no capability and ship no delta spec.

`openspec/specs/` is the ledger of *what BookLoom does for its user* (ADR-0016). A Checkstyle rule, a Gradle task graph,
and a lockfile are how the product is built, not things it does. Writing "the system SHALL fail the build on unformatted
code" would be a requirement about the development process wearing a product-requirement costume, and would pollute the
one artifact whose value depends on meaning exactly one thing.

*Alternative considered:* invent a `build-tooling` capability. Rejected — it is not in the 16-capability map, that map
exists so the `FR-*` join key holds exactly, and no `FR-*` id covers build tooling. The verification that would live in
a spec lives in `tasks.md` instead, where each task carries its own check.

### D2 — All shared configuration in `build-logic` precompiled convention plugins

**Chosen:** four plugins, per `01_BUILD_AND_TOOLING.md#convention-plugins`:

| Plugin | Owns |
|---|---|
| `bookloom.java-conventions` | JDK 25 toolchain, JPMS compile args, Lombok (sole APT), `net.ltgt.errorprone` + NullAway, Checkstyle, SpotBugs + FindSecBugs |
| `bookloom.spotless-conventions` | Palantir Java Format, 120 columns |
| `bookloom.test-conventions` | JUnit 5 platform, AssertJ, Mockito 5, WireMock, TestFX/Monocle, JaCoCo, tag exclusions |
| `bookloom.javafx-conventions` | `org.openjfx.javafxplugin` — applied to `:ui` and `:app` **only** |

Each subproject's `build.gradle.kts` is then a few lines: apply conventions, declare dependencies. A copy-pasted
`allprojects { }` block in the root script is the alternative and is rejected by
`.claude/rules/gradle-build-and-quality.md`: it defeats configuration caching, cannot be unit-tested, and drifts.

`bookloom.javafx-conventions` being a separate plugin — rather than a conditional inside `java-conventions` — is what
makes "only `:ui`/`:app` see JavaFX" structural rather than a convention. A core module cannot accidentally acquire
JavaFX; it would have to apply a plugin it has no reason to apply.

### D3 — The compiler pipeline: Lombok is a processor, Error Prone is a plugin

Per `01_BUILD_AND_TOOLING.md#error-prone-lombok`, and worth restating because the naive framing is wrong: **Error Prone
is not an annotation processor**, so there is no processor-ordering problem to solve. The wiring is:

- `net.ltgt.errorprone` adds Error Prone as a **javac plugin** and NullAway as an Error Prone **check**, and sets
  `-XDcompilePolicy=simple` — which Error Prone requires so its plugin sees one flat compilation per file.
- **Lombok is the sole entry on `annotationProcessor`.** It desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder`
  before the Error Prone plugin walks the tree, so NullAway analyzes the *generated members*, not the annotations.
- NullAway is reconciled with Lombok by setting `lombok.addNullAnnotations` in `lombok.config`, so generated members
  carry correct nullability. The alternative — NullAway's generated-code exclusion — is the fallback if a specific
  Lombok emission still trips it.

Error Prone's SECURITY and CORRECTNESS categories fail the build (`02_QUALITY_GATES.md#tools`).

### D4 — `arch-test` as a shared source set, with rules that pass on empty modules

The eight ArchUnit rules live **once**, in a shared `arch-test` source set, not duplicated per module — a
`dependency-direction` rule needs to see the whole graph to be meaningful, and per-module copies would drift.

The subtlety: on an empty tree every rule passes **vacuously**, which proves nothing. So each rule ships with a
**violation fixture** — an FX import in `:document`, a reversed module edge, an HTTP client in `:pipeline`, SQL in
`:llm`, a Guice import in `:api`, a mutable class in an `..api..` package, a `static final Logger` in the bootstrap
package — and a test asserting the rule rejects it. Fixtures live in test sources so they never reach production
classes. Without them, a rule with a typo'd package scope would sit green forever.

`records-first` scopes to **carrier packages** only (`..dto`, `..api..`) per DD-05 / ADR-0014, because Lombok-bearing
service classes are deliberately not records.

`bootstrap-no-static-logger` scopes to the pre-logging startup path — `ua.bookloom.app` `Launcher`/lock acquisition and
`ua.bookloom.util.paths`. It has no subject yet; change 2 gives it one. It ships now so that change 2 cannot introduce
the violation in the first place (ADR-0015, DD-39: a static logger firing before Logback is configured pins it to the
wrong path).

### D5 — Local-only test tags are excluded by the *default task*, never by ad-hoc skips

`liveLocal`, `promptEval`, and `visual` are excluded via `useJUnitPlatform { excludeTags(...) }` on the default `Test`
task, with three separately registered tasks each `includeTags`-ing exactly one and **not** wired into `check`
(DD-35, `06_TESTING_STRATEGY.md#live-local`).

This is the only mechanism keeping local-only tests out of CI. `@Disabled`, environment-sniffing inside a test body, or
a `-PskipLive` property are all rejected: they are invisible in the task graph, so an accidentally-CI-running live test
would look like a normal green test until it hit a network timeout on a runner.

Each tagged task must also **skip cleanly rather than fail** when its env gate is absent (JUnit 5 assumption /
`@EnabledIfEnvironmentVariable`), so `./gradlew liveLocal` on a fresh checkout is green-with-skips.

### D6 — Coverage gate applies only to modules that have production code

JaCoCo branch coverage is exact **80% per module** on `:api :util :document :llm :pipeline :persistence`; `:ui` is
excluded and covered behaviourally by TestFX (`02_QUALITY_GATES.md#coverage-gate`,
`06_TESTING_STRATEGY.md#coverage-traceability`).

The wrinkle this change must handle: **all eight modules are empty right now.** A naive threshold makes
`./gradlew check` fail on day one with a division-by-zero-shaped complaint. So the gate is configured to skip modules
with no production sources — as an explicit exclusion, not a happy accident of how JaCoCo reports empty modules.

### D7 — Per-platform lock state for JavaFX

Gradle dependency locking is on and lockfiles are committed. But JavaFX artifacts are **classified per OS**
(`win`/`mac`/`mac-aarch64`/`linux`), so a single committed lockfile is **not** reproducible across platforms
(`01_BUILD_AND_TOOLING.md#dependency-locking`).

**Chosen:** exclude the JavaFX variant-classified configurations from locking and rely on the exact catalog pin for
those artifacts. The alternative — one lockfile per OS, each written on that OS — is also spec-permitted but requires
three machines to regenerate a lock and would block a contributor on a platform nobody has handy. The
single-lock-reproducibility claim is therefore asserted only over platform-neutral configurations, and the CI job must
not claim otherwise.

### D8 — Hooks invoke the same Gradle entry points as CI

`lefthook.yml` pre-push runs `./gradlew clean build check spotlessCheck` — the identical command CI runs — so a green
push implies a green CI quality job for the same tree. No hook-only variant, no weaker subset. Pre-commit stays under
~10s (Spotless on staged files with re-staging, gitleaks, file-size guard) and runs no tests.

Pre-commit operating on **staged files only, re-staging Spotless output**, matters for partial stages: formatting churn
from unrelated files must not silently join the commit.

## Risks / Trade-offs

- **Eight empty modules with placeholder types may drift from the inventory.** → Each placeholder is named from
  `01_MODULE_INVENTORY.md`, not invented, and the final task group re-checks the inventory.
- **ArchUnit rules written against packages that do not exist yet can be silently mis-scoped.** → D4's violation
  fixtures are the mitigation; a mis-scoped rule fails its own fixture test.
- **The coverage gate on empty modules is the most likely day-one build breakage.** → D6 makes the exclusion explicit
  and the task list verifies it with a module that has production code below threshold.
- **The license gate can red-light a transitive dependency of a build plugin.** → Run it early in the task order, not
  as the last step, so an unacceptable transitive is discovered while there is still room to swap the tool.
- **JavaFX per-OS classifiers interact badly with locking.** → D7; and the CI job runs on `ubuntu-24.04` only for this
  change, so the multi-platform lock question does not gate the merge.
- **`liveLocal`/`promptEval`/`visual` tasks have no tests yet**, so "it correctly runs only its tag" is unprovable on
  content. → Prove it with a canary test per tag, deleted or kept as a permanent marker test.

## Migration Plan

Not applicable — there is nothing to migrate from. Rollback is `git revert`; no persisted state, no released artifact,
no consumer exists.

## Open Questions

None. Every decision above is settled by an existing ADR or by
`04_Build_and_Release/01_BUILD_AND_TOOLING.md` / `02_QUALITY_GATES.md`; D1 is settled by ADR-0016 and D7 chooses between
two options the spec explicitly permits.
