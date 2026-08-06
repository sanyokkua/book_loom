# ADR-0020 — Allow the Public Domain license, scoped to `aopalliance:aopalliance`

**Status:** accepted **Date:** 2026-08-03
**Deciders:** project owner
**Extends:** the license allowlist in `05_Dependencies/03_LICENSING.md#permissive-only-gate` (the ban on
GPL/LGPL/AGPL/SSPL and all three recorded exceptions stand unchanged)

## Context and problem statement

`05_Dependencies/03_LICENSING.md#permissive-only-gate` fixes a closed allowlist — Apache-2.0, MIT, BSD (2-/3-clause),
plus GPLv2+CPE for the JavaFX runtime modules, plus exactly three recorded exceptions (EPL-1.0 for Logback, the ICU
License for ICU4J, the JDOM License for JDOM2) — and states that "a new or transitively-introduced license absent from
this allowlist blocks merge until removed or replaced."

Wiring that gate in change `bootstrap-gradle-and-quality-toolchain` (tasks 3.3/3.4) produced exactly one violation
across the whole runtime graph:

```
aopalliance:aopalliance:1.0 - [PUBLIC DOMAIN]
```

`aopalliance:aopalliance:1.0` declares `<name>Public Domain</name>` in its POM and is a **hard transitive of Guice
7.0.0**, which the frozen specification mandates as the DI container (`02_Architecture/10_DI_AND_LIFECYCLE.md`,
ADR-0001-era stack decisions, and six `module-info.java` files that already declare `requires com.google.guice`).

The spec anticipates neither `aopalliance` nor the Public Domain designation, so this is a genuine gap rather than a
decision the spec already made. Per ADR-0016 the remedy is this ADR, not an edit to `docs/specification/**`.

The spec's own escape hatch — "removed or replaced" — has no viable form here:

- **Guice 7.0.0 is the newest release.** No later version drops the dependency.
- **Guice 7.0.0 publishes no `no_aop` variant.** `guice-7.0.0-no_aop.jar` returns 404 on Maven Central, so the usual
  "take the variant without the transitive" route does not exist for this version.
- Guice's `ProxyFactory` and `InterceptorBindingProcessor` reference `org.aopalliance.intercept.*` on the
  injector-creation path, so simply excluding the artifact risks `NoClassDefFoundError` when the composition root is
  built — a failure the bootstrap change cannot detect, because it has no runtime yet.

## Decision drivers

- **Public Domain is not the risk the gate exists to manage.** The gate's purpose is to keep BookLoom's MIT claim
  true by excluding licenses that impose obligations on the distributable. Public domain imposes *none* — it is
  strictly more permissive than MIT, and categorically unlike the GPL/LGPL/AGPL/SSPL family the spec bans.
- **The allowlist must stay closed.** An exception that is granted globally would silently admit every future
  public-domain artifact without review, which is the failure mode a machine-checked allowlist exists to prevent.
- **Vendoring is not a licensing remedy.** Hand-copying the four AOP Alliance interfaces into the codebase would
  remove the line from the report while shipping the identical public-domain code, and would put a foreign package
  into `:api` — the module whose entire value is having no dependencies. It hides a finding rather than resolving one.
- **The decided stack is not up for renegotiation over a transitive.** Replacing Guice would touch six
  `module-info.java` files, the DI-and-lifecycle architecture, and the composition-root change that follows.

## Considered options

- **A — Record Public Domain as a fourth exception, scoped to `aopalliance:aopalliance`.**
- **B — Exclude `aopalliance` from the Guice dependency.**
- **C — Vendor the four AOP Alliance interfaces into the project.**
- **D — Replace Guice with a DI framework that has no public-domain transitive.**

## Decision outcome

Chosen: **A — record Public Domain as a fourth exception, scoped by module to `aopalliance:aopalliance`.**

It is the only option that keeps the mandated stack intact while leaving the gate genuinely strict. The exception is
written into `config/license/allowed-licenses.json` with a `moduleName` constraint, so it grants nothing beyond this
one artifact; any other public-domain dependency introduced later still fails the build and still requires a decision.

The three existing exceptions, the base allowlist, and the outright ban on GPL/LGPL/AGPL/SSPL are unchanged. So is
`#build-tool-exception`: Checkstyle and SpotBugs remain LGPL-2.1 and remain un-allowlisted — they pass because the
gate is scoped to `runtimeClasspath` and they are never resolved by it.

### Consequences

- Positive: `./gradlew checkLicense` is green across the full runtime graph without weakening any ban.
- Positive: Guice, the six `module-info.java` files that require it, and the forthcoming composition root are all
  untouched.
- Positive: the exception is scoped and recorded, so a second public-domain artifact is a fresh decision rather than
  an inherited waiver.
- Negative: the allowlist in `03_LICENSING.md#permissive-only-gate` now has four exceptions rather than three, and
  that clause must be read through this ADR. The spec file stays unedited.
- Neutral: `THIRD-PARTY-NOTICES` lists `aopalliance` with its Public Domain designation, which satisfies attribution
  trivially — public domain requires none.

## Pros and cons of the options

### Option A — Public Domain exception scoped to `aopalliance` (chosen)

- Good: the least permissive license in the graph is admitted under the most restrictive possible scope.
- Good: no runtime risk; nothing about the dependency graph changes.
- Bad: extends a frozen-spec allowlist, which is a real (if small) widening and the reason this ADR exists.

### Option B — Exclude `aopalliance` from Guice

- Good: the artifact leaves the distributable entirely, so no exception is needed.
- Bad: Guice references `org.aopalliance.intercept.*` on the injector-creation path and publishes no `no_aop`
  variant for 7.0.0 — the likely result is a `NoClassDefFoundError` at first injector construction.
- Bad: unprovable in this change, which has no runtime. The breakage would surface in the composition-root change,
  far from the decision that caused it.

### Option C — Vendor the four interfaces

- Good: removes the report line without touching Guice's behaviour.
- Bad: ships the same public-domain code under a different path — it changes the report, not the licensing position.
- Bad: puts `org.aopalliance.intercept` inside `:api`, the module defined by having no dependencies and no framework.

### Option D — Replace Guice

- Good: no exception, no vendoring.
- Bad: contradicts the decided stack, six `module-info.java` files, and `02_Architecture/10_DI_AND_LIFECYCLE.md`.
- Bad: wildly disproportionate to a permissive transitive that imposes no obligations.

## Links

- Spec clause extended: `docs/specification/05_Dependencies/03_LICENSING.md#permissive-only-gate` (allowlist);
  `#build-tool-exception` and `#license-gate-tool` are unaffected
- Policy files: `config/license/allowed-licenses.json`, `config/license/README.md`
- Changes: `bootstrap-gradle-and-quality-toolchain` (tasks 3.3, 3.4, 3.5; CI wiring in 7.3)
- Related: `docs/adr/ADR-0016-openspec-delivery-tracking.md` (a spec gap is remedied by an ADR, never a spec edit)
