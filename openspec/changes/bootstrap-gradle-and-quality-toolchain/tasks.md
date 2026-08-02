# Tasks — bootstrap-gradle-and-quality-toolchain

Absorbs the former STORY-001 (Gradle multi-module + JPMS), STORY-004 (Spotless / Error Prone+NullAway / Checkstyle /
SpotBugs+FindSecBugs), STORY-005 (ArchUnit boundary suite), STORY-009 (test conventions, tags, coverage gate),
STORY-011 (Lefthook) and STORY-012 (CI + license report + SCA). STORY-010 (the `trace`/`traceCheck` tooling) is
**dropped, not converted** — ADR-0016 retires it.

## 1. Gradle skeleton and the eight JPMS modules

- [ ] 1.1 Commit the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` + `.properties`)
      pinned to one Gradle version with its distribution SHA-256. Every build in this project — local, hook, CI — must
      go through the wrapper, so a contributor's system `gradle` can never produce a different result from CI.
      → repo root · `04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-system`, DD-03
- [ ] 1.2 Write `settings.gradle.kts` including the eight subprojects `:api :util :document :llm :pipeline
      :persistence :ui :app` plus `includeBuild("build-logic")`. This fixes the module set for the whole project; every
      later change adds packages inside these eight, never a ninth subproject without an ADR.
      → repo root · `01_BUILD_AND_TOOLING.md#project-layout`, `02_Architecture/02_MODULES_AND_LAYERING.md`
- [ ] 1.3 Create `gradle/libs.versions.toml` with `[versions]`, `[libraries]`, `[bundles]`, `[plugins]` sections holding
      every dependency coordinate this change introduces. No build script may ever contain an inline
      `group:artifact:version` string — one catalog is the single source of truth, so a version bump is one edit and is
      reviewable in isolation. → repo root · `01_BUILD_AND_TOOLING.md#version-catalog`, DD-03
- [ ] 1.4 Write one `module-info.java` per subproject declaring the module name `ua.bookloom.<module>` and **only** the
      dependency edges the layering table allows (`:app` → all; `:ui` → `:pipeline :api :util` + `javafx.*`;
      `:pipeline` → `:document :llm :persistence :api :util`; `:document`/`:llm`/`:persistence` → `:api :util`;
      `:util` → `:api`; `:api` → nothing internal). A forbidden import then fails at **compile time**, before ArchUnit
      even runs — the module graph is enforced by the language, not by convention.
      → all eight modules · `02_MODULES_AND_LAYERING.md#dependency-direction`, DD-06
- [ ] 1.5 Add `requires com.google.guice;` and `opens <impl package> to com.google.guice;` to each service module's
      `module-info.java` now, even though no Guice module exists yet. Wiring it here means change 2 (the composition
      root) needs zero `module-info` churn, and reflective injection into a JPMS module fails at runtime rather than
      compile time if the `opens` is missing — a failure mode best avoided by never having it.
      → `:document :llm :pipeline :persistence :ui :app` · `02_MODULES_AND_LAYERING.md`,
      `02_Architecture/10_DI_AND_LIFECYCLE.md`
- [ ] 1.6 Add exactly one placeholder public type per module, named from the module inventory rather than invented, so
      each `module-info.java` has something to export and the module compiles. Keep them minimal — they are scaffolding
      that later changes replace, not API. → all eight modules · `docs/implementation_plan/01_MODULE_INVENTORY.md`
- [ ] 1.7 Add `.editorconfig` and `.gitattributes` fixing LF line endings, UTF-8, and final-newline for all text files.
      Without these, a Windows checkout produces CRLF that Spotless rewrites on every commit, generating churn that
      makes real diffs unreadable. → repo root · `01_BUILD_AND_TOOLING.md`,
      `.claude/rules/gradle-build-and-quality.md`
- [ ] 1.8 Verify `./gradlew build` compiles all eight modules green from a clean checkout, and write a functional test
      asserting it. This is the floor everything else stands on; if it is not reproducible from a clean clone, nothing
      downstream is. → `build-logic` test · `01_BUILD_AND_TOOLING.md#build-system`

## 2. Convention plugins in `build-logic`

- [ ] 2.1 Create the `build-logic` included build with the `kotlin-dsl` plugin and a `src/main/kotlin` source root for
      precompiled script plugins. All shared configuration lives here rather than in an `allprojects { }` block,
      because a convention plugin is configuration-cache friendly and can itself be unit-tested — an `allprojects`
      block is neither. → `build-logic` · `01_BUILD_AND_TOOLING.md#convention-plugins`, DD-03
- [ ] 2.2 Write `bookloom.java-conventions.gradle.kts` setting `java.toolchain.languageVersion = 25` and the JPMS
      compile arguments. Pinning the toolchain means a contributor with JDK 21 installed still builds against 25 —
      Gradle downloads it — so "works on my machine" cannot diverge on language level.
      → `build-logic` · `01_BUILD_AND_TOOLING.md#build-system`, ADR-0001
- [ ] 2.3 Write `bookloom.spotless-conventions.gradle.kts` applying Spotless with Palantir Java Format at 120 columns,
      exposing `spotlessApply` and `spotlessCheck`. Formatting must be mechanical and never argued about in review;
      `spotlessApply` in the pre-commit hook means it is fixed before it is ever seen.
      → `build-logic` · `04_Build_and_Release/02_QUALITY_GATES.md#tools`, DD-25
- [ ] 2.4 Wire Lombok as the **sole** entry on `annotationProcessor` in `bookloom.java-conventions`, and add a root
      `lombok.config` with `lombok.addNullAnnotations` set so generated members carry nullability annotations. Lombok
      desugars before the Error Prone javac plugin walks the tree, so NullAway analyzes the generated constructor, not
      the annotation — without the config it raises false non-null violations on every `@RequiredArgsConstructor`
      service. → `build-logic`, repo root · `01_BUILD_AND_TOOLING.md#error-prone-lombok`, DD-05, ADR-0014
- [ ] 2.5 Add the `net.ltgt.errorprone` plugin with NullAway as an Error Prone check and `-XDcompilePolicy=simple`, and
      configure Error Prone's SECURITY and CORRECTNESS categories to **fail** the build. Error Prone is a javac plugin,
      not an annotation processor, and it requires the simple compile policy to see one flat compilation per file — the
      flag is not optional. → `build-logic` · `01_BUILD_AND_TOOLING.md#error-prone-lombok`,
      `02_QUALITY_GATES.md#null-safety`
- [ ] 2.6 Seed a `@NullMarked` (JSpecify) `package-info.java` in every package created by this change, and make it the
      documented convention for new packages. NullAway only checks packages that opt in, so an unmarked package is
      silently unanalyzed — the marker is what turns the tool on.
      → all eight modules · `02_QUALITY_GATES.md#null-safety`, `.claude/rules/java-coding-style.md`
- [ ] 2.7 Add Checkstyle with the project ruleset under `config/checkstyle/` and bind it into `check`. Checkstyle covers
      the naming and structural conventions Spotless does not (Spotless formats; it does not judge).
      → `build-logic`, `config/checkstyle/` · `02_QUALITY_GATES.md#tools`
- [ ] 2.8 Add SpotBugs with the FindSecBugs plugin, bound into `check`, failing on high-priority findings. FindSecBugs
      is the automated backstop for the credentials-as-reference invariant — it flags a hard-coded secret or an unsafe
      credential path that review can miss. → `build-logic`, `config/spotbugs/` · `02_QUALITY_GATES.md#tools`, DD-11
- [ ] 2.9 Write `bookloom.javafx-conventions.gradle.kts` applying `org.openjfx.javafxplugin` with the JavaFX 25
      `controls`, `fxml`, and `graphics` modules, and apply it to **`:ui` and `:app` only**. Keeping JavaFX in a
      separate plugin makes the FX-free core structural: a core module would have to deliberately apply a plugin it has
      no reason to apply. → `build-logic`, `:ui`, `:app` · `01_BUILD_AND_TOOLING.md#javafx-plugin`, DD-06
- [ ] 2.10 Write a functional test that an unformatted source file fails `spotlessCheck` and is fixed by
      `spotlessApply`; a test that a NullAway-detectable null defect in a `@NullMarked` package fails `check` while a
      Lombok `@RequiredArgsConstructor` service in the same package compiles clean; and tests that seeded Checkstyle and
      FindSecBugs canary violations each fail `check`. A quality tool that is configured but not proven to fail red is
      indistinguishable from one that is switched off. → `build-logic` test · `02_QUALITY_GATES.md#tools`

## 3. Dependency locking and the license gate

- [ ] 3.1 Enable Gradle dependency locking across the platform-neutral configurations and commit the lockfiles. A
      locked graph means an upstream republishing a version cannot silently change a build, and a version bump becomes a
      reviewable diff instead of an invisible resolution change.
      → repo root, all modules · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [ ] 3.2 Exclude the JavaFX variant-classified configurations from locking and rely on the exact catalog pin for those
      artifacts, per design decision D7. JavaFX resolves a different per-OS classified artifact on each platform, so a
      single committed lockfile is genuinely not reproducible across platforms — asserting otherwise would be a false
      claim baked into the build. → repo root, `:ui`, `:app` · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [ ] 3.3 Add the `com.github.jk1.dependency-license-report` plugin with an allowed-license policy file permitting
      Apache-2.0, MIT, and the BSD family plus the three recorded exceptions — EPL-1.0 (Logback), the ICU License
      (ICU4J), and the JDOM License — and banning GPL/LGPL/AGPL/SSPL. Wire it as `checkLicense` and generate
      `THIRD-PARTY-NOTICES`. This ships as an MIT-licensed distributable; one copyleft transitive would make that claim
      false. → repo root, `config/license/` · `05_Dependencies/03_LICENSING.md#license-gate-tool`
- [ ] 3.4 Run `checkLicense` against the full dependency graph **now, before the CI wiring lands**, and resolve any
      non-allowlisted transitive by swapping the tool rather than widening the allowlist. Discovering an unacceptable
      license after CI is green means either a rushed exception or ripping out a tool everything already depends on.
      → repo root · `05_Dependencies/03_LICENSING.md`
- [ ] 3.5 Write a functional test that resolving with a version absent from the lock state fails the build, and one
      that a coordinate carrying a banned license (a test-only GPL canary) fails `checkLicense` naming the offending
      coordinate. → `build-logic` test · `01_BUILD_AND_TOOLING.md#dependency-locking`,
      `05_Dependencies/03_LICENSING.md`

## 4. The ArchUnit boundary suite

- [ ] 4.1 Create the shared `arch-test` source set and wire it into `check`. The rules live once and see the whole
      module graph — `dependency-direction` is meaningless from inside a single module, and per-module copies would
      drift apart. → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [ ] 4.2 Implement `fx-free-core`: no class in `:api :util :document :llm :pipeline :persistence` may depend on
      `javafx..`. The core must run headless in JUnit and WireMock; a JavaFX dependency anywhere in it would make the
      business logic untestable without a toolkit. → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`, DD-06
- [ ] 4.3 Implement `dependency-direction` as a layered check encoding exactly the allowed edges, accepting every
      permitted edge and rejecting every omitted one across the full eight-module graph. This is the rule that keeps the
      graph an acyclic DAG pointing at `:api`. → `arch-test` · `02_MODULES_AND_LAYERING.md#dependency-direction`
- [ ] 4.4 Implement `ports-not-concretes`: a caller in another module must depend on the `:api` interface, never on a
      concrete `..Impl`/`..Dao`/`..Service`. Ports are what make every collaborator mockable at the `:api` seam.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [ ] 4.5 Implement `no-http-in-core-except-llm` (only `:llm` may import `java.net.http..`) and
      `no-sql-in-core-except-persistence` (only `:persistence` may import `java.sql..`, `org.jdbi..`, `org.flywaydb..`).
      The HTTP rule is the **structural seed of the offline invariant (F9)**: it makes "only one module can open a
      socket" a compile-gate fact rather than a promise.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`, DD-01
- [ ] 4.6 Implement `api-is-framework-free`: `:api` may not import Guice, Jackson, JavaFX, JDBI, or any parser library.
      `:api` is the dependency floor every other module points at — a framework there propagates to all eight.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [ ] 4.7 Implement `records-first` scoped to **carrier packages only** (`..dto`, `..api..`): data carriers must be
      records, never Lombok `@Data`/`@Value`. Service classes are deliberately out of scope — Lombok
      `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on services is the accepted hybrid.
      → `arch-test` · DD-05, ADR-0014
- [ ] 4.8 Implement `bootstrap-no-static-logger`: no class on the pre-logging startup path — `ua.bookloom.app`
      `Launcher` / lock acquisition and `ua.bookloom.util.paths` — may declare a static SLF4J `Logger` or touch
      `org.slf4j..` at class-init time. It has no subject until change 2, and that is the point: shipping it now means
      change 2 cannot introduce the violation, where a static logger firing before Logback is configured would pin
      logging to the wrong directory. → `arch-test` · `02_Architecture/10_DI_AND_LIFECYCLE.md#logging-bootstrap-order`,
      DD-39, ADR-0015
- [ ] 4.9 Add a **violation fixture per rule** in test sources — an FX import in `:document`, a reversed module edge, an
      HTTP client in `:pipeline`, SQL in `:llm`, a Guice import in `:api`, a mutable class in an `..api..` package, a
      `static final Logger` in the bootstrap package — with a test asserting each rule rejects its fixture. On an empty
      tree every rule passes vacuously, so without fixtures a rule with a typo'd package scope sits green forever.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`
- [ ] 4.10 Confirm all eight rules execute under `./gradlew check` and are green on the scaffold, asserted by a test
      that counts them rather than by inspection — a rule silently dropped from the suite is invisible otherwise.
      → `arch-test` · `02_MODULES_AND_LAYERING.md#archunit-rules`

## 5. Test conventions, tag sets, and the coverage gate

- [ ] 5.1 Write `bookloom.test-conventions.gradle.kts` providing JUnit 5 + AssertJ + Mockito 5 + WireMock +
      TestFX/Monocle to every module, with the Monocle headless system properties set. Fixing the stack once means no
      change ever re-argues which assertion library to use, and UI tests never need a display server.
      → `build-logic` · `04_Build_and_Release/06_TESTING_STRATEGY.md#test-types`
- [ ] 5.2 Configure the default `Test` task with `useJUnitPlatform { excludeTags("liveLocal", "promptEval", "visual") }`
      and register three separate tasks (`liveLocal`, `promptEval`, `visual`), each `includeTags`-ing exactly its own
      tag and **not** wired into `check`. The task graph is the only exclusion mechanism — an `@Disabled` or an
      in-test environment sniff is invisible in the graph, so a live test accidentally running in CI would look like a
      normal green test until it timed out on a runner.
      → `build-logic` · `06_TESTING_STRATEGY.md#live-local`, DD-35
- [ ] 5.3 Make each tagged task **skip cleanly rather than fail** when its environment gate is absent, via a JUnit 5
      assumption or `@EnabledIfEnvironmentVariable`. A fresh checkout with no local Ollama must report
      `./gradlew liveLocal` as green-with-skips, not red. → `build-logic` · `06_TESTING_STRATEGY.md#live-local`
- [ ] 5.4 Configure the JaCoCo branch-coverage gate at exactly 80% per module on `:api :util :document :llm :pipeline
      :persistence`, excluding `:ui` (covered behaviourally by TestFX) and excluding modules with **no production
      sources**. All eight modules are empty right now, so without the empty-module exclusion `./gradlew check` fails on
      day one — and the exclusion must be explicit configuration, not a happy accident of how JaCoCo reports zero
      classes. → `build-logic` · `02_QUALITY_GATES.md#coverage-gate`, `06_TESTING_STRATEGY.md#coverage-traceability`
- [ ] 5.5 Add a canary test per excluded tag and a TestFX + Monocle canary UI test, then write functional tests
      asserting that `./gradlew test` and `./gradlew check` run zero tagged tests while each tagged task runs exactly
      its own, that a module with production code below 80% branch coverage fails `check` while an empty module is
      exempt, and that the Monocle canary renders headlessly. → `build-logic` test, `:ui` test ·
      `02_QUALITY_GATES.md#coverage-gate`, `06_TESTING_STRATEGY.md#test-types`

## 6. Lefthook git hooks

- [ ] 6.1 Write `lefthook.yml` with a `pre-commit` stage under ~10 seconds: Spotless apply on **staged Java files only**
      with the formatted output re-staged, gitleaks, and a file-size guard. Staged-only matters for partial commits —
      formatting churn from unrelated files must not silently join the commit.
      → repo root · `02_QUALITY_GATES.md#lefthook-stages`, DD-25
- [ ] 6.2 Add the `commit-msg` stage enforcing Conventional Commits, so the history stays machine-readable for the
      release tooling that arrives in change 27. → repo root · `02_QUALITY_GATES.md#lefthook-stages`
- [ ] 6.3 Add the `pre-push` stage running exactly `./gradlew clean build check spotlessCheck` — the **identical**
      command CI runs, with no hook-only weaker variant — so a green push implies a green CI quality job for the same
      tree. → repo root · `02_QUALITY_GATES.md#lefthook-stages`, `#what-fails-where`
- [ ] 6.4 Document the `lefthook install` setup step in the README and note the `git push --no-verify` escape hatch in
      the config comments, so hooks are active after a fresh clone without hidden ceremony and the bypass is a known
      decision rather than a rediscovered trick. → repo root, `README.md` · `02_QUALITY_GATES.md#lefthook-stages`
- [ ] 6.5 Add scripted hook checks (not part of the Gradle `check` graph, since they need a git repo with hooks
      installed): one committing a mis-formatted staged file and asserting the committed content is formatted, and one
      introducing a Checkstyle violation and asserting `git push` fails before any ref transfers.
      → `tooling/hooks-test/` · `02_QUALITY_GATES.md#lefthook-stages`

## 7. CI quality workflow

- [ ] 7.1 Write `.github/workflows/ci.yml` triggered on pull requests targeting `master`, running on `ubuntu-24.04`
      with Temurin 25 and the Gradle cache, with a concurrency group that cancels superseded runs of the same PR and a
      timeout guard on every job. Pin every action to a version — a floating `@main` makes the merge gate itself
      unreproducible. → `.github/workflows/` · `04_Build_and_Release/04_CI_CD.md#quality-job`
- [ ] 7.2 Run `./gradlew clean build check spotlessCheck` under **Xvfb** in the quality job so the TestFX/Monocle UI
      tests execute headlessly. This must be the same command the pre-push hook runs — CI adds gates, it never runs a
      weaker variant of the shared ones. → `.github/workflows/` · `04_CI_CD.md#quality-job`,
      `02_QUALITY_GATES.md#ci-gates`
- [ ] 7.3 Add the license gate (`./gradlew checkLicense`) and the OWASP dependency-check SCA gate as CI-only steps,
      failing on a non-allowlisted license or a vulnerability above the documented severity threshold. Consume an
      optional `NVD_API_KEY` secret when present and degrade gracefully (a slower NVD sync) when absent — the workflow
      must not fail on a repository that has no such secret configured.
      → `.github/workflows/` · `05_Dependencies/03_LICENSING.md#license-gate-tool`,
      `05_Dependencies/02_DEPENDENCY_POLICY.md#owasp-dependency-check`, `04_CI_CD.md#no-secrets`
- [ ] 7.4 Run Gradle with locking in strict mode in CI so a dependency version changed without regenerating lockfiles
      fails the job **before any test executes**. This makes the locking from task 3.1 actually enforced rather than
      merely present. → `.github/workflows/` · `01_BUILD_AND_TOOLING.md#dependency-locking`
- [ ] 7.5 Upload the Gradle test-report directory as a workflow artifact on failure, so a red UI test is diagnosable
      from the PR without re-running locally. → `.github/workflows/` · `04_CI_CD.md#quality-job`
- [ ] 7.6 Do **not** add `./gradlew trace` or `./gradlew traceCheck` to the workflow. ADR-0016 retires that tooling —
      it is never built, and CI step 7 of `04_CI_CD.md#quality-job` is superseded. Verify the workflow contains no
      reference to it. → `.github/workflows/` · `docs/adr/ADR-0016-openspec-delivery-tracking.md`
- [ ] 7.7 Prove each CI gate fails red using a temporary canary branch per gate — a GPL-licensed test-only coordinate, a
      version bumped without `--write-locks`, and a deliberately failing Monocle widget test — and record the run links
      before deleting the branches. A gate that has only ever been observed green has not been observed at all.
      → `.github/workflows/` · `02_QUALITY_GATES.md#ci-gates`

## 8. Green gate

- [ ] 8.1 Run `./gradlew spotlessApply`, then `./gradlew clean build check spotlessCheck` and confirm it is **green
      across all eight empty modules** — Spotless, Checkstyle, Error Prone + NullAway, SpotBugs + FindSecBugs, all eight
      ArchUnit rules, the coverage gate, and every test. There is no "pre-existing failure" exemption; this change *is*
      the baseline, so anything red here is red forever after.
      → whole project · `docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-change-checklist`
- [ ] 8.2 Confirm `./gradlew liveLocal`, `./gradlew promptEval`, and `./gradlew visual` each run green-with-skips on a
      machine with no local endpoint configured, and that none of them ran as part of task 8.1.
      → whole project · `06_TESTING_STRATEGY.md#live-local`
- [ ] 8.3 Update `docs/implementation_plan/01_MODULE_INVENTORY.md` to reflect the eight modules, the `build-logic`
      included build, and the `arch-test` source set as they were actually created — the inventory is the canonical
      module map every later proposal reads. → `docs/implementation_plan/` ·
      `06_DEFINITION_OF_DONE.md#per-change-checklist`
- [ ] 8.4 Confirm the offline invariant holds: nothing in this change makes a network call at application runtime. The
      only network access is Gradle dependency resolution and CI, which are build-time, not app behaviour.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01
- [ ] 8.5 Run `openspec validate bootstrap-gradle-and-quality-toolchain --strict`, confirm it is clean, then archive the
      change with `openspec archive`. Because `skip_specs: true` is set, nothing folds into `openspec/specs/` — that is
      correct: this change added no user-observable behaviour.
      → `openspec/` · `docs/adr/ADR-0016-openspec-delivery-tracking.md`
