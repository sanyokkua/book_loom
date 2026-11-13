# ADR-0039 — Report why a book cannot be imported as data, and keep the error vocabulary at fifteen codes

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner (plan approval)

## Context and problem statement

The Import screen must tell a DRM-protected book ("DRM blocked", naming the scheme) apart from a file the
application cannot read ("Unsupported", naming what it looks like), as the reference rendering draws. Today both
arrive as `ErrorCode.validation`, told apart only by message text. ADR-0022 and
`02_Architecture/09_ERROR_HANDLING.md#error-code` hold `ErrorCode` at fifteen constants on purpose: every module
compiles against that set.

## Decision

- A new read-only inspection answers a path with a **verdict**: readable, DRM-protected (with the encryption scheme
  when known), or unsupported (with the detected file type, such as PDF or DOCX), plus the format, its version and the
  language evidence (ADR-0037). A refusal is a normal answer, not a failure; an unexpected fault is still `internal`.
- Opening a book is unchanged: a refused book still fails `open` with `ErrorCode.validation`, so the command line's
  exit codes and every existing caller keep their behaviour.
- No `ErrorCode` constant is added.

## Considered options

- **Add `drm` and `unsupported` codes.** Rejected: it breaks the frozen vocabulary ADR-0022 defends, for one screen.
- **Branch on the message text.** Rejected: brittle, and the text is localized.

## Consequences

- **Positive:** the screen routes on a closed, typed verdict; the envelope stays stable.
- **Negative:** the book is examined twice on import (inspection, then open). Inspection reads only the container
  manifest and headers, so the cost is small.
- **Neutral:** `11_NOTIFICATIONS_AND_ERRORS.md#error-code-categories` already lists "drm-protected" and
  "unsupported-format" as categories, which this realizes without new codes.

## What would falsify this decision

A second screen needs the same distinction from a failure that is not an import, which would argue for a code.
