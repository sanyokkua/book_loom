# License gate policy

`allowed-licenses.json` is the machine-checked allowlist behind `./gradlew checkLicense`. JSON has no comments, so
the justification for every entry lives here. **The two files must be changed together** — an entry added there
without a row here is an unexplained widening of the gate, which is the one thing this policy exists to prevent.

Binding source: `docs/specification/05_Dependencies/03_LICENSING.md` (frozen). This file records; it does not decide.

## What the gate checks

Only the `runtimeClasspath` of the eight subprojects — the graph that reaches a user. See the comment on
`licenseReport { configurations = ... }` in the root `build.gradle.kts` for why that scoping is the load-bearing
part of the design.

## Canonical license names

Entries use **SPDX identifiers**, because `LicenseBundleNormalizer` (the `filters` entry in the root build script)
folds the many spellings found in POMs — `Apache 2`, `The Apache Software License, Version 2.0`, a bare URL — onto
SPDX canonical names before the check runs. Writing `Apache License, Version 2.0` here would silently match nothing.
The canonical set ships inside the plugin as `default-license-normalizer-bundle.json`.

## Base allowlist

| Entry | Justification |
|---|---|
| `Apache-2.0` | Permissive, distribution-safe. `03_LICENSING.md#permissive-only-gate`. |
| `MIT`, `MIT-0` | Permissive, distribution-safe. BookLoom itself ships under MIT. |
| `BSD-2-Clause`, `BSD-3-Clause`, `0BSD` | The BSD family named in `#permissive-only-gate`. |

## Recorded exceptions

Each is scoped to its named module by `moduleName` regex, never granted globally. A blanket `EPL-1.0` entry would
admit any future EPL dependency without review; scoping means the exception covers exactly the artifact whose
justification was recorded.

| Entry | Module | Justification |
|---|---|---|
| `EPL-1.0` | `ch.qos.logback:*` | Weak, file-level copyleft imposing no obligation on the app's own MIT sources; Logback is used unmodified as a library. Logback is EPL-1.0 / LGPL-2.1 dual — the **EPL-1.0** option is taken explicitly. |
| `Unicode/ICU License`, `ICU License` | `com.ibm.icu:icu4j` | An MIT/X-style permissive license (attribution only). ICU4J is bundled for sentence segmentation and ICU `MessageFormat` (DD-48). Two spellings are listed because this license is not in the normalizer's SPDX bundle, so the raw POM string is matched. |
| `JDOM License` | `org.jdom:jdom2` | A permissive, Apache-style BSD-derived license (attribution only, no copyleft). JDOM2 is bundled for FB2/EPUB XML round-trip. Also not in the SPDX bundle. |
| `GNU GENERAL PUBLIC LICENSE, Version 2 + Classpath Exception` | `org.openjfx:*` | JDK-adjacent: JavaFX is distributed on the same terms as the JDK, and the Classpath Exception is precisely what permits linking it into an independently-licensed application. `#permissive-only-gate` admits it *only* for the JavaFX runtime modules, which is what the `org.openjfx:` scope enforces. |
| `PUBLIC DOMAIN` | `aopalliance:aopalliance` | **ADR-0020.** A hard transitive of Guice 7.0.0, which the spec mandates as the DI container. Guice 7.0.0 is the newest release and publishes no `no_aop` variant, so the spec's "removed or replaced" remedy has no viable form. Public domain imposes no obligations at all — strictly more permissive than MIT — so it is not the risk this gate manages. Scoped to the single artifact: any other public-domain dependency still fails the build. |

Logback, ICU4J and JDOM2 are not dependencies yet — they arrive with the changes that first use them. Their entries
are inert until then, and are written now so that adding the dependency is not also a change to security policy.

## What is deliberately absent

- **LGPL-2.1** — Checkstyle and SpotBugs are LGPL-2.1 and are *not* allowlisted. They pass the gate because they are
  never resolved by it: they are developer tooling on the `checkstyle`/`spotbugs` configurations and reach no user
  (`03_LICENSING.md#build-tool-exception`). Allowlisting them would also have admitted LGPL code into the shipped
  image.
- **EPL-2.0** — JUnit is EPL-2.0 and is test-scope, so it is off `runtimeClasspath` for the same structural reason.
- **GPL / AGPL / SSPL** — banned outright, with no exception mechanism.

## Changing this policy

A non-allowlisted license on a *runtime* dependency is resolved by **replacing the dependency**, not by adding an
entry here. An entry is only correct when the license is genuinely permissive and the spec already records it.
