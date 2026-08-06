# Tasks — bootstrap-gradle-and-quality-toolchain

Absorbs the former STORY-001 (Gradle multi-module + JPMS), STORY-004 (Spotless / Error Prone+NullAway / Checkstyle /
SpotBugs+FindSecBugs), STORY-005 (ArchUnit boundary suite), STORY-009 (test conventions, tags, coverage gate),
STORY-011 (Lefthook) and STORY-012 (CI + license report + SCA). STORY-010 (the `trace`/`traceCheck` tooling) is
**dropped, not converted** — ADR-0016 retires it.

## 1. Gradle skeleton and the eight JPMS modules

- [x] 1.1 Commit the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` + `.properties`)
      pinned to one Gradle version with its distribution SHA-256. Every build in this project — local, hook, CI — must
      go through the wrapper, so a contributor's system `gradle` can never produce a different result from CI.
      → repo root · `04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-system`, DD-03
- [x] 1.2 Write `settings.gradle.kts` including the eight subprojects `:api :util :document :llm :pipeline
      :persistence :ui :app` plus `includeBuild("build-logic")`. This fixes the module set for the whole project; every
      later change adds packages inside these eight, never a ninth subproject without an ADR.
      → repo root · `01_BUILD_AND_TOOLING.md#project-layout`, `02_Architecture/02_MODULES_AND_LAYERING.md`
- [x] 1.3 Create `gradle/libs.versions.toml` with `[versions]`, `[libraries]`, `[bundles]`, `[plugins]` sections holding
      every dependency coordinate this change introduces. No build script may ever contain an inline
      `group:artifact:version` string — one catalog is the single source of truth, so a version bump is one edit and is
      reviewable in isolation. → repo root · `01_BUILD_AND_TOOLING.md#version-catalog`, DD-03
- [x] 1.4 Write one `module-info.java` per subproject declaring the module name `ua.bookloom.<module>` and **only** the
      dependency edges the layering table allows (`:app` → all; `:ui` → `:pipeline :api :util` + `javafx.*`;
      `:pipeline` → `:document :llm :persistence :api :util`; `:document`/`:llm`/`:persistence` → `:api :util`;
      `:util` → `:api`; `:api` → nothing internal). A forbidden import then fails at **compile time**, before ArchUnit
      even runs — the module graph is enforced by the language, not by convention.
      → all eight modules · `02_MODULES_AND_LAYERING.md#dependency-direction`, DD-06
- [x] 1.5 Add `requires com.google.guice;` and `opens <impl package> to com.google.guice;` to each service module's
      `module-info.java` now, even though no Guice module exists yet. Wiring it here means change 2 (the composition
      root) needs zero `module-info` churn, and reflective injection into a JPMS module fails at runtime rather than
      compile time if the `opens` is missing — a failure mode best avoided by never having it.
      → `:document :llm :pipeline :persistence :ui :app` · `02_MODULES_AND_LAYERING.md`,
      `02_Architecture/10_DI_AND_LIFECYCLE.md`
- [x] 1.6 Add exactly one placeholder public type per module, named from the module inventory rather than invented, so
      each `module-info.java` has something to export and the module compiles. Keep them minimal — they are scaffolding
      that later changes replace, not API. → all eight modules · `docs/implementation_plan/01_MODULE_INVENTORY.md`
- [x] 1.7 Add `.editorconfig` and `.gitattributes` fixing LF line endings, UTF-8, and final-newline for all text files.
      Without these, a Windows checkout produces CRLF that Spotless rewrites on every commit, generating churn that
      makes real diffs unreadable. → repo root · `01_BUILD_AND_TOOLING.md`,
      `.claude/rules/gradle-build-and-quality.md`
- [x] 1.8 Verify `./gradlew build` compiles all eight modules green from a clean checkout, and write a functional test
      asserting it. This is the floor everything else stands on; if it is not reproducible from a clean clone, nothing
      downstream is. → `build-logic` test · `01_BUILD_AND_TOOLING.md#build-system`

## 2. Convention plugins in `build-logic`

- [x] 2.1 Create the `build-logic` included build with the `kotlin-dsl` plugin and a `src/main/kotlin` source root for
      precompiled script plugins. All shared configuration lives here rather than in an `allprojects { }` block,
      because a convention plugin is configuration-cache friendly and can itself be unit-tested — an `allprojects`
      block is neither. → `build-logic` · `01_BUILD_AND_TOOLING.md#convention-plugins`, DD-03
- [x] 2.2 Write `bookloom.java-conventions.gradle.kts` setting `java.toolchain.languageVersion = 25` and the JPMS
      compile arguments. Pinning the toolchain means a contributor with JDK 21 installed still builds against 25 —
      Gradle downloads it — so "works on my machine" cannot diverge on language level.
      → `build-logic` · `01_BUILD_AND_TOOLING.md#build-system`, ADR-0001
- [x] 2.3 Write `bookloom.spotless-conventions.gradle.kts` applying Spotless with Palantir Java Format at 120 columns,
      exposing `spotlessApply` and `spotlessCheck`. Formatting must be mechanical and never argued about in review;
      `spotlessApply` in the pre-commit hook means it is fixed before it is ever seen.
      → `build-logic` · `04_Build_and_Release/02_QUALITY_GATES.md#tools`, DD-25
- [x] 2.4 Wire Lombok as the **sole** entry on `annotationProcessor` in `bookloom.java-conventions`, and add a root
      `lombok.config` with `lombok.addNullAnnotations` set so generated members carry nullability annotations. Lombok
      desugars before the Error Prone javac plugin walks the tree, so NullAway analyzes the generated constructor, not
      the annotation — without the config it raises false non-null violations on every `@RequiredArgsConstructor`
      service. → `build-logic`, repo root · `01_BUILD_AND_TOOLING.md#error-prone-lombok`, DD-05, ADR-0014
- [x] 2.5 Add the `net.ltgt.errorprone` plugin with NullAway as an Error Prone check and `-XDcompilePolicy=simple`, and
      configure Error Prone's SECURITY and CORRECTNESS categories to **fail** the build. Error Prone is a javac plugin,
      not an annotation processor, and it requires the simple compile policy to see one flat compilation per file — the
      flag is not optional. → `build-logic` · `01_BUILD_AND_TOOLING.md#error-prone-lombok`,
      `02_QUALITY_GATES.md#null-safety`
- [x] 2.6 Seed a `@NullMarked` (JSpecify) `package-info.java` in every package created by this change, and make it the
      documented convention for new packages. NullAway only checks packages that opt in, so an unmarked package is
      silently unanalyzed — the marker is what turns the tool on.
      → all eight modules · `02_QUALITY_GATES.md#null-safety`, `.claude/rules/java-coding-style.md`
- [x] 2.7 Add Checkstyle with the project ruleset under `config/checkstyle/` and bind it into `check`. Checkstyle covers
      the naming and structural conventions Spotless does not (Spotless formats; it does not judge).
      → `build-logic`, `config/checkstyle/` · `02_QUALITY_GATES.md#tools`
- [x] 2.8 Add SpotBugs with the FindSecBugs plugin, bound into `check`, failing on high-priority findings. FindSecBugs
      is the automated backstop for the credentials-as-reference invariant — it flags a hard-coded secret or an unsafe
      credential path that review can miss. → `build-logic`, `config/spotbugs/` · `02_QUALITY_GATES.md#tools`, DD-11
- [x] 2.9 Write `bookloom.javafx-conventions.gradle.kts` applying `org.openjfx.javafxplugin` with the JavaFX 25
      `controls`, `fxml`, and `graphics` modules, and apply it to **`:ui` and `:app` only**. Keeping JavaFX in a
      separate plugin makes the FX-free core structural: a core module would have to deliberately apply a plugin it has
      no reason to apply. → `build-logic`, `:ui`, `:app` · `01_BUILD_AND_TOOLING.md#javafx-plugin`, DD-06
- [x] 2.10 Write a functional test that an unformatted source file fails `spotlessCheck` and is fixed by
      `spotlessApply`; a test that a NullAway-detectable null defect in a `@NullMarked` package fails `check` while a
      Lombok `@RequiredArgsConstructor` service in the same package compiles clean; and tests that seeded Checkstyle and
      FindSecBugs canary violations each fail `check`. A quality tool that is configured but not proven to fail red is
      indistinguishable from one that is switched off. → `build-logic` test · `02_QUALITY_GATES.md#tools`

## 3. Dependency locking and the license gate

- [x] 3.1 Enable Gradle dependency locking across the platform-neutral configurations and commit the lockfiles. A
      locked graph means an upstream republishing a version cannot silently change a build, and a version bump becomes a
      reviewable diff instead of an invisible resolution change.
      → repo root, all modules · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [x] 3.2 Exclude the JavaFX variant-classified configurations from locking and rely on the exact catalog pin for those
      artifacts, per design decision D7. JavaFX resolves a different per-OS classified artifact on each platform, so a
      single committed lockfile is genuinely not reproducible across platforms — asserting otherwise would be a false
      claim baked into the build. → repo root, `:ui`, `:app` · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [x] 3.3 Add the `com.github.jk1.dependency-license-report` plugin with an allowed-license policy file permitting
      Apache-2.0, MIT, and the BSD family plus the three recorded exceptions — EPL-1.0 (Logback), the ICU License
      (ICU4J), and the JDOM License — and banning GPL/LGPL/AGPL/SSPL. Wire it as `checkLicense` and generate
      `THIRD-PARTY-NOTICES`. This ships as an MIT-licensed distributable; one copyleft transitive would make that claim
      false. → repo root, `config/license/` · `05_Dependencies/03_LICENSING.md#license-gate-tool`
- [x] 3.4 Run `checkLicense` against the full dependency graph **now, before the CI wiring lands**, and resolve any
      non-allowlisted transitive by swapping the tool rather than widening the allowlist. Discovering an unacceptable
      license after CI is green means either a rushed exception or ripping out a tool everything already depends on.
      → repo root · `05_Dependencies/03_LICENSING.md`
- [x] 3.5 Write a functional test that resolving with a version absent from the lock state fails the build, and one
      that a coordinate carrying a banned license (a test-only GPL canary) fails `checkLicense` naming the offending
      coordinate. → `build-logic` test · `01_BUILD_AND_TOOLING.md#dependency-locking`,
      `05_Dependencies/03_LICENSING.md`

## 4. The ArchUnit boundary suite

- [x] 4.1 Create the shared `arch-test` source set and wire it into `check`. The rules live once and see the whole
      module graph — `dependency-direction` is meaningless from inside a single module, and per-module copies would
      drift apart. → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [x] 4.2 Implement `fx-free-core`: no class in `:api :util :document :llm :pipeline :persistence` may depend on
      `javafx..`. The core must run headless in JUnit and WireMock; a JavaFX dependency anywhere in it would make the
      business logic untestable without a toolkit. → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`, DD-06
- [x] 4.3 Implement `dependency-direction` as a layered check encoding exactly the allowed edges, accepting every
      permitted edge and rejecting every omitted one across the full eight-module graph. This is the rule that keeps the
      graph an acyclic DAG pointing at `:api`. → `arch-test` · `02_MODULES_AND_LAYERING.md#dependency-direction`
- [x] 4.4 Implement `ports-not-concretes`: a caller in another module must depend on the `:api` interface, never on a
      concrete `..Impl`/`..Dao`/`..Service`. Ports are what make every collaborator mockable at the `:api` seam.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [x] 4.5 Implement `no-http-in-core-except-llm` (only `:llm` may import `java.net.http..`) and
      `no-sql-in-core-except-persistence` (only `:persistence` may import `java.sql..`, `org.jdbi..`, `org.flywaydb..`).
      The HTTP rule is the **structural seed of the offline invariant (F9)**: it makes "only one module can open a
      socket" a compile-gate fact rather than a promise.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`, DD-01
- [x] 4.6 Implement `api-is-framework-free`: `:api` may not import Guice, Jackson, JavaFX, JDBI, or any parser library.
      `:api` is the dependency floor every other module points at — a framework there propagates to all eight.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [x] 4.7 Implement `records-first` scoped to **carrier packages only** (`..dto`, `..api..`): data carriers must be
      records, never Lombok `@Data`/`@Value`. Service classes are deliberately out of scope — Lombok
      `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on services is the accepted hybrid.
      → `arch-test` · DD-05, ADR-0014
- [x] 4.8 Implement `bootstrap-no-static-logger`: no class on the pre-logging startup path — `ua.bookloom.app`
      `Launcher` / lock acquisition and `ua.bookloom.util.paths` — may declare a static SLF4J `Logger` or touch
      `org.slf4j..` at class-init time. It has no subject until change 2, and that is the point: shipping it now means
      change 2 cannot introduce the violation, where a static logger firing before Logback is configured would pin
      logging to the wrong directory. → `arch-test` · `02_Architecture/10_DI_AND_LIFECYCLE.md#logging-bootstrap-order`,
      DD-39, ADR-0015
- [x] 4.9 Add a **violation fixture per rule** in test sources — an FX import in `:document`, a reversed module edge, an
      HTTP client in `:pipeline`, SQL in `:llm`, a Guice import in `:api`, a mutable class in an `..api..` package, a
      `static final Logger` in the bootstrap package — with a test asserting each rule rejects its fixture. On an empty
      tree every rule passes vacuously, so without fixtures a rule with a typo'd package scope sits green forever.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [x] 4.10 Confirm all eight rules execute under `./gradlew check` and are green on the scaffold, asserted by a test
      that counts them rather than by inspection — a rule silently dropped from the suite is invisible otherwise.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`

## 5. Test conventions, tag sets, and the coverage gate

- [x] 5.1 Write `bookloom.test-conventions.gradle.kts` providing JUnit 5 + AssertJ + Mockito 5 + WireMock +
      TestFX/Monocle to every module, with the Monocle headless system properties set. Fixing the stack once means no
      change ever re-argues which assertion library to use, and UI tests never need a display server.
      → `build-logic` · `04_Build_and_Release/06_TESTING_STRATEGY.md#test-types`
- [x] 5.2 Configure the default `Test` task with `useJUnitPlatform { excludeTags("liveLocal", "promptEval", "visual") }`
      and register three separate tasks (`liveLocal`, `promptEval`, `visual`), each `includeTags`-ing exactly its own
      tag and **not** wired into `check`. The task graph is the only exclusion mechanism — an `@Disabled` or an
      in-test environment sniff is invisible in the graph, so a live test accidentally running in CI would look like a
      normal green test until it timed out on a runner.
      → `build-logic` · `06_TESTING_STRATEGY.md#live-local`, DD-35
- [x] 5.3 Make each tagged task **skip cleanly rather than fail** when its environment gate is absent, via a JUnit 5
      assumption or `@EnabledIfEnvironmentVariable`. A fresh checkout with no local Ollama must report
      `./gradlew liveLocal` as green-with-skips, not red. → `build-logic` · `06_TESTING_STRATEGY.md#live-local`
- [x] 5.4 Configure the JaCoCo branch-coverage gate at exactly 80% per module on `:api :util :document :llm :pipeline
      :persistence`, excluding `:ui` (covered behaviourally by TestFX) and excluding modules with **no production
      sources**. All eight modules are empty right now, so without the empty-module exclusion `./gradlew check` fails on
      day one — and the exclusion must be explicit configuration, not a happy accident of how JaCoCo reports zero
      classes. → `build-logic` · `02_QUALITY_GATES.md#coverage-gate`, `06_TESTING_STRATEGY.md#coverage-traceability`
- [x] 5.5 Add a canary test per excluded tag and a TestFX + Monocle canary UI test, then write functional tests
      asserting that `./gradlew test` and `./gradlew check` run zero tagged tests while each tagged task runs exactly
      its own, that a module with production code below 80% branch coverage fails `check` while an empty module is
      exempt, and that the Monocle canary renders headlessly. → `build-logic` test, `:ui` test ·
      `02_QUALITY_GATES.md#coverage-gate`, `06_TESTING_STRATEGY.md#test-types`

## 6. Lefthook git hooks

- [x] 6.1 Write `lefthook.yml` with a `pre-commit` stage under ~10 seconds: Spotless apply on **staged Java files only**
      with the formatted output re-staged, gitleaks, and a file-size guard. Staged-only matters for partial commits —
      formatting churn from unrelated files must not silently join the commit.
      → repo root · `02_QUALITY_GATES.md#lefthook-stages`, DD-25
- [x] 6.2 Add the `commit-msg` stage enforcing Conventional Commits, so the history stays machine-readable for the
      release tooling that arrives in change 27. → repo root · `02_QUALITY_GATES.md#lefthook-stages`
- [x] 6.3 Add the `pre-push` stage running exactly `./gradlew clean build check spotlessCheck` — the **identical**
      command CI runs, with no hook-only weaker variant — so a green push implies a green CI quality job for the same
      tree. → repo root · `02_QUALITY_GATES.md#lefthook-stages`, `#what-fails-where`
- [x] 6.4 Document the `lefthook install` setup step in the README and note the `git push --no-verify` escape hatch in
      the config comments, so hooks are active after a fresh clone without hidden ceremony and the bypass is a known
      decision rather than a rediscovered trick. → repo root, `README.md` · `02_QUALITY_GATES.md#lefthook-stages`
- [x] 6.5 Add scripted hook checks (not part of the Gradle `check` graph, since they need a git repo with hooks
      installed): one committing a mis-formatted staged file and asserting the committed content is formatted, and one
      introducing a Checkstyle violation and asserting `git push` fails before any ref transfers.
      → `tooling/hooks-test/` · `02_QUALITY_GATES.md#lefthook-stages`

## 7. CI quality workflow

- [x] 7.1 Write `.github/workflows/ci.yml` triggered on pull requests targeting `master`, running on `ubuntu-24.04`
      with Temurin 25 and the Gradle cache, with a concurrency group that cancels superseded runs of the same PR and a
      timeout guard on every job. Pin every action to a version — a floating `@main` makes the merge gate itself
      unreproducible. → `.github/workflows/` · `04_Build_and_Release/04_CI_CD.md#quality-job`
- [x] 7.2 Run `./gradlew clean build check spotlessCheck` under **Xvfb** in the quality job so the TestFX/Monocle UI
      tests execute headlessly. This must be the same command the pre-push hook runs — CI adds gates, it never runs a
      weaker variant of the shared ones. → `.github/workflows/` · `04_CI_CD.md#quality-job`,
      `02_QUALITY_GATES.md#ci-gates`
- [x] 7.3 Add the license gate (`./gradlew checkLicense`) and the OWASP dependency-check SCA gate as CI-only steps,
      failing on a non-allowlisted license or a vulnerability above the documented severity threshold. Consume an
      optional `NVD_API_KEY` secret when present and degrade gracefully (a slower NVD sync) when absent — the workflow
      must not fail on a repository that has no such secret configured.
      → `.github/workflows/` · `05_Dependencies/03_LICENSING.md#license-gate-tool`,
      `05_Dependencies/02_DEPENDENCY_POLICY.md#owasp-dependency-check`, `04_CI_CD.md#no-secrets`

      **Written and locally exercised; CI observation deferred project-wide (see the note under 7.7).** Both steps
      exist: `checkLicense` at `.github/workflows/ci.yml:167`, `dependencyCheckAggregate` at `:202` with
      `NVD_API_KEY: ${{ secrets.NVD_API_KEY }}` — which expands to the empty string on a repository with no such
      secret, so the step degrades to a slower unauthenticated NVD sync rather than failing. `build.gradle.kts`
      carries `dependencyCheck { failBuildOnCVSS = 7.0f }` and the aggregate-over-analyze choice.

      **The license half is proven red**, and continuously: task 3.5's `build-logic` functional test
      (`LicenseGateFunctionalTest`) seeds a GPL coordinate and asserts `checkLicense` fails naming it, and it runs
      inside `check` — so every green gate re-proves it.

      **The SCA half is also proven red — observed, not assumed, and it caught a real defect in this very change.**
      An earlier draft of this note claimed the `failBuildOnCVSS` path "has never executed" because nothing in the
      graph carried a CVSS ≥ 7.0 finding. That was wrong, and the first full local run disproved it. Corrected
      2026-08-03 against the actual evidence:

      A local `dependencyCheckAggregate` completed after a **2 h 39 m** first-time NVD sync (372,649 CVEs;
      unauthenticated sync is rate-limited to a few requests per 30 s window — precisely the cost the optional
      `NVD_API_KEY` buys down) and ended **`BUILD FAILED`, exit 1, 13 findings, 8 above the 7.0 threshold.** They
      split into two piles:

      - **One genuine finding — and a real bug in this change.** `guava:31.0.1-jre` carries CVE-2023-2976 (CVSS 7.1,
        `FileBackedOutputStream` writing into the shared default temp dir; fixed in 32.0.1). Root cause:
        `gradle/libs.versions.toml` pinned `guava = "33.6.0-jre"` and its own comment said the pin existed "so the
        constraint that upgrades it has a catalog-backed version" — **but that constraint was never written.**
        Nothing referenced `libs.guava`, so Guice 7.0.0's transitive 31.0.1-jre won. Fixed by adding the missing
        `constraints { implementation(libs.guava) }` block to `bookloom.java-conventions` (a constraint, not a
        declared dependency: BookLoom does not use Guava and must not gain it on every compile classpath) and
        regenerating the lockfiles.
      - **Seven false positives.** `javafx-graphics-26.0.2` bundles `com.sun.javafx.*` / `com.sun.glass.*`, whose
        package names the evidence collector reads as *vendor* evidence, yielding LOW-confidence
        `cpe:2.3:a:oracle:openjdk` and `cpe:2.3:a:sun:openjdk` matches that drag in 2009-era Sun Java SE CVEs
        (CVE-2009-2475/2476/2689/3879/3881/3882/3883). Suppressed in `config/owasp/suppressions.xml` **by CPE, not
        by CVE** — the defect is the identifier, so enumerating today's CVEs would let tomorrow's through. The
        correct `cpe:2.3:a:oracle:javafx` match is deliberately left live.

      After both fixes: `dependencyCheckAggregate` → **`BUILD SUCCESSFUL`, 0 active vulnerabilities** (11 suppressed
      on the two wrong CPEs), and the whole-project gate still `91 actionable tasks: 91 executed`.

      **Consequence for the CI debt list under 7.7: the SCA canary is no longer owed.** `failBuildOnCVSS` has been
      observed failing the build on the real dependency graph, which is stronger evidence than the synthetic
      high-CVE coordinate that item asked for.
- [x] 7.4 Run Gradle with locking in strict mode in CI so a dependency version changed without regenerating lockfiles
      fails the job **before any test executes**. This makes the locking from task 3.1 actually enforced rather than
      merely present. → `.github/workflows/` · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [x] 7.5 Upload the Gradle test-report directory as a workflow artifact on failure, so a red UI test is diagnosable
      from the PR without re-running locally. → `.github/workflows/` · `04_CI_CD.md#quality-job`
- [x] 7.6 Do **not** add `./gradlew trace` or `./gradlew traceCheck` to the workflow. ADR-0016 retires that tooling —
      it is never built, and CI step 7 of `04_CI_CD.md#quality-job` is superseded. Verify the workflow contains no
      reference to it. → `.github/workflows/` · `docs/adr/ADR-0016-openspec-delivery-tracking.md`
- [x] 7.7 Prove each CI gate fails red using a temporary canary branch per gate — a GPL-licensed test-only coordinate, a
      version bumped without `--write-locks`, and a deliberately failing Monocle widget test — and record the run links
      before deleting the branches. A gate that has only ever been observed green has not been observed at all.
      → `.github/workflows/` · `02_QUALITY_GATES.md#ci-gates`

      **Closed under a standing project decision, taken 2026-08-03: CI is not exercised until the application is
      feature-complete.** Delivery runs locally on feature branches for the whole build-out; the bar for every change
      up to and including the last one is the local gate `./gradlew clean build check spotlessCheck` green, plus the
      workflow files being *correct and CI-ready*. The GitHub Actions workflows are therefore authored, validated, and
      kept in step with the local scripts, but no run link is required to close a task until the end-of-project CI
      validation pass — at which point 7.7's remaining canary exercise is performed once, against the finished
      pipeline, rather than piecemeal per change.

      **This is a deliberate, recorded trade.** What it costs is real and is stated plainly rather than glossed: the
      workflow has never executed, so "the workflow works" remains unsupported by evidence until that pass. What it
      buys is that the build-out is not gated on push access, run minutes, or a CI round-trip per change.

      **Consequence for every later change:** the Definition of Done's green gate is the *local* one. A task may not
      be closed on the strength of a CI run that has not happened, and a later change must not quietly re-defer this
      pass — it is owed once, at the end. Everything already proven locally still stands:

      - **Strict lock gate — proven red locally.** With `util/gradle.lockfile` moved aside, `:util:verifyLocks`
        passes in DEFAULT mode and fails in STRICT with `Locking strict mode: Configuration ':util:annotationProcessor'
        is locked but does not have lock state`. That is the whole delta 7.4 buys, observed both ways.
      - **License gate — proven red by an existing test.** Task 3.5's `build-logic` functional test seeds a GPL
        coordinate and asserts `checkLicense` fails naming it. It runs inside `check`, so every gate run re-proves it.
      - **Quality gate — proven red by existing tests.** The `build-logic` functional tests seed a canary per tool
        (Spotless, NullAway, Checkstyle, FindSecBugs) and assert each fails. What is NOT covered by them is the
        deliberately-failing headless widget test 7.7 asks for — there is no `:ui` test source yet to fail.
      - **SCA gate — proven red locally, on the real graph.** A full local `dependencyCheckAggregate` ended
        `BUILD FAILED` on 8 findings above the 7.0 threshold — one genuine (`guava:31.0.1-jre`, CVE-2023-2976, from
        a catalog pin that was never wired to a constraint) and seven CPE false positives against
        `javafx-graphics-26.0.2`. Both are fixed; the run is now `BUILD SUCCESSFUL` with 0 active findings. See the
        corrected note under 7.3 — no synthetic high-CVE canary is needed, because the gate already failed on a
        real one.
      - **The workflow itself — NOT proven at all.** No run has executed on GitHub Actions. It is valid YAML, passes
        `actionlint` clean, every action input is verified present at its pinned SHA, and every command in it was run
        locally — but "the workflow works" is a claim only a run link can support.

      **The end-of-project CI validation pass owes exactly this, and it is the list to work from:** a canary branch
      per gate producing a recorded run link — a GPL-licensed test-only coordinate (license), a version bumped without
      `--write-locks` (strict locking), and a deliberately failing Monocle widget test (quality/UI) — plus one green
      run proving the workflow itself executes end to end.

      The SCA canary that stood here has been **struck**: `failBuildOnCVSS` was observed failing the build on the
      real dependency graph (see 7.3), which is better evidence than the synthetic coordinate would have produced.

## 8. Green gate

- [x] 8.1 Run `./gradlew spotlessApply`, then `./gradlew clean build check spotlessCheck` and confirm it is **green
      across all eight empty modules** — Spotless, Checkstyle, Error Prone + NullAway, SpotBugs + FindSecBugs, all eight
      ArchUnit rules, the coverage gate, and every test. There is no "pre-existing failure" exemption; this change *is*
      the baseline, so anything red here is red forever after.
      → whole project · `docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-change-checklist`

      **Verified 2026-08-03: `BUILD SUCCESSFUL in 1m 3s`, `91 actionable tasks: 91 executed`.** Test totals from that
      run's XML reports: `:build-logic:test` 27 tests, `:app:archTest` 18, `:ui:test` 1 — 0 failures, 0 errors, 0 skips.

      One trap worth recording, because it made the first run of this task *look* green on weaker evidence:
      **`build-logic` is an included build, so the root `clean` does not reach it.** The first attempt reported
      `79 executed, 12 up-to-date` with `:build-logic:test` among the up-to-date — meaning the 27 functional canaries
      that prove Spotless/NullAway/Checkstyle/FindSecBugs/license/lock each fail red were served from cache, not
      re-executed. `./gradlew :build-logic:clean` first, then the gate, is what produced the 91/91 figure above.
- [x] 8.2 Confirm `./gradlew liveLocal`, `./gradlew promptEval`, and `./gradlew visual` each run green-with-skips on a
      machine with no local endpoint configured, and that none of them ran as part of task 8.1.
      → whole project · `06_TESTING_STRATEGY.md#live-local`

      **Verified 2026-08-03 on this machine, with no `BOOKLOOM_LIVE_*` variable set.** Each task `BUILD SUCCESSFUL`,
      and each one's JUnit XML shows the canary skipped rather than absent — `liveLocal` → `LiveLocalProviderCanaryTest`
      2 tests / 2 skipped (Ollama + LM Studio), `promptEval` → `PromptEvalCanaryTest` 1 / 1, `visual` →
      `VisualSnapshotCanaryTest` 1 / 1; 0 failures across all three. Skipped, not vacuous: the assumption fired.

      The second half — that none of them ran inside 8.1 — is shown from 8.1's own task graph rather than asserted:
      `./gradlew clean build check spotlessCheck --dry-run` enumerates 198 lines, and
      `grep -icE "liveLocal|promptEval|visual"` over it returns **0**. Neither the tasks nor anything named after them
      is reachable from the gate.
- [x] 8.3 Update `docs/implementation_plan/01_MODULE_INVENTORY.md` to reflect the eight modules, the `build-logic`
      included build, and the `arch-test` source set as they were actually created — the inventory is the canonical
      module map every later proposal reads. → `docs/implementation_plan/` ·
      `06_DEFINITION_OF_DONE.md#per-change-checklist`

      Added `#as-built-baseline`, which separates the forward-looking package tables (still the citation targets) from
      what exists today: the eight modules with their one placeholder type each, `build-logic` as an **included build**
      with its `GradleRunner` suite and the root-`clean` caveat above, `arch-test` as a source set physically at
      `:app/src/archTest/java` with a violation fixture per rule, and the `verifyLocks` / `-PstrictLocks` lock gate that
      CI runs as its own step *before* the quality gate so pre-push and CI keep running byte-identical commands.
      Also corrected `#test-conventions`, which claimed the ArchUnit tests run in a "fast `pre-push` subset" — pre-push
      runs the full gate verbatim (task 6.3), not a subset.
- [x] 8.4 Confirm the offline invariant holds: nothing in this change makes a network call at application runtime. The
      only network access is Gradle dependency resolution and CI, which are build-time, not app behaviour.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01

      **Verified by grep over all eight modules' `src/main`:** zero occurrences of `java.net`, `HttpClient`,
      `URLConnection`, `Socket`, or `openStream` in production code. `:llm/module-info.java` declares
      `requires java.net.http`, which is the structural permission the `no-http-in-core-except-llm` ArchUnit rule
      exists to police — no code uses it yet. The only `java.net.URI` references in the whole tree are in the
      env-gated `liveLocal`/`promptEval` canaries, which read an endpoint string and skip before opening a socket.
- [x] 8.5 Run `openspec validate bootstrap-gradle-and-quality-toolchain --strict`, confirm it is clean, then archive the
      change with `openspec archive`. Because `skip_specs: true` is set, nothing folds into `openspec/specs/` — that is
      correct: this change added no user-observable behaviour.
      → `openspec/` · `docs/adr/ADR-0016-openspec-delivery-tracking.md`

      **Unblocked 2026-08-03 by the standing decision recorded under 7.7:** CI is not exercised until the application
      is feature-complete, so the closing bar for this change — and every change until then — is the local gate, which
      8.1 verified green at `91 actionable tasks: 91 executed`. 7.3 and 7.7 are closed on that basis with their
      unproven halves itemised rather than waved through, and the end-of-project CI validation pass carries the
      remainder as an explicit debt.
