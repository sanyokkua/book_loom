---
name: create-mermaid-diagrams
description: >-
  Use when authoring or validating a mermaid diagram for the BookLoom
  specification — flowchart label quoting rules and avoiding `<`, `>`, and `->` inside
  labels so the diagram renders. Covers the safe-label conventions and validating a
  `.mermaid` file. Note the two canonical pipeline diagrams already at
  `docs/specification/diagrams/` — reference them, do not duplicate them.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Create Mermaid Diagrams

Diagrams in the spec are `.mermaid` files under `docs/specification/diagrams/`. They must
render in a standard mermaid renderer without escaping errors. The two canonical pipeline
diagrams already exist — reference them, never invent parallel copies.

## When to use

- Adding a new diagram to `docs/specification/diagrams/` for a spec section — note
  `docs/specification/` is frozen for normal story work, so a new spec diagram lands only
  as part of a lead-approved spec change; diagrams elsewhere (e.g. in a story or note)
  follow the same syntax rules.
- Fixing a diagram that fails to render (parse/escaping error).
- Validating a `.mermaid` file's syntax before committing.

## When NOT to use

- Do NOT create a new pipeline or chunk-loop diagram — the canonical ones already exist at
  `docs/specification/diagrams/pipeline.mermaid` (whole-book flow) and
  `docs/specification/diagrams/chunk-translate-loop.mermaid` (per-chunk loop). Reference
  them from prose instead.
- Do NOT embed a diagram's meaning only in the diagram — the spec text is authoritative;
  the diagram illustrates it.
- Do NOT edit frozen spec prose to fit a diagram.

## Workflow

1. **Check for an existing diagram** in `docs/specification/diagrams/` before authoring; if
   the concept is the pipeline or the chunk loop, cite the canonical file rather than
   redraw it.
2. **Choose the diagram type** (`flowchart TD`/`LR`, `stateDiagram-v2`, `sequenceDiagram`)
   that matches the spec section (e.g. the segment status state machine -> `stateDiagram-v2`).
3. **Write node labels safely** (see quoting rules) — this is where diagrams break most.
4. **Keep it self-contained** — no references to any out-of-repo project or history; labels
   name only in-repo concepts (modules, ports, states, `⟦gN⟧`, `Result`/`AppError`).
5. **Validate** the file renders (mermaid CLI if available: `mmdc -i <file>.mermaid -o
   /tmp/out.svg`; otherwise review against the quoting rules) and that arrows/edges parse.

## Flowchart label quoting rules

- Wrap ANY label containing spaces, punctuation, or special characters in double quotes:
  `A["Assemble context package"]`.
- NEVER put a bare `<`, `>`, or `->` inside a label — the parser treats them as markup or
  edge syntax and the diagram fails. Rephrase:
  - draft `->` QA  becomes  `"draft then QA"` or `"draft to QA"`.
  - `target/source < band`  becomes  `"ratio below band"`.
  - `A > B`  becomes  `"A over B"` or `"A greater than B"`.
- Avoid raw parentheses/brackets/braces/pipe/backtick/`#`/`;` inside an UNquoted label;
  quote the label if you need them, and prefer plain words.
- Use `<br/>` only inside a quoted label for a line break; never a literal newline.
- Edge labels follow the same rule: `A -->|"pass QA"| B`, not `A -->|pass->QA| B`.
- Keep node ids simple (`ascii` letters/digits/underscore); put the human text in the label.

## Reference index

- Canonical in-repo diagrams (reference, do not duplicate):
  `docs/specification/diagrams/pipeline.mermaid`,
  `docs/specification/diagrams/chunk-translate-loop.mermaid`.
- In-repo authorities: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`,
  `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
  `docs/specification/01_Product/06_REVIEW_AND_EDITING.md` (state machine).

## Mandatory validation checklist

- [ ] No bare `<`, `>`, or `->` inside any node or edge label.
- [ ] Every label with spaces/punctuation is double-quoted.
- [ ] The file renders without a parse error (CLI or careful review).
- [ ] Node ids are simple ascii; human text lives in labels.
- [ ] The diagram is self-contained — no out-of-repo names or decision history.
- [ ] No duplicate of the two canonical pipeline diagrams was created.

## Gotchas

- The single most common failure is a `->` or a comparison operator inside a label — always
  rephrase to words.
- An unquoted label with a comma or colon can silently truncate; quote defensively.
- `stateDiagram-v2` uses `-->` for transitions; a transition LABEL still must not contain a
  bare `>` — write `A --> B : "ratio below band"`.
- Reintroducing a parallel pipeline diagram fragments the source of truth — cite the
  canonical files instead.
