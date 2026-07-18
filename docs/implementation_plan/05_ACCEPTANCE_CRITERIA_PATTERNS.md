**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/02_STORY_FORMAT.md`, `docs/implementation_plan/03_TRACEABILITY.md`,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/mockups/ui-mockup.html`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`

# Acceptance-Criteria Patterns

Every acceptance criterion in a story (`### STORY-NNN-AC-N`) is written to one of six patterns, **P1–P6**. A pattern
makes the AC concrete, testable, and unambiguous, and tells the tester how to prove it. Choose the pattern that matches
the behaviour; a single story mixes patterns as needed.

## writing-rules {#writing-rules}

These rules apply to every AC regardless of pattern:

- **One value per AC.** An AC asserts exactly one observable outcome. If you need "and", split into two ACs.
- **No implementation detail.** State the observable behaviour, contract, or visual result — not the class, algorithm,
  or library. "Returns a `Result` with `ErrorCode.DRM_PROTECTED`" is a contract (allowed); "calls `DrmSniffer.check()`"
  is implementation (not allowed).
- **Testable as written.** Each AC maps to at least one proving test carrying `// Proves: STORY-NNN-AC-N` on its first
  line (see `docs/implementation_plan/03_TRACEABILITY.md`).
- **Every `EC-` is covered.** Every edge case in a story's `edge_cases` front-matter appears in some AC's proving test —
  usually a P5 guard/negative AC paired with the `EC-` id.
- **Deterministic.** No AC depends on wall-clock timing, network availability, or model output text. LLM interactions
  are proven against WireMock stubs, not a live model.
- **The clean gate is an implicit AC of every story.** Beyond the story's explicit ACs, every story must leave
  `./gradlew clean build check spotlessCheck` **green across the whole project** — build, format, lint, ArchUnit, and
  tests all passing, zero findings. There is **no "pre-existing failure" exemption**: a mechanical check that is red
  anywhere is fixed as part of the story (or a cited prerequisite), never carried forward. This is not written out as a
  separate `### STORY-NNN-AC` line but is enforced by the Definition of Done
  (`06_DEFINITION_OF_DONE.md#per-story-checklist`) on every story.

## p1-given-when-then {#p1-given-when-then}

**Behaviour.** Use for functional behaviour with clear preconditions and outcome.

> **Given** `<initial state / input>` **When** `<action>` **Then** `<single observable outcome>`.

Example: *Given an EPUB whose OPF declares `en` but whose body text is Ukrainian, When the file is imported, Then the
detected source language is `uk` and a language-mismatch state is surfaced.*
Proof: unit/integration test asserting the returned detection.

## p2-state-transition {#p2-state-transition}

**State machine.** Use for lifecycle/status changes, especially the segment status machine and job states.

> **From** `<state>` **on** `<event>` **the entity moves to** `<state>` (and no other transition is permitted).

Example: *From `PENDING` on a passing QA gate with score ≥ τ, a segment moves to `ACCEPTED`; from `FLAGGED` on an
accepted edit it moves to `REVISED`.*
Proof: test drives the event and asserts the resulting state; a negative test asserts illegal transitions are rejected.
Grounded in `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`.

## p3-contract-api-shape {#p3-contract-api-shape}

**Contract / API shape.** Use for the `Result` envelope, `AppError`/`ErrorCode`, DTO/record shapes, and port method
contracts.

> **Calling** `<port method>` **with** `<input>` **yields a `Result` whose** `data` **is** `<shape>` **or whose**
> `error` **is** `ErrorCode.<CODE>` **with safe details** `<allowlisted fields>`.

Example: *Calling `Provider.verify` on an unreachable endpoint yields a `Result` with
`error.code == ErrorCode.PROVIDER_UNREACHABLE` and no secret in `error.details`.*
Proof: unit test asserting the envelope and error code; assert the safe-details allowlist (see
`docs/specification/02_Architecture/09_ERROR_HANDLING.md`). Records-first: DTOs asserted as records.

## p4-rendering-control-state {#p4-rendering-control-state}

**Rendering / control-state (JavaFX).** Use for UI behaviour — the observable state of a control after an interaction.
This is the JavaFX analogue of a DOM assertion.

> **On** `<screen/state>` **after** `<user action>` **the control** `<id>` **is**
> `<enabled/disabled/visible/selected/text=…>`.

Example: *On the Translating screen in the `paused` state, the Resume button is enabled and the Pause button is
disabled.*
Proof: TestFX (headless via Monocle) driving the interaction and asserting control state via `lookup(...)`. Runs in
`:ui/src/test/java/...`.

## p5-guard-negative {#p5-guard-negative}

**Guard / negative — pair with an `EC-` id.** Use for rejection paths, refusals, and edge cases. Every `EC-` an area
declares should surface here.

> **Given** `<adverse input / edge condition EC-XXX-N>` **When** `<action>` **Then**
> `<safe rejection / defined fallback>` and no partial/corrupt state remains.

Example: *Given a DRM-protected EPUB (EC-DRM-1), When imported, Then the app refuses with `ErrorCode.DRM_PROTECTED`,
shows the DRM-blocked state, and imports nothing.*
Proof: test carrying `// Proves: STORY-NNN-AC-N` and (for the edge case) `// Proves: EC-DRM-1`. The `EC-` marker is what
closes edge-case coverage in `traceCheck` (check #7).

## p6-visual-reference {#p6-visual-reference}

**Visual reference (UI only).** Use for screens/states/dialogs whose acceptance is "matches the mockup". The mockup
`docs/specification/mockups/ui-mockup.html` is the binding visual source of truth.

> **The** `<screen/state/dialog>` **in** `<light|dark>` **theme matches** `docs/specification/mockups/ui-mockup.html`
> **for** `<named screen/state>`:
> `<the specific structural facts being asserted — layout regions present, controls, tokens applied>`.

Example: *The Import screen in the `language-mismatch` state matches the mockup: the detected-file card shows format,
detected language, and a warning banner; charcoal sidebar and cognac primary action tokens applied.*
Proof: a P6 AC is backed by a **TestFX/Monocle screenshot or control-tree assertion where practical** — assert the
presence and token-driven styling of the named regions/controls rather than pixel-diffing. Cite the mockup screen/state
and the theme. Ground the field/control list in `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`.

## choosing-a-pattern {#choosing-a-pattern}

| If the AC is about…                        | Use |
|--------------------------------------------|-----|
| Functional behaviour with input→outcome    | P1  |
| A status/lifecycle change                  | P2  |
| A `Result`/`AppError`/DTO/port contract    | P3  |
| A JavaFX control's state after interaction | P4  |
| A rejection, refusal, or `EC-` edge case   | P5  |
| A screen/state/dialog matching the mockup  | P6  |

## anti-patterns {#anti-patterns}

Reject an AC that: bundles multiple outcomes with "and/or"; names a class/method/library; depends on live network or
model text; asserts timing; restates a requirement without an observable check; or (for UI) claims "looks like the
mockup" without naming the screen/state and the concrete regions/controls asserted.
