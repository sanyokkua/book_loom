# Known limits and open work

What BookLoom does not do well yet, or deliberately does not do. Each entry says where it lives and what would fix
it. Items fixed by an archived change are not listed; the change history is in `openspec/changes/archive/`.
The backlog with ids (D1 and on) is `docs/spec_baseline/implementation_plan/CHANGE_BACKLOG.md`.

## Quality limits the owner accepted (2026-11-11)

Measured in the closing run of `redesign-translation-pipeline` (archived 2026-11-12)
([results](evaluation/results-redesign-closing-run.md)); the review desk is the guard for each.

| Limit | What happens | Backlog |
|---|---|---|
| Quality not proven better than the old pipeline | The redesigned pipeline scored 0.9682 against 0.9108 on the long-context check, but the paired reading was 66:47 in its favour (p = 0.090), below the preset bar | D20 |
| Narrator gender slips | At Balanced 9 and at Max 5 paragraphs changed the narrator's grammatical gender; the old pipeline had 0 | D21 |
| Source-language words in ru to uk output | About 5 of 100 sampled paragraphs keep a Russian content word; there is no word-level language check | D22 |
| Possessives and case forms as separate glossary rows | 7 of the top 20 names were spelled two ways; one flagged paragraph was exported in Russian | D23 |

## Seen in the hand run, not fixed (backlog D24, D25)

- On very short books a chapter summary is often not stored ("Chapter summary not kept", reason `all-dropped`), so
  every Continue asks for it again. Not yet checked on a long book.
- The Ukrainian interface was not checked by hand (the test machine's locale was English); the bundles are tested for
  key parity and plural forms only.
- The finished banner says "1 segment needs your review" when only a second-look item remains.
- A name filter on the model drop-down can show a cut-off list near the window bottom.

## Open or deliberate

- **Provider calls.** The silent structured-output downgrade exists (`StructuredRungs`), but there is no bounded JSON
  repair call and no extraction of JSON from surrounding prose; an unparseable reply is re-asked, or the segment is
  flagged. Authentication is a bearer header from
  an environment-variable name; an OS keychain is not built.
- **Log volume.** DEBUG is verbose on purpose (about 37 lines per segment). For large books set
  `BOOKLOOM_LOG_LEVEL=INFO`; the run log and the detailed log are bounded
  ([logging](architecture/overview.md)).
- **Command line.** Translating every book of a folder is a non-goal; run the command once per book.
- **Book Brief labels.** Long Ukrainian option labels may truncate at a 1024 px window width (cosmetic).
- **Windows.** The reveal-in-folder action handles the odd `explorer.exe` exit code, but the quoting of its
  `/select,<path>` argument has not been checked on a real Windows machine. Packages are unsigned on every platform.
- **Database before release.** Until the first release the schema is one file; a database with a different schema or
  record shape is renamed `bookloom.db.old-<time>` and a fresh one is created (ADR-0043).
