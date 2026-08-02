**Status:** Final **Owner:** architect **Audience:** anyone authoring or reviewing an OpenSpec change **Last Updated:**
2026-08-02 **Cross-references:** `docs/adr/ADR-0016-openspec-delivery-tracking.md`,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/mockups/ui-mockup.html`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`

# Scenario Patterns

Every `#### Scenario:` under a requirement in an OpenSpec change's `specs/<capability>/spec.md` is written to one of six
patterns, **P1–P6**. A pattern makes the scenario concrete, testable, and unambiguous, and tells whoever writes the test
how to prove it. Choose the pattern that matches the behaviour; one requirement mixes patterns as needed.

These patterns predate the move to OpenSpec (ADR-0016) — they were acceptance-criteria patterns for story files. They
survived the migration unchanged in substance because the six shapes are good regardless of which artifact holds them.
What changed is the container: a pattern now shapes a **scenario under an EARS requirement**, not an
`### STORY-NNN-AC-N` heading, and the proving test carries `// Covers: FR-*` rather than `// Proves: STORY-NNN-AC-N`.

## how-a-pattern-fits-a-requirement {#how-a-pattern-fits}

A requirement is written in **EARS** (ADR-0016 R1) and states one obligation. Its scenarios are the concrete cases that
prove the obligation holds — and each scenario uses a pattern:

```markdown
### Requirement: Placeholder multiset is a hard gate

WHEN a translated chunk returns from the model, the system SHALL compare the multiset of
`⟦gN⟧` placeholders in the target text against the multiset in the masked source text.

IF the two multisets differ in any way — a placeholder missing, added, or duplicated — THEN
the system SHALL fail the chunk with a `validation` error, and SHALL NOT unmask, repair, or
silently reconcile the difference.

**Source:** FR-DOC-05, FR-QA-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`).
In plain words: the model may rewrite the words, never the inline structure. This is the one
gate that no confidence score and no judge verdict can override.

#### Scenario: Model drops a placeholder                       ← P5 (guard / negative)
- **WHEN** the masked source contained `⟦g1⟧` and `⟦g2⟧` but the target contains only `⟦g1⟧`
- **THEN** the chunk fails with `ErrorCode.validation` and no unmasking occurs

#### Scenario: Model reorders placeholders                     ← P1 (behaviour)
- **WHEN** the target contains `⟦g2⟧⟦g1⟧` and the source contained `⟦g1⟧⟦g2⟧`
- **THEN** the multiset matches, the gate passes, and unmasking proceeds
```

The second scenario resolves an ambiguity the one-line FR leaves open — a multiset is order-insensitive. Surfacing
those is the point of the rephrasing pass, not overhead.

## writing-rules {#writing-rules}

These apply to every scenario regardless of pattern:

- **One outcome per scenario.** A scenario asserts exactly one observable outcome. If you need "and", split it.
- **Concrete values, never abstractions** (ADR-0016 R3). Not "invalid input" → `⟦g1⟧⟦g2⟧` vs `⟦g1⟧`. Not "an error" →
  `ErrorCode.validation`. Not "a large file" → a stated size. If you cannot name a concrete value, the requirement above
  it is still too vague.
- **No implementation detail.** State the observable behaviour, contract, or visual result — not the class, algorithm,
  or library. "Returns a `Result` with `ErrorCode.DRM_PROTECTED`" is a contract (allowed); "calls `DrmSniffer.check()`"
  is implementation (belongs in `design.md`).
- **Testable as written.** Each scenario maps to at least one test carrying `// Covers: FR-*` plus a one-line EARS
  restatement of the obligation (ADR-0016 R5).
- **Every `EC-` is covered.** Every enumerated edge case the change touches appears in some scenario — usually a P5
  guard/negative one naming the `EC-` id.
- **Deterministic.** No scenario depends on wall-clock timing, network availability, or model output text. LLM
  interactions are proven against WireMock stubs, never a live model.
- **Exactly four hashes.** `#### Scenario: <name>`. Three hashes fail silently in OpenSpec's parser.
- **The clean gate is implicit everywhere.** Beyond any scenario, every change must leave
  `./gradlew clean build check spotlessCheck` **green across the whole project** — zero findings, no "pre-existing
  failure" exemption. It is never written out as a scenario; it is enforced by
  `06_DEFINITION_OF_DONE.md#per-change-checklist`.

## p1-given-when-then {#p1-given-when-then}

**Behaviour.** Functional behaviour with clear preconditions and outcome.

> **GIVEN** `<initial state / input>` **WHEN** `<action>` **THEN** `<single observable outcome>`.

Example: *GIVEN an EPUB whose OPF declares `en` but whose body text is Ukrainian, WHEN the file is imported, THEN the
detected source language is `uk` and a language-mismatch state is surfaced.*

Proof: unit/integration test asserting the returned detection.

## p2-state-transition {#p2-state-transition}

**State machine.** Lifecycle/status changes — especially the segment status machine and job states.

> **FROM** `<state>` **ON** `<event>` **the entity moves to** `<state>` (and no other transition is permitted).

Example: *FROM `PENDING` ON a passing QA gate with score ≥ τ, a segment moves to `ACCEPTED`; FROM `FLAGGED` ON an
accepted edit it moves to `REVISED`.*

Proof: a test drives the event and asserts the resulting state; a negative test asserts illegal transitions are
rejected. Grounded in `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`.

## p3-contract-api-shape {#p3-contract-api-shape}

**Contract / API shape.** The `Result` envelope, `AppError`/`ErrorCode`, DTO/record shapes, port method contracts.

> **Calling** `<port method>` **with** `<input>` **yields a `Result` whose** `data` **is** `<shape>` **or whose**
> `error` **is** `ErrorCode.<CODE>` **with safe details** `<allowlisted fields>`.

Example: *Calling `Provider.verify` on an unreachable endpoint yields a `Result` with
`error.code == ErrorCode.PROVIDER_UNREACHABLE` and no secret in `error.details`.*

Proof: unit test asserting the envelope and error code, plus the safe-details allowlist
(`docs/specification/02_Architecture/09_ERROR_HANDLING.md`). Records-first: DTOs asserted as records.

## p4-rendering-control-state {#p4-rendering-control-state}

**Rendering / control-state (JavaFX).** The observable state of a control after an interaction — the JavaFX analogue of
a DOM assertion.

> **ON** `<screen/state>` **AFTER** `<user action>` **the control** `<id>` **is**
> `<enabled/disabled/visible/selected/text=…>`.

Example: *ON the Translating screen in the `paused` state, the Resume button is enabled and the Pause button is
disabled.*

Proof: TestFX (headless via Monocle) driving the interaction and asserting control state via `lookup(...)`, in
`:ui/src/test/java/...`. "Wired" is asserted **behaviourally** — drive the viewmodel property and assert the node
updates, fire the control and assert the command was invoked — never by inspecting bindings.

## p5-guard-negative {#p5-guard-negative}

**Guard / negative — pair with an `EC-` id.** Rejection paths, refusals, and edge cases. Every `EC-` an area declares
should surface here.

> **GIVEN** `<adverse input / edge condition EC-XXX-N>` **WHEN** `<action>` **THEN**
> `<safe rejection / defined fallback>` and no partial/corrupt state remains.

Example: *GIVEN a DRM-protected EPUB (EC-DRM-1), WHEN imported, THEN the app refuses with `ErrorCode.DRM_PROTECTED`,
shows the DRM-blocked state, and imports nothing.*

Proof: a test whose marker names both the requirement's FR id and the edge case, e.g.
`// Covers: FR-IMPORT-03, EC-DRM-1 — IF an imported EPUB is DRM-protected, THEN import refuses with
ErrorCode.DRM_PROTECTED and persists nothing.`

## p6-visual-reference {#p6-visual-reference}

**Visual reference (UI only).** Screens/states/dialogs whose acceptance is "matches the mockup".
`docs/specification/mockups/ui-mockup.html` is the binding visual source of truth.

> **The** `<screen/state/dialog>` **in** `<light|dark>` **theme matches** `docs/specification/mockups/ui-mockup.html`
> **for** `<named screen/state>`: `<the specific structural facts asserted — layout regions, controls, tokens applied>`.

Example: *The Import screen in the `language-mismatch` state matches the mockup: the detected-file card shows format,
detected language, and a warning banner; charcoal sidebar and cognac primary-action tokens applied.*

Proof: a TestFX/Monocle screenshot or control-tree assertion where practical — assert the presence and token-driven
styling of the named regions/controls rather than pixel-diffing. Cite the mockup screen/state and the theme. Ground the
field/control list in `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`.

## choosing-a-pattern {#choosing-a-pattern}

| If the scenario is about…                  | Use |
|--------------------------------------------|-----|
| Functional behaviour with input→outcome    | P1  |
| A status/lifecycle change                  | P2  |
| A `Result`/`AppError`/DTO/port contract    | P3  |
| A JavaFX control's state after interaction | P4  |
| A rejection, refusal, or `EC-` edge case   | P5  |
| A screen/state/dialog matching the mockup  | P6  |

## anti-patterns {#anti-patterns}

Reject a scenario that: bundles multiple outcomes with "and/or"; names a class/method/library; uses an abstraction
("invalid input", "an error") where a concrete value belongs; depends on live network or model text; asserts timing;
restates the requirement without an observable check; uses three hashes instead of four; or (for UI) claims "looks like
the mockup" without naming the screen/state and the concrete regions/controls asserted.
