---
name: document-pipeline
description: >-
  Use when working in `:document` — parsing EPUB/FB2/Markdown/TXT into the skeleton +
  ordered segment model, inline masking to `⟦gN⟧` placeholders, unmask + tag-multiset
  validation, reassembly and repackaging, and the per-format round-trip golden test.
  Covers the per-format rules (EPUB mimetype-first / IDs / fonts, FB2 XML / encoding /
  binary, Markdown AST, TXT), detecting the real source language, and refusing DRM.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Document Pipeline (`:document`)

The governing principle (DD-07/DD-43): parse to an immutable **skeleton** plus an **ordered,
individually-addressable segment list**; translate text nodes only; reassemble
**structure-and-text-preserving (canonical-equal)** — export re-serializes the parsed model,
so exact bytes may differ under jsoup/CommonMark/zip normalization, but structure, nesting,
IDs, images, fonts, encoding, and all text are preserved. The skeleton is NEVER sent to the
model and NEVER semantically regenerated — only text nodes change, so formatting is
preserved by construction. `:document` is FX-free and implements the `DocumentPort` port
from `:api`.

## When to use

- Adding or fixing a format parser (EPUB, FB2, Markdown, TXT) or its reassembly.
- Implementing inline masking / unmasking / the tag-multiset validation gate.
- Writing or updating a per-format round-trip golden test.
- Handling a format edge case (DRM, encoding, verse, notes, images, fonts).
- Detecting the real source language of an imported book.

## When NOT to use

- Do NOT regenerate the skeleton on export — write target strings back into the SAME nodes.
- Do NOT translate or send the skeleton, attributes, IDs, image bytes, fonts, ZIP layout,
  or the OPF manifest/spine identity — those are preserved verbatim. Exceptions (DD-47):
  book metadata title/author, Markdown frontmatter values, image `alt`, and EPUB nav/NCX
  ToC labels are translatable **metadata-unit segments**, each gated by its Book-Brief
  "Also translate" toggle.
- Do NOT translate code or math (DD-49): inline `<code>` and inline MathML `<math>` are
  atomic protected placeholders (text kept exactly); `<pre>`/block code and block math are
  non-translatable blocks excluded from segmentation and the token budget.
- Do NOT add PDF/DOCX — scope is EPUB/FB2/MD/TXT only (DD-08).
- Do NOT trust declared language metadata — detect the real content language.
- Do NOT attempt to decrypt DRM — detect and refuse.
- Do NOT `requires javafx.*` (FX-free core).

## Workflow

1. **Parse to skeleton + segments.** Read the container/document and build the immutable
   skeleton with an ordered segment list; each segment is individually addressable and
   carries a `source_hash`. Text nodes are the only mutable slots.
   - **EPUB:** parse the OPF, read the spine for reading order, process content documents
     in spine order; parse XHTML bodies with jsoup; support EPUB 2 and 3; content not in
     the spine is preserved verbatim — EXCEPT the EPUB3 nav / EPUB2 NCX ToC labels, which
     are carved out of the out-of-spine rule and translatable via the Book-Brief
     "Also translate" group (DD-47).
   - **FB2:** parse as one XML document (JDOM2) preserving comments, CDATA, entities, and
     declared encoding; support `.fb2.zip`; keep base64 `binary` images unchanged; handle
     `poem`/`stanza`/`v` verse and `body name="notes"` footnotes.
   - **Markdown:** parse to an AST (CommonMark); translate prose text nodes only; mask
     inline code and URLs; fenced/indented code blocks are non-translatable blocks excluded
     from segmentation and the token budget (DD-49); protect frontmatter keys (values are
     translatable only via the "Also translate" toggle, DD-47).
   - **TXT:** paragraph-delimited segments (blank-line separated); preserve line endings,
     whitespace/indentation, and encoding.
2. **Detect the real source language** (Lingua), ignoring declared metadata. If declared
   disagrees with detected, surface a language-mismatch state (EC-LANG-1) for user confirm.
   Detect a multi-language book's dominant language; per-segment foreign passages follow
   the foreign-passage policy (default keep-as-is, DD-26).
3. **Refuse DRM.** Detect encryption (`META-INF/encryption.xml` or vendor DRM in EPUB;
   container encryption in FB2) and refuse with a DRM-blocked state — no partial import
   (EC-EPUB-1, EC-DRM-1). Reject malformed/corrupt files (missing OPF/spine, XML-decl vs
   bytes mismatch) with a clear reason; no partial import.
4. **Mask inline content.** Replace inline markup, locked glossary terms, URLs, and
   **selective numerals** — standalone/typographic numerals and numerals inside locked
   terms; prose numerals stay translatable so they inflect/localize — with `⟦gN⟧`
   placeholders before translation (FR-DOC-04). Inline `<code>` and inline MathML `<math>`
   mask as **single atomic protected placeholders**, their text kept exactly (DD-49);
   `<pre>`/block code and block math never reach the masking layer (excluded from
   segmentation and the token budget). Index-term/cross-reference anchor ids/targets are
   preserved; the visible link text stays translatable. Mask nested tags as a well-formed
   placeholder set and restore exact nesting; mask only the intended span for a locked term
   inside a longer word (avoid partial-word substitution).
5. **Unmask + validate (hard gate).** After translation, restore placeholders and run the
   **tag-multiset check**: the placeholder multiset in the output MUST equal the input's.
   A dropped/duplicated placeholder HARD-FAILS the chunk (never export a broken set) — the
   pipeline routes it to self-heal/flag (EC-INLINE-2).
6. **Reassemble + repackage** per format:
   - **EPUB:** write `mimetype` FIRST and STORED (uncompressed); preserve all other ZIP
     entries and their identity; keep all element IDs, cross-reference targets, images, and
     embedded/obfuscated fonts unchanged; update `dc:language` to the target on export.
   - **FB2:** preserve declared encoding on export (including `windows-1251`), updating the
     XML declaration accordingly; keep binary images unchanged; update the language element.
   - **Markdown:** preserve structure (headings, lists, tables, blockquotes, emphasis);
     never translate URL targets or code spans; translate frontmatter values only where
     configured.
   - **TXT:** preserve whitespace/indentation and line endings.
7. **Prove round-trip fidelity.** For each format, the golden test parses to
   skeleton+segments and reassembles WITHOUT changing any target text, and MUST produce a
   **canonical-equal** artifact: the comparison canonicalizes both sides (re-parse-equal /
   canonical-XML equal; EPUB by decompressed canonical content + preserved entry order +
   mimetype-first/STORED, unchanged binary entries by decompressed bytes; **TXT exact
   bytes**) — except intentional language-metadata updates (FR-DOC-09, DD-43). Run over
   representative fixtures.

## Reference index

- In-repo authorities: `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`,
  `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
  `docs/specification/00_Foundation/02_GLOSSARY.md`.

## Mandatory validation checklist

- [ ] Parsing yields an immutable skeleton + ordered, addressable segments with hashes.
- [ ] The skeleton is never sent to the model and never semantically regenerated on export.
- [ ] Attributes, IDs, images, fonts, ZIP layout, and OPF/spine identity are preserved
      (metadata title/author, frontmatter values, `alt`, and nav/NCX labels translate only
      via their "Also translate" toggles, DD-47).
- [ ] Inline markup / locked terms / URLs / selective numerals masked to `⟦gN⟧` (prose
      numerals translatable); nesting restored.
- [ ] Inline `<code>`/inline MathML are atomic protected placeholders; `<pre>`/block
      code/block math excluded from segmentation and the token budget; anchor ids
      preserved, link text translatable (DD-49).
- [ ] Tag-multiset hard gate fails a dropped/duplicated placeholder; no broken export.
- [ ] EPUB repackage writes `mimetype` first + stored; `dc:language` updated on export.
- [ ] FB2 preserves declared encoding (incl. `windows-1251`) and binary images.
- [ ] Real source language detected (Lingua); language-mismatch surfaced; DRM refused.
- [ ] Per-format round-trip golden test is canonical-equal — canonicalized compare, TXT
      exact bytes (minus intentional lang metadata, DD-43).
- [ ] `:document` remains FX-free (ArchUnit green).

## Gotchas

- EPUB `mimetype` MUST be the first entry and STORED (not deflated) or readers reject the
  book — the round-trip test will catch it.
- FB2 `windows-1251` books silently corrupt if read/written as UTF-8; honor the declared
  encoding and update the XML declaration on any intentional change.
- A locked term inside a longer word must mask only its span — partial-word substitution
  breaks the text (EC-INLINE-3).
- Foreign-language passages default to keep-as-is; the QA "wrong language" check is
  policy-aware and must NOT flag a deliberately kept passage (DD-26, EC-FOREIGN-1/2).
- Duplicate/non-unique element IDs are preserved as-is — never rewrite IDs (EC-EPUB-4).
- Out-of-spine EPUB resources are preserved verbatim, not translated (EC-EPUB-3) — with
  the DD-47 carve-out: nav/NCX ToC labels are translatable via their toggle.
