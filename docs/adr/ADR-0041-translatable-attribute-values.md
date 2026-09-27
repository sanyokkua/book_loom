# ADR-0041 — The attribute values DD-47 names are translatable: an exception to "only text nodes change"

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner (plan approval)

## Context and problem statement

`AGENTS.md` and `.claude/rules/document-roundtrip.md` state that the skeleton is never regenerated and only text
nodes change. DD-47 and FR-DOC-11 require translating image alternative text — an attribute value, not a text node —
together with metadata titles, navigation labels and Markdown frontmatter values, under the Book Brief's "Also
translate" switches (`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`). The owner asked for all "Also translate"
switches to work.

## Decision

- Image alternative text (`alt` on an image; the alt part of a Markdown image) is a translatable slot, addressed by a
  new anchor kind that names an element and one attribute.
- The exception is closed: only the attribute values DD-47 lists become translatable. Every other attribute, element,
  identifier and link stays byte-for-byte as it was.
- The golden round trip still proves a zero-edit reassembly is canonical-equal, and a second golden per format proves
  that editing only those slots changes only those values.

## Considered options

- **Leave alt text untranslated.** Rejected: DD-47 requires it and the owner asked for the switch to work.
- **Treat any attribute as translatable when asked.** Rejected: `title`, `href` and data attributes carry identifiers
  and must never change.

## Consequences

- **Positive:** a translated book no longer reads its image descriptions aloud in the source language.
- **Negative:** the invariant statement becomes "only text nodes and the listed attribute values change", and both
  documents that state it are edited.

## What would falsify this decision

A translated attribute value breaks a reading system — for example because a book uses `alt` text as an identifier
or lookup key.
