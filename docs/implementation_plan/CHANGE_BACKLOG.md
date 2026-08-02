**Status:** Final **Owner:** architect **Audience:** anyone picking up the next unit of work **Last Updated:**
2026-08-02 **Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/implementation_plan/07_ROADMAP.md`,
`docs/implementation_plan/README.md#end-to-end-flow`, `openspec/config.yaml`

# Change Backlog

The ordered list of OpenSpec changes that build BookLoom, grouped into the five stages of ADR-0017.

> **This is names and order only — deliberately unplanned.** No entry below has a proposal, specs, or tasks yet, and
> none should be written ahead of time. Each change gets its real artifacts from `/opsx:propose` **when its turn
> comes**, against the codebase as it actually is at that moment rather than as it was imagined months earlier. The
> "covers" column names the material a proposal should draw on; it is not a scope contract.

Exactly one change has been authored: **change 1**, `bootstrap-gradle-and-quality-toolchain`.

## how-to-use-this {#how-to-use-this}

1. Take the lowest-numbered change whose stage dependencies are satisfied (see
   `07_ROADMAP.md#stages` — A before everything; B and B′ in parallel after A; C after B; D after B′ and C; E after D).
2. Run `/opsx:propose` for it. Read the generated artifacts critically — `openspec/config.yaml` steers them toward the
   R1–R6 standard, it does not guarantee them.
3. `openspec validate <change> --strict`, then `/opsx:apply`, then the Definition of Done, then `/opsx:archive`.
4. At each stage boundary, run `bash scripts/fr-coverage.sh`. Advisory only.

Capability names must come from the 16-name map in `openspec/config.yaml` so the `FR-*` join key holds exactly. **NEW**
means the change creates `openspec/specs/<capability>/`; **MOD** means it adds to or changes an existing one; check
`openspec/specs/` before deciding which.

---

## Stage A — Infrastructure

Nothing else starts until **both** are archived. Both are pure infrastructure with no user-observable behaviour, so both
set `skip_specs: true`.

| # | Change | Capability | Exit gate |
|---|---|---|---|
| 1 | `bootstrap-gradle-and-quality-toolchain` | *(skip_specs)* | `./gradlew clean build check spotlessCheck` green on eight empty modules |
| 2 | `bootstrap-app-launch-and-empty-window` | *(skip_specs)* | `./gradlew :app:run` opens a blank themed JavaFX window; `:app:collectDist` stages a distributable |

**Change 1 — authored.** See `openspec/changes/bootstrap-gradle-and-quality-toolchain/`. Covers the Gradle multi-module
build and eight JPMS modules, the version catalog and dependency locking, the `build-logic` convention plugins
(Spotless/Palantir, Lombok + Error Prone/NullAway, Checkstyle, SpotBugs+FindSecBugs), the eight-rule ArchUnit boundary
suite, test conventions with the `liveLocal`/`promptEval`/`visual` tag exclusions and the coverage gate, Lefthook, the
CI quality workflow with the license and SCA gates, and `.editorconfig`/`.gitattributes`.

**Change 2 — covers:** the app-paths resolver (per-OS data/log dirs, dev/prod `-Dev` separation, DD-39/ADR-0015); the
programmatic Logback bootstrap publishing the log dir before the first `LoggerFactory.getLogger` call; the
build-generated version resource (DD-50); the `Result`/`AppError`/`ErrorCode` envelope with the safe-details allowlist
(**seam F2**); the `Launcher` + Guice composition root + single-instance `bookloom.lock`; the packaging scripts and
launch smoke; and a minimal JavaFX `Application` with a Scene-level token stylesheet. It absorbs the former STORY-002,
003, 006, 007, 008, and 013.

---

## Stage B — Document round-trip core

Depends on Stage A. The goal is narrow and demonstrable: **open an existing file → parse → reassemble → save it back**,
canonical-equal.

| # | Change | Capability |
|---|---|---|
| 3 | `add-document-skeleton-and-epub-roundtrip` | `document-round-trip` **NEW** |
| 4 | `add-fb2-md-txt-roundtrip` | `document-round-trip` MOD |
| 5 | `add-inline-masking-and-placeholder-gate` | `document-round-trip` MOD |

Change 3 establishes **seam F1** (skeleton + ordered segment list; text nodes the only mutable slots). Change 5
establishes the placeholder-multiset hard gate that no confidence score or judge verdict can override.

---

## Stage B′ — UI component library

**Runs in parallel with Stage B**; depends only on Stage A. Reusable controls built against the mockup's own
**"Component library"** screen — widget-level, composed into no application screen yet. Screen composition is Stage D.

| # | Change | Capability |
|---|---|---|
| 6 | `add-theming-token-system` | `theming` **NEW** |
| 7 | `add-ui-component-library` | `app-shell` **NEW** *(or `skip_specs` — decide at propose time)* |

Change 7's capability question is genuinely open: a library of controls composed into no screen may have no
user-observable behaviour to specify, in which case `skip_specs: true` is honest and inventing requirements is not.
Decide when proposing, not now.

---

## Stage C — Engine internals

Depends on Stage B. Storage (8–9) and the provider stack (10–11) are independent of each other; both precede the
pipeline (12+).

| # | Change | Capability |
|---|---|---|
| 8 | `add-local-storage` | `local-storage` **NEW** |
| 9 | `add-resume-checkpoints` | `resume` **NEW** |
| 10 | `add-llm-provider-abstraction` | `llm-provider` **NEW** |
| 11 | `add-inference-gate-and-response-contract` | `inference` **NEW** |
| 12 | `add-chunking-and-context-assembly` | `translation-pipeline` **NEW** |
| 13 | `add-translation-draft-loop` | `translation-pipeline` MOD |
| 14 | `add-deterministic-qa-gate` | `quality-gates` **NEW** |
| 15 | `add-consistency-stack` | `translation-pipeline` MOD · `glossary` **NEW** |
| 16 | `add-judge-and-self-heal` | `quality-gates` MOD |

Seams established here: **F6/F7** (checkpoints, settings KV) by 8–9; **F3** (provider abstraction) by 10; **F4**
(single-flight gate) by 11; **F5** (context-package assembler) by 12.

---

## Stage D — Composition

Depends on Stage B′ **and** Stage C. Screens assembled from Stage B′ widgets and wired to the Stage C engine.

| # | Change | Capability |
|---|---|---|
| 17 | `add-app-shell-and-navigation` | `app-shell` MOD |
| 18 | `add-localization-infrastructure` | `localization` **NEW** |
| 19 | `add-import-and-brief-screens` | `book-import` **NEW** · `book-brief` **NEW** |
| 20 | `add-structure-and-glossary-screens` | `glossary` MOD |
| 21 | `add-translating-dashboard` | `app-shell` MOD |
| 22 | `add-review-queue` | `review-queue` **NEW** |
| 23 | `add-export-flow` | `export` **NEW** |
| 24 | `add-settings-and-provider-ui` | `settings` **NEW** · `llm-provider` MOD |
| 25 | `add-notifications-and-error-surfacing` | `notifications` **NEW** |

Change 17 establishes **seam F8** (the observable state mirror). Change 18 lands **before** the screens deliberately, so
every screen is built against bundle keys from the start rather than retrofitted (`07_ROADMAP.md#execution-notes`).

---

## Stage E — Whole-book quality & release

| # | Change | Capability |
|---|---|---|
| 26 | `add-backward-revision-sweep` | `translation-pipeline` MOD |
| 27 | `add-packaging-and-release` | *(skip_specs)* |
| 28 | `complete-ukrainian-localization` | `localization` MOD |

---

## capability-map {#capability-map}

The 16 capabilities, mapped from the frozen FR areas so the `FR-*` join key holds exactly. This table is duplicated in
`openspec/config.yaml`, which is what actually steers generation; keep them in sync.

| Capability             | FR area                        | Introduced by |
|------------------------|--------------------------------|---------------|
| `document-round-trip`  | fr-doc                         | change 3      |
| `theming`              | fr-ui (FR-UI-05) + fr-theme    | change 6      |
| `app-shell`            | fr-ui (FR-UI-01..04, FR-UI-09) | change 7 or 17|
| `local-storage`        | fr-persist                     | change 8      |
| `resume`               | fr-resume                      | change 9      |
| `llm-provider`         | fr-prov, fr-model              | change 10     |
| `inference`            | fr-infer                       | change 11     |
| `translation-pipeline` | fr-algo                        | change 12     |
| `quality-gates`        | fr-qa                          | change 14     |
| `glossary`             | fr-gloss                       | change 15     |
| `localization`         | fr-ui (FR-UI-06/08) + fr-i18n  | change 18     |
| `book-import`          | fr-import                      | change 19     |
| `book-brief`           | fr-brief                       | change 19     |
| `review-queue`         | fr-review                      | change 22     |
| `export`               | fr-export                      | change 23     |
| `settings`             | fr-settings                    | change 24     |
| `notifications`        | fr-notif                       | change 25     |

Introducing a capability outside this list requires an ADR first — the map exists so `scripts/fr-coverage.sh` means
something.

**ADR-0018 amendments.** `FR-THEME-01..10` (`01_Product/09_THEMING.md`) belongs to `theming`, and `FR-I18N-01..09`
(`01_Product/10_I18N_AND_ACCESSIBILITY.md`) belongs to `localization` — 19 requirements the original sixteen-area map
left unowned. `FR-A11Y-*` and `NFR-A11Y-*` (17 ids) are deliberately **unowned and excluded from coverage**: the
specification declares accessibility advisory and never a merge gate, so it is a review item on `:ui` changes rather
than an implementation obligation. Cite ids in the canonical zero-padded form (`FR-THEME-01`, not `FR-THEME-1`).
