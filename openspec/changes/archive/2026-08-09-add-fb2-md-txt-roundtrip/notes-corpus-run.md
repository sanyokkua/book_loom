# Corpus run — task 12.8

**Local, manual, optional.** Run against the real-book corpus in the git-ignored `.temporary_context/` on one
machine. Nothing here is a gate, nothing from the corpus is committed, and the build does not reference that
directory (verified by task 12.7). Recorded because the finding below is worth more than the run.

## What was run

Every `.epub`, `.fb2`, `.fb2.zip`, `.md` and `.txt` under the corpus was opened through the real `DocumentPort`
by a throwaway harness, which was deleted afterwards. Counted: books opened, books yielding zero segments, books
refused, and the refusal's user-facing title.

## Result

| Format | Books | Opened | Zero segments | Refused |
|---|---|---|---|---|
| `.epub` | 194 | 193 | **12** | 1 |
| `.fb2` | 7 | 7 | 0 | 0 |
| `.md` | 11 | 11 | 0 | 0 |
| `.txt` | 4 | 4 | 0 | 0 |
| **Total** | **216** | **215** | **12** | **1** |

**626,668 segments** across the corpus. The one refusal surfaces as "This file could not be opened" — the
format-neutral message this change introduced, not the "not a valid EPUB" the shipped code would have said.

Every FB2, Markdown and TXT book in the corpus opened and produced segments. The three new formats behave on real
data as they do on the fixtures.

## The finding: 12 EPUBs still import empty, and not for the reason ADR-0027 fixed

ADR-0027 predicted 42 books importing essentially empty under the tag whitelist. After structural recognition, 12
still yield zero segments — a **different** cause, and one this change did not introduce.

**Diagnosis, confirmed by a minimal reproduction.** These books are XHTML that self-closes its script tag:

```xml
<head>…<script src="js/book.js"/><meta charset="UTF-8"/></head>
<body><h2>Heading</h2><p>Real prose here.</p></body>
```

That is valid XHTML and unrepresentable in HTML: `<script>` is a raw-text element, so an HTML parser cannot honour
an XML self-closing tag on it and instead treats **everything after it** as script data. jsoup's HTML parser —
chosen deliberately in change 3's design.md D4, because real-world XHTML is HTML-shaped and an XML parser rejects
it — therefore consumes the whole document. Measured on the reproduction: `body.children()` is `0` with the
self-closed script present and `2` with it removed, and the swallowed "script data" is the entire body.

The affected books are one converter's output — a Conan Doyle set, both Alice books, and Švejk — each with 15 to 48
real chapter files.

**Why it was not fixed here.** No task or requirement in this change covers it; the parser choice is a design
decision (D4) whose revision belongs in its own change with its own fixture and its own ADR note. Folding a fourth
shipped-EPUB behaviour change into a change that already carries three ADRs would be unreviewed scope creep, and
the fix is not as small as it looks: the skeleton would then carry `<script …></script>` where the source had
`<script …/>`, which is fine under DD-43 but is a round-trip behaviour change that deserves its own gate.

**Why it matters.** It is exactly the failure class ADR-0027 exists to eliminate — a book that imports with no text,
invisible to every fidelity assertion — and it costs 6.2% of this library. The coverage assertion added by this
change is what would catch it, and would have caught it here, had a fixture carried that shape. A follow-up change
should add one.

Recorded in `docs/implementation_plan/CHANGE_BACKLOG.md` so it is not lost.
