**Status:** Final **Owner:** architect **Audience:** architect, coder, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/05_Dependencies/01_DEPENDENCIES.md`,
`docs/specification/05_Dependencies/02_DEPENDENCY_POLICY.md`,
`docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`

# Licensing

The application is **MIT-licensed** and depends only on permissively-licensed libraries. This document fixes the license
gate and records the license of each dependency.

## app-license {#app-license}

BookLoom ships under the **MIT License**. The `LICENSE` file at the repo root is authoritative; the About dialog and
installer metadata reference it.

## permissive-only-gate {#permissive-only-gate}

The machine-checked allowlist contains only permissive, distribution-safe licenses. Base allowlist: **Apache-2.0**,
**MIT**, **BSD (2-/3-clause)**. Also acceptable when it appears on JDK-adjacent artifacts:
**EPL/GPL-with-Classpath-Exception** *only* for the JavaFX runtime modules (GPLv2+CPE, distributed like the JDK).
Everything copyleft that would impose obligations on the app — **GPL, LGPL (as a hard rule here), AGPL, SSPL** — is
**banned**. A dependency whose license is not on the allowlist fails the build.

Three additional licenses are on the allowlist for named bundled dependencies, each with a recorded justification (all
permissive, all distribution-safe under the jlink runtime image):

| Allowed license  | Bundled dependency                         | Justification                                                                                                                                                                                                                                       |
|------------------|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **EPL-1.0**      | Logback (`logback-classic`/`logback-core`) | Eclipse Public License 1.0 is a weak/file-level copyleft that imposes no obligation on the app's own MIT-licensed sources; Logback is used unmodified as a library. Logback is EPL-1.0 / LGPL-2.1 dual — we take the **EPL-1.0** option explicitly. |
| **ICU License**  | ICU4J                                      | The ICU License is an MIT/X-style permissive license (attribution only); ICU4J is bundled for sentence segmentation and ICU i18n `MessageFormat` (DD-48).                                                                                           |
| **JDOM License** | JDOM2                                      | The JDOM License is a permissive, Apache-style BSD-derived license (attribution only, no copyleft); JDOM2 is bundled for FB2/EPUB XML round-trip.                                                                                                   |

GPL/AGPL/SSPL remain banned regardless of the above. A new or transitively-introduced license absent from this allowlist
blocks merge until removed or replaced.

## license-gate-tool {#license-gate-tool}

The gate is enforced by a **dedicated license-report/allowed-license plugin —
`com.github.jk1.dependency-license-report`** — configured with an **allowed-license policy file**
(`config/license/allowed-licenses.json`) enumerating exactly the licenses in `#permissive-only-gate` (with per-artifact
normalization/aliases for the EPL-1.0, ICU, and JDOM strings). Its `checkLicense` task **fails the build** on any
resolved runtime/bundled artifact whose license is not on the allowlist.

This license gate is **distinct from OWASP dependency-check**: the license plugin verifies *license compliance* against
the allowlist, whereas OWASP dependency-check (`02_DEPENDENCY_POLICY.md#owasp-dependency-check`) is software-composition
analysis for *known vulnerabilities*. Both run in CI (`04_Build_and_Release/02_QUALITY_GATES.md`,
`04_Build_and_Release/04_CI_CD.md`) and either can fail the merge gate independently. The same plugin also emits the
`THIRD-PARTY-NOTICES` artifact (`#notices`).

## per-dependency-licenses {#per-dependency-licenses}

| Dependency                          | License                                                                   |
|-------------------------------------|---------------------------------------------------------------------------|
| sqlite-jdbc (org.xerial)            | Apache-2.0                                                                |
| Flyway (flyway-core)                | Apache-2.0                                                                |
| JDBI 3                              | Apache-2.0                                                                |
| JDOM2                               | BSD-style (JDOM License, Apache-like)                                     |
| dom4j (if used instead of JDOM2)    | BSD-style                                                                 |
| jsoup                               | MIT                                                                       |
| commonmark-java                     | BSD-2-Clause                                                              |
| Jackson (databind, datatype-jsr310) | Apache-2.0                                                                |
| Lingua                              | Apache-2.0                                                                |
| ICU4J                               | ICU License (permissive, MIT/X-style)                                     |
| Google Guice                        | Apache-2.0                                                                |
| JavaFX 25                           | GPLv2 + Classpath Exception (JDK-adjacent; permitted for the runtime)     |
| AtlantaFX                           | MIT                                                                       |
| Ikonli                              | Apache-2.0                                                                |
| ControlsFX                          | BSD-3-Clause                                                              |
| SLF4J                               | MIT                                                                       |
| Logback                             | EPL-1.0 / LGPL-2.1 dual — **use under EPL-1.0** (permissive)              |
| Lombok                              | MIT (compile-time annotation processor; not shipped in the runtime image) |
| JUnit 5                             | EPL-2.0 (test-scope)                                                      |
| AssertJ                             | Apache-2.0 (test-scope)                                                   |
| Mockito                             | MIT (test-scope)                                                          |
| WireMock                            | Apache-2.0 (test-scope)                                                   |
| TestFX                              | EUPL-1.1 / Apache-2.0 — **use under Apache-2.0** (test-scope)             |
| Monocle                             | GPLv2 + Classpath Exception (test-scope, JDK-adjacent)                    |
| Error Prone                         | Apache-2.0 (build-scope)                                                  |
| NullAway                            | MIT (build-scope)                                                         |
| Checkstyle                          | LGPL-2.1 — **build-tool only, not distributed** (see note)                |
| SpotBugs / FindSecBugs              | LGPL-2.1 / Apache-2.0 — **build-tool only, not distributed**              |
| ArchUnit                            | Apache-2.0 (test-scope)                                                   |
| Spotless / Palantir Java Format     | Apache-2.0 (build-scope)                                                  |

## build-tool-exception {#build-tool-exception}

Build/analysis tools that are **not distributed with the application** (Checkstyle, SpotBugs, PIT) are exempt from the
runtime permissive-only gate: their LGPL/GPL licenses apply to the tool the developer runs, not to any code shipped to
users. The gate's hard ban applies to **runtime and bundled** dependencies (anything reachable in the jlink image).
Logback is bundled, so it is used explicitly under its permissive **EPL-1.0** option; TestFX and JUnit are test-scope
and not shipped.

## notices {#notices}

A generated `THIRD-PARTY-NOTICES` file (from the `com.github.jk1.dependency-license-report` task, `#license-gate-tool`)
lists every bundled dependency and its license for inclusion with releases, satisfying attribution requirements of the
permissive licenses (including the EPL-1.0, ICU, and JDOM attributions).
