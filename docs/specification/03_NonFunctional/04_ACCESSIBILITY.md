**Status:** Informative (non-normative)
**Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18 **Cross-references:**
`docs/specification/03_NonFunctional/01_QUALITY_ATTRIBUTES.md`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`, `docs/specification/mockups/ui-mockup.html`

# Accessibility

**This entire document is non-normative, best-effort guidance.** The project has no accessibility requirements unless
explicitly specified elsewhere; nothing here is a release or merge gate, and no `NFR-A11Y-*` item blocks a story, phase,
or release. The guidance below (oriented at WCAG 2.1 Level AA) describes good practice for realizing the mockup's
screens with JavaFX's platform accessibility API, and teams are encouraged — not required — to follow it. The
`NFR-A11Y-*` ids are retained for reference only and are **advisory**.

## requirements {#requirements}

_Advisory guidance, not requirements in the gating sense:_

| ID          | Requirement                                                                                                                                                                                                                                                               |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-A11Y-01 | Aim for WCAG 2.1 AA across the screens and dialogs enumerated in `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#screens` (best-effort).                                                                                                                                    |
| NFR-A11Y-02 | **Contrast** — text and meaningful UI have ≥ 4.5:1 (normal) / 3:1 (large text, UI components) in both light and dark themes. The token palette is chosen/verified to pass in both themes; status colors are distinguishable, and status is never conveyed by color alone. |
| NFR-A11Y-03 | **Keyboard** — every action is reachable and operable by keyboard: logical tab order, visible focus indicator, no keyboard traps, mnemonics/accelerators for primary actions, Esc closes dialogs.                                                                         |
| NFR-A11Y-04 | **Screen reader** — controls expose an accessible name/role/value via `Node.accessibleText`/`accessibleRole`/`accessibleHelp`; icon-only buttons (Ikonli) carry an accessible name; tables/tree expose row/column semantics.                                              |
| NFR-A11Y-05 | **Text sizing** — the layout tolerates OS text scaling without clipping or loss of function up to 200%.                                                                                                                                                                   |
| NFR-A11Y-06 | **Non-text cues** — errors/warnings pair color with an icon and text (`09_ERROR_HANDLING.md`); the flagged status uses shape/label, not hue alone.                                                                                                                        |
| NFR-A11Y-07 | **Focus management** — opening a dialog moves focus into it and returns focus to the invoker on close; toasts do not steal focus.                                                                                                                                         |
| NFR-A11Y-08 | **Labels & instructions** — every input has an associated label; validation errors are announced and programmatically associated with their field.                                                                                                                        |
| NFR-A11Y-09 | **Motion** — no essential information is conveyed only by animation; progress has a textual/percentage equivalent.                                                                                                                                                        |

## color-and-theming {#color-and-theming}

Both light and dark themes derive from one token set (`07_UI_ARCHITECTURE_JAVAFX.md#theming`). Contrast is checked on
what each conformance screen really paints, in both themes (`09_THEMING.md#contrast`, FR-THEME-11): text 4.5:1, icons and
control edges 3:1, informative text at least 12px. Unlike the other checks below, this one is a **gate** — a token change
that breaks a ratio fails the `:ui` tests. Selected rows, chips, and draft/warm surfaces (Sand Dollar) are covered, since
the check reads the surface behind each piece of text.

## keyboard-and-focus {#keyboard-and-focus}

- Primary flows (Import → Brief → Translate → Review → Export) are fully keyboard-navigable: on each workflow screen
  Tab reaches every operable control and comes back round (`FocusTraversalTest`). Tab stops once in a group of
  toggles (a segmented picker, the filter chips) and the arrow keys move within it; Tab leaves a text area instead of
  typing a tab character (no text BookLoom asks for needs one), so a note field is never a keyboard trap.
- Keyboard focus is a 2px ring in the `focus` role (`nav-focus` on the dark navigation column and title bar), drawn
  1px outside the control so it holds 3:1 against the surface around it whatever the control's own fill, and shown only
  when focus came from the keyboard (a field being typed in always shows it); a focused row of a list, table or tree is
  framed inside its edges. Pressed quiet buttons darken; an unavailable input is dimmed to 0.45 like a button.
- Review actions (accept/edit/retry/retry-with-note/skip) have accelerators so a reviewer can clear the flagged queue
  without the mouse.
- The side-by-side compare panes are reachable and readable in sequence by a screen reader.

## verification {#verification}

Where teams choose to check this guidance, useful (advisory, **non-blocking**) checks are: TestFX assertions on
accessible name/role for key controls, a contrast check against the token palette in both themes, and a keyboard-only
traversal test for each primary screen. Findings map to `NFR-A11Y-*` for reference and may be surfaced during the
packaging/release advisory a11y pass (`04_Build_and_Release`); they do not gate a merge or a release.
