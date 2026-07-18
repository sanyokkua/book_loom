**Status:** Final **Owner:** architect **Audience:** anyone working a session **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/README.md`

# Working Notes

This folder holds **transient, non-binding context and scratch** produced while working a session: investigation notes,
planning scratch, spike write-ups, throwaway checklists, and anything else that helps get a story done but is not part
of the specification or the story record.

## what-belongs-here {#what-belongs-here}

- Investigation notes from reading the codebase or reproducing a problem.
- Planning scratch before `/plan-phase-stories-creation` or `/plan-user-story-implementation` produces the real plan.
- Temporary tables, command output, or reasoning kept for the duration of a session.

## what-does-not {#what-does-not}

- **Not the spec.** The specification under `docs/specification/` is frozen and authoritative; nothing here overrides
  it.
- **Not a story.** Implementable, testable work lives in `docs/stories/` in the story format.
- **Not an ADR.** Architecturally significant decisions go in `docs/adr/`.
- **Not traceability.** `docs/traceability.yaml` is generated; nothing here feeds `traceCheck`.

## rules {#rules}

- Content here is **non-binding** and may be **cleaned up freely** at any time. Do not cite a working note as authority.
- Keep it in-repo and self-contained; no references to anything outside `tranlator_app/`.
- If a note turns out to matter, promote it into the proper artifact (a story, an ADR, or a spec revision) — do not
  leave load-bearing decisions in scratch.
