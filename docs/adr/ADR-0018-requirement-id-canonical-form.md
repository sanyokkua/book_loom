# ADR-0018 — Canonical requirement-id form, and owners for the three unmapped FR families

**Status:** accepted **Date:** 2026-08-02 **Deciders:** architect

## Context and problem statement

ADR-0016 replaced generated traceability with a **grep**: a requirement in `openspec/specs/` cites an `FR-*` id, a test
carries `// Covers: FR-*`, and `scripts/fr-coverage.sh` joins the two against the frozen catalog. The whole mechanism
rests on one assumption — **that a given requirement is written the same way everywhere.** That assumption was never
checked. This ADR checks it.

A full census of `docs/specification/**` (letter-suffix-aware regex `(FR|NFR|EC)-[A-Z0-9]+-[0-9]+[a-z]?`) finds **312
distinct requirement ids**: 203 `FR-*`, 44 `NFR-*`, 65 `EC-*`. Three facts about them matter.

**Fact 1 — two id-number conventions coexist.** The sixteen families in the FR catalog
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md`) are **zero-padded to two digits**: `FR-DOC-01`, `FR-NOTIF-06`. Three
families defined in area documents are **unpadded single-digit**: `FR-THEME-1..10`, `FR-I18N-1..9`, `FR-A11Y-1..8`.
Nothing records which is canonical, so a citation written as `FR-THEME-01` — the form an agent will naturally produce
by analogy with the other sixteen families — silently fails to join.

**Fact 2 — an undocumented second level exists.** `01_Product/11_NOTIFICATIONS_AND_ERRORS.md` refines catalog
requirements into lettered sub-clauses: `FR-NOTIF-03` ("show an error dialog with an expandable technical-details
section") is refined by `FR-NOTIF-3a`, `-3b`, `-3c`. Thirteen such refinements exist across `FR-NOTIF-3a..6c`. They
are useful — they are the concrete, testable form of a one-line catalog entry — but they are undeclared, unpadded, and
a naive `[0-9]+` regex truncates `FR-NOTIF-3a` to `FR-NOTIF-3`, inventing an id that does not exist.

**Fact 3 — three FR families have no capability owner.** The sixteen-capability map in `openspec/config.yaml` and
`CHANGE_BACKLOG.md#capability-map` was built from the sixteen FR-catalog area anchors. It therefore omits
`FR-THEME-*` (10 ids), `FR-I18N-*` (9), and `FR-A11Y-*` (8) — **27 requirements with no owning capability**, and so no
change obliged to implement them and no coverage line reporting them missing.

There is a fourth thing worth recording because it cost real analysis time and will cost it again: **the naive regex
`FR-[A-Z]+-[0-9]+` matches inside `NFR-...`.** Applied to `NFR-PRIV-01` it yields the phantom id `FR-PRIV-01`. During
the investigation that produced this ADR, that single flaw manufactured two false findings — seven non-existent
`FR-PRIV/PERF/REL/MAINT/OFFLINE/PORT/USAB` families, and a non-existent `FR-A11Y-01`-vs-`FR-A11Y-1` collision. Both
evaporated under a boundary-safe regex. Any future tooling that greps these ids must anchor the prefix.

The specification is **frozen**, so none of this can be fixed by editing it. Per `04_ADR_FORMAT.md#when-to-write-one`,
the deviation is recorded here.

## Decision drivers

- **The join key must be robust.** A coverage instrument that misses a citation because someone padded a digit is
  worse than no instrument — it reports false confidence.
- **Normalization must not merge distinct requirements.** Rewriting ids is only safe if no two real ids collapse onto
  one.
- **Unowned requirements are unimplemented requirements.** 27 ids with no capability is exactly the "implemented plan
  missing requirements" failure mode this project has hit before.
- **Do not invent a gate the spec disclaims.** Accessibility is explicitly advisory and never a merge gate
  (`03_NonFunctional/04_ACCESSIBILITY.md`, `.claude/rules/testing.md`). Tracking it in a coverage instrument would
  imply an obligation the specification refuses to make.
- **The frozen spec stays frozen.**

## Considered options

**For the id form:**

- **Option A — Declare the padded form canonical and normalize at comparison time.** Tooling zero-pads both sides
  before joining; both spellings resolve to the same key.
- **Option B — Accept ids verbatim, no normalization.** Cite exactly what the spec wrote.
- **Option C — Rewrite the unpadded ids in the spec.** Rejected on sight: the spec is frozen.

**For the three unmapped families:**

- **Option D — Give all three capability owners.**
- **Option E — Declare all three cross-cutting**, verified by the invariants table and the Definition of Done.
- **Option F — Split by character:** owners for the two that describe a capability's own behaviour, cross-cutting for
  the one the spec itself declares advisory.

## Decision outcome

**Chosen: Option A for the id form, Option F for the families.**

### 1. Canonical form

```
FR-<AREA>-<NN>[<letter>]      NFR-<AREA>-<NN>      EC-<AREA>-<N>
```

`<AREA>` is uppercase alphanumeric (`DOC`, `A11Y`, `I18N`). `<NN>` is **zero-padded to at least two digits**.
`<letter>` is an optional lowercase sub-clause suffix marking a refinement of the parent requirement.

**New citations SHALL use this form.** `FR-THEME-01`, not `FR-THEME-1`.

### 2. Known variants in the frozen spec

These exist and are correct **as the spec wrote them**; the spec is not edited. Tooling normalizes; humans citing them
may write either form.

| Family | Form in the frozen spec | Canonical form | Count | Home |
|---|---|---|---|---|
| `FR-THEME` | `FR-THEME-1` … `FR-THEME-10` | `FR-THEME-01` … `FR-THEME-10` | 10 | `01_Product/09_THEMING.md` |
| `FR-I18N` | `FR-I18N-1` … `FR-I18N-9` | `FR-I18N-01` … `FR-I18N-09` | 9 | `01_Product/10_I18N_AND_ACCESSIBILITY.md` |
| `FR-A11Y` | `FR-A11Y-1` … `FR-A11Y-8` | `FR-A11Y-01` … `FR-A11Y-08` | 8 | `01_Product/10_I18N_AND_ACCESSIBILITY.md` |
| `FR-NOTIF` refinements | `FR-NOTIF-3a` … `FR-NOTIF-6c` | `FR-NOTIF-03a` … `FR-NOTIF-06c` | 13 | `01_Product/11_NOTIFICATIONS_AND_ERRORS.md` |

All sixteen FR-catalog families, all 44 `NFR-*`, and all 65 `EC-*` are already canonical.

### 3. Normalization is provably safe here

Zero-padding merges two ids only if both forms of the same id exist. They do not: normalizing all 312 ids and looking
for duplicates yields **an empty set**. `FR-THEME-1` has no `FR-THEME-01` twin. So normalization repairs nothing that
is currently broken — its value is **forward**: it makes the join immune to an agent padding a digit by analogy, which
is the far likelier future failure.

### 4. Refinements inherit their parent's owner

`FR-NOTIF-03a` belongs to whatever capability owns `FR-NOTIF-03`. A change may cite either level; citing the refinement
is preferred because it is the concrete, testable statement. Coverage counts the parent as claimed when any of its
refinements is claimed.

### 5. Tooling rules

`scripts/fr-coverage.sh` (and any future tool touching these ids) MUST:

- **Anchor the prefix** — `(^|[^A-Z0-9])(FR|NFR|EC)-…` — so `NFR-PRIV-01` never yields `FR-PRIV-01`.
- **Allow digits in the area** — `[A-Z0-9]+`, so `A11Y` and `I18N` are matched.
- **Capture the letter suffix** — `[0-9]+[a-z]?`, so `FR-NOTIF-3a` is not truncated to `FR-NOTIF-3`.
- **Normalize by zero-padding to two digits before comparing**, preserving the suffix.

### 6. Capability owners for the three families

| Family | Decision | Rationale |
|---|---|---|
| `FR-THEME-*` (10) | **Owned by `theming`** | `09_THEMING.md` is the theming capability's own specification — the token catalogue, the palette, the light/dark pairing. These are that capability's requirements under a different area name, nothing more. |
| `FR-I18N-*` (9) | **Owned by `localization`** | Same: `10_I18N_AND_ACCESSIBILITY.md` is where the bundle-parity, ICU-pattern, and locale-selection requirements live, and `localization` is the capability that implements them. |
| `FR-A11Y-*` (8) **and `NFR-A11Y-*` (9)** | **Cross-cutting and advisory — no owner, excluded from coverage** | The specification is explicit that accessibility is advisory and **never a merge gate**, and `03_NonFunctional/04_ACCESSIBILITY.md` states outright that `NFR-A11Y-*` ids are "retained for reference only and are advisory" (see also `.claude/rules/testing.md`). Assigning a capability owner would create an implementation obligation the spec deliberately declines to make, and putting the ids in a coverage report would show a permanent red line for work that is correctly not gated. They become an **advisory review item on `:ui` changes** in the Definition of Done instead. |

This **amends** the sixteen-capability map from ADR-0016 — it does not supersede it. The map stays sixteen capabilities;
two of them simply own one more FR family each.

### Consequences

Positive:

- The join key ADR-0016 depends on is now specified rather than assumed, and is robust to the most likely future
  citation error.
- 19 previously-unowned requirements (`FR-THEME` + `FR-I18N`) gain an owning capability and will appear in
  `--change` coverage for changes 6 and 18.
- The lettered refinement level is documented, so `FR-NOTIF-03a` is citable as the concrete requirement it is instead
  of being silently mangled.
- The `NFR-`-substring trap is recorded, so the next person greps correctly the first time.

Negative:

- Two id spellings remain legal for four families. A reader may see both. Mitigated by the variant table above and by
  normalization making the difference invisible to tooling.
- The two A11Y families (17 ids) are deliberately untracked by any instrument. If accessibility is ever promoted to a
  gate, this decision must be revisited — that would be a new ADR.

Neutral:

- No spec file is edited. No id is renumbered. Existing citations keep working.

## Pros and cons of the options

### Option A — canonical + normalize (chosen)

Good: one stated form for new work; tooling tolerant of both; provably merges nothing. Bad: two spellings remain
readable in the corpus.

### Option B — verbatim, no normalization

Good: zero machinery; ids mean exactly what the spec says. Bad: leaves the join brittle in precisely the way that
produces silent under-reporting — a padded citation of an unpadded id just vanishes.

### Option D — capability owners for all three families

Good: uniform; nothing unowned. Bad: manufactures a gating obligation for accessibility that the frozen spec
explicitly disclaims, and would put a permanently-uncovered family in every coverage report.

### Option E — all three cross-cutting

Good: simple. Bad: wrong for `FR-THEME` and `FR-I18N`, which are ordinary capability requirements that happen to live
in an area doc; declaring them cross-cutting would leave 19 real, gateable requirements with nobody obliged to build
them.

## Links

- Design decisions: none — this governs id hygiene, not a `DD-NN`.
- Spec clauses: `docs/specification/00_Foundation/05_SPEC_INDEX.md#id-conventions`,
  `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/01_Product/09_THEMING.md`,
  `docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md`,
  `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`,
  `docs/specification/03_NonFunctional/04_ACCESSIBILITY.md`
- Related: **amends** ADR-0016 (the capability map and the R6 coverage grep it defines); ADR-0017 (changes 6 and 18
  are the ones that gain requirements from this)
- Changes: none yet — the first affected are change 6 (`add-theming-token-system`) and change 18
  (`add-localization-infrastructure`).
