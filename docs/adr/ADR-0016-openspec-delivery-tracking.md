# ADR-0016 — Track delivery with OpenSpec changes; retire stories and generated traceability

**Status:** accepted; R5 (test markers) and R6 (coverage grep) superseded by ADR-0032 on 2026-09-11 **Date:** 2026-08-02 **Deciders:** architect

## Context and problem statement

BookLoom carries **two** delivery-tracking systems at once. The homegrown one is
`docs/implementation_plan/02_STORY_FORMAT.md` + `03_TRACEABILITY.md` + `docs/stories/**`, with a generated
`docs/traceability.yaml` produced by `./gradlew trace` and validated by `./gradlew traceCheck`, joined to the code by
`// Proves: STORY-NNN-AC-N` markers in tests. The other is **OpenSpec**, initialized in `openspec/` but empty —
`openspec/specs/` and `openspec/changes/` hold nothing and `config.yaml` is 100% commented out.

Running both doubles the ceremony on every unit of work: the same behaviour has to be written as a story with
front-matter, as an OpenSpec change with requirements, and then joined back through two different id schemes.

The timing is what makes this decidable now rather than expensively later. The repo is **~40 spec files, 15 ADRs, 14
phases, 13 stories — and zero lines of code**: no `settings.gradle.kts`, no `build.gradle.kts`, no `src/`. The `trace`
and `traceCheck` Gradle tasks **do not exist**; they are `STORY-010`, unwritten. Deleting the machinery today costs one
documentation pass. Building it first and deleting it later costs the JavaParser generator, the FR-ID index, the
canonical fingerprint, their tests, and a permanent line in every future change's done-gate.

There is a second, deeper problem the story format has independent of duplication: its artifacts are **not readable on
their own**. A story's front-matter reads `spec_clauses: [09_ERROR_HANDLING.md#result-envelope, DD-14]` and
`acceptance_criteria: [STORY-002-AC-1, STORY-002-AC-2]`, and a task reads `- [ ] 2.1 STORY-002-AC-2`. Every one of those
is a pointer with no payload — the reader (human or agent) must open two or three other files before they know what the
work actually is. That indirection is the main tax, and it is not fixed by picking either tracking system; it has to be
fixed by an authoring standard.

The **specification is frozen** and is load-bearing on exactly this: `04_CI_CD.md`, `02_QUALITY_GATES.md`,
`06_TESTING_STRATEGY.md`, `01_BUILD_AND_TOOLING.md`, and `06_IMPLEMENTATION_STAGES.md` each mandate `traceCheck` or the
`// Proves:` convention. Per the project's own rule (`AGENTS.md`, `04_ADR_FORMAT.md#when-to-write-one`), a genuine gap or
deviation becomes an ADR, never a spec edit. Hence this record.

## Decision drivers

- **One tracking system.** Two is strictly worse than either one alone.
- **Delete before building.** `trace`/`traceCheck` are unimplemented; the window to remove them for free closes the
  moment `STORY-010` is executed.
- **Self-contained artifacts.** A requirement, a scenario, and a task must each be understandable without opening
  another file. Ids are citations, never payloads.
- **A stable join key.** Story ids are invented per-story and churn; `FR-*`/`NFR-*`/`DD-*` ids live in a frozen catalog
  and are permanent by contract.
- **Coverage without a build task.** Knowing which requirements are not yet built should not require a Gradle plugin,
  a generated YAML file, and a fingerprint.
- **The frozen spec stays frozen.** The deviation is recorded here, not patched into `docs/specification/**`.

## Considered options

- **Option A — Keep stories + traceability, delete the empty `openspec/` tree.**
- **Option B — OpenSpec changes replace stories; generated traceability is never built; an explicit authoring standard
  (R1–R6) makes every artifact self-contained.**
- **Option C — Keep both: OpenSpec for planning, stories + `traceCheck` for the audit trail.**

## Decision outcome

Chosen: **Option B.**

**1. `openspec/changes/<name>/` is the unit of work.** It replaces the story file. A change carries `proposal.md`
(why/what/capabilities/impact), optional `design.md`, `specs/<capability>/spec.md` (the requirements), and `tasks.md`
(the implementation checklist — what the story's task list used to be). The loop is
`backlog entry → /opsx:propose → /opsx:apply → /opsx:archive`.

**2. `docs/stories/**`, `02_STORY_FORMAT.md`, and `03_TRACEABILITY.md` are deleted.** The 13 existing stories are
absorbed into the first two changes' `tasks.md` (see ADR-0017 and `docs/implementation_plan/CHANGE_BACKLOG.md`);
`STORY-010` (the trace tooling itself) is dropped rather than converted.

**3. `./gradlew trace` and `./gradlew traceCheck` are never implemented**, and `docs/traceability.yaml` is deleted.
Neither task appears in the build, in CI, or in any Definition of Done.

**4. `// Proves: STORY-NNN-AC-N` is replaced by `// Covers: FR-*` plus a one-line EARS restatement** on the test. The
restatement is what makes the marker readable; the `FR-*` id is what makes it greppable.

**5. `openspec/specs/` starts empty and grows.** Each change `ADDED`s only what it actually implements and
`openspec archive` folds that into the main specs. `openspec/specs/` therefore always means *what is actually built* —
never a wish list. The frozen `docs/specification/**` remains the full-intent catalog; the two are deliberately
different things.

**6. Coverage is a grep** (`scripts/fr-coverage.sh`), advisory during build-out and run at stage boundaries — not a
build gate.

### The authoring standard (R1–R6)

This is the substance of the decision, not a footnote. It is enforced through `openspec/config.yaml` rules so that
generated artifacts come out in this shape by default.

- **R1 — Requirements are written in EARS, not copied from the FR table.** Patterns:
  `The <system> SHALL <response>` (ubiquitous) · `WHEN <trigger>, the <system> SHALL <response>` (event) ·
  `WHILE <state>, …` (state) · `IF <condition>, THEN …` (unwanted) · `WHERE <feature>, …` (optional). This fits
  OpenSpec natively — its scenario format is already `WHEN`/`THEN` and it mandates SHALL/MUST. A dense FR
  **decomposes into several atomic EARS requirements**: `FR-DOC-04` — one five-line sentence covering inline tags,
  locked terms, URLs, selective numerals, code, MathML and anchors — becomes three or four. That decomposition is the
  value-add, and the shared `FR-DOC-04` citation is what keeps coverage checkable across the split.
- **R2 — Every requirement carries a `Source` block in plain words.** The `FR-*` ids and their spec anchors, followed
  by an "In plain words:" gloss stating what the requirement means and why it matters. The citation locates; the gloss
  explains.
- **R3 — Scenarios use concrete values, never abstractions.** Not "invalid input" → `⟦g1⟧⟦g2⟧` vs `⟦g1⟧`. Not "an
  error" → `ErrorCode.validation`.
- **R4 — Task items state what and why in a sentence.** `- [ ] 2.1 STORY-002-AC-2` becomes a full sentence naming the
  change, its consequence, the owning module, and the spec pointer.
- **R5 — Test markers carry a one-line EARS restatement.**

  ```java
  // Covers: FR-DOC-05 — IF the placeholder multiset of the target differs from the source,
  //         THEN the chunk fails as a validation error with no repair attempt.
  @Test
  void validateChunk_placeholderMissingFromTarget_returnsValidationError() { … }
  ```

- **R6 — Coverage is a grep, not a build task.**

  ```bash
  comm -23 <(grep -ohE 'FR-[A-Z]+-[0-9]+' docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md | sort -u) \
           <(grep -rohE 'FR-[A-Z]+-[0-9]+' openspec/specs/ | sort -u)
  ```

### Superseded spec clauses

The specification stays frozen and is **not edited**. Where the clauses below mandate the retired machinery, this ADR
supersedes them; everything else in those files stands unchanged.

| Frozen clause | What it mandates | Superseded by |
|---|---|---|
| `04_Build_and_Release/04_CI_CD.md:35` — quality-job step 7 | `./gradlew traceCheck` runs as a CI merge-gate step | Step removed. CI runs `spotlessCheck`, `check`, coverage, and the license + SCA gates only. |
| `04_Build_and_Release/02_QUALITY_GATES.md:110` — `#ci-gates` | the CI job runs `traceCheck` | Removed from the CI job. |
| `04_Build_and_Release/02_QUALITY_GATES.md:131` — `#what-fails-where` | row "Traceability orphan / stale record → fails (`traceCheck`)" | Row deleted. Uncovered `FR-*` ids are advisory output from `scripts/fr-coverage.sh`, never a build failure. |
| `04_Build_and_Release/02_QUALITY_GATES.md:134` | "The Definition of Done (per story) requires … plus `traceCheck` with zero orphans before merge." | Reads: the Definition of Done (per change) requires the full CI-equivalent set green. No traceability gate. |
| `04_Build_and_Release/06_TESTING_STRATEGY.md:21` | "Every test that proves an acceptance criterion carries a `// Proves: STORY-NNN-AC-N` marker so `traceCheck` binds it" | Every test that covers a requirement carries `// Covers: FR-*` plus a one-line EARS restatement (R5). Nothing binds it mechanically. |
| `04_Build_and_Release/06_TESTING_STRATEGY.md:302-309` — `#coverage-traceability` | the `// Proves:`/`EC-` convention and `traceCheck` failing on orphans | The JaCoCo ~80% core-module branch threshold in that section **stands**. The traceability half is replaced by R5 + R6. |
| `04_Build_and_Release/01_BUILD_AND_TOOLING.md:133-139` — task table | `trace` and `traceCheck` Gradle tasks, the JavaParser generator, the parsed FR-ID index, the canonical `(story,AC,test)` fingerprint | Both task rows removed. None of that tooling is built. |
| `00_Foundation/06_IMPLEMENTATION_STAGES.md:71` — `#stage-exit-invariants` | "`./gradlew traceCheck` passing with zero orphans" as a stage-exit invariant | Clause struck. The remaining stage-exit invariants — offline (F9), the whole-project clean gate, ArchUnit green, `./gradlew test` green, round-trip golden green, mockup conformance — all stand unchanged. |

Two related clauses are **not** superseded and must keep holding: the whole-project clean gate
(`./gradlew clean build check spotlessCheck`, no "pre-existing" exemption) and the JaCoCo core-module branch threshold.
Those are the real quality gates; traceability was never one.

### Consequences

Positive:

- One tracking system. A unit of work is described once, in one place.
- Artifacts are readable cold. R1–R4 mean a reviewer opening `tasks.md` or a `spec.md` knows what the work is without
  a second file.
- The join key is permanent. `FR-*` ids come from a frozen catalog; story ids never had that property.
- Ambiguities surface. Rewriting a dense FR into atomic EARS requirements with concrete scenarios forces the questions
  a one-line requirement leaves open — e.g. "multiset" turning out to be order-insensitive.
- Zero build machinery to write, test, keep green, or run. The JavaParser generator and fingerprint are never built.
- `openspec/specs/` becomes an honest ledger of built behaviour, useful for onboarding in a way an aspirational catalog
  is not.

Negative:

- **Coverage is no longer mechanically enforced.** Nothing fails a build when a requirement has no test. Mitigated by
  R5 + R6 (the grep) run at stage boundaries, and by the fact that a change's `tasks.md` is reviewed before apply.
- **EARS rephrasing is real work** — a dense FR takes thought to decompose. This is accepted deliberately: the
  decomposition is where ambiguity is found, so the cost buys something.
- Five frozen spec files now have clauses that only read correctly alongside this ADR. Mitigated by the explicit
  clause-by-clause table above.

Neutral:

- `docs/specification/**` stays frozen and authoritative for *intent*; `openspec/specs/` tracks *reality*. Two
  artifacts, two jobs — deliberate, and the `FR-*` citation is the bridge.
- `docs/implementation_plan/phases/**` and `01_MODULE_INVENTORY.md` survive as reference material feeding future
  proposals. `05_ACCEPTANCE_CRITERIA_PATTERNS.md` survives reframed as scenario patterns (P1–P6 are good patterns
  regardless of which system consumes them).

## Pros and cons of the options

### Option A — keep stories, delete OpenSpec

- Good: nothing to relearn; the format is already written down in detail.
- Bad: leaves the indirection problem entirely unsolved (ID-only front-matter, `- [ ] 2.1 STORY-002-AC-2` tasks); still
  requires building `trace`/`traceCheck` (JavaParser + FR-ID index + fingerprint) and keeping it green forever;
  discards a tool that already has proposal/spec/task/archive lifecycle, validation, and agent integration.

### Option B — OpenSpec + R1–R6 (chosen)

- Good: one system; self-contained artifacts by rule; permanent `FR-*` join key; no build machinery; `openspec/specs/`
  is an honest built-behaviour ledger; `openspec validate --strict` catches malformed artifacts for free.
- Bad: coverage becomes advisory; EARS rephrasing is genuine effort; five frozen clauses need this ADR to read
  correctly.

### Option C — keep both

- Good: nothing is lost.
- Bad: the status quo, and the reason this ADR exists. Doubles authoring cost per unit of work, needs two id schemes
  reconciled, and still requires the trace tooling to be built.

## Links

- Design decisions: none directly; this governs the *process* around `DD-*`, not a `DD-*` itself.
- Spec clauses: `docs/specification/04_Build_and_Release/04_CI_CD.md#quality-job`,
  `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#ci-gates`,
  `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#what-fails-where`,
  `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#coverage-traceability`,
  `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#gradle-tasks`,
  `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants`
- Related: ADR-0017 (delivery order under this model)
- Changes: `bootstrap-gradle-and-quality-toolchain` is the first change authored under this ADR;
  `docs/implementation_plan/CHANGE_BACKLOG.md` holds the ordered backlog.
