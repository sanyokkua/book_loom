# ADR-0044 — Look for a local AI program once, by itself, at first launch

**Status:** accepted 2026-10-20 **Date:** 2026-10-20 **Deciders:** owner (decision 13 of the 2026-10-19 final review)
**Amends:** ADR-0010, DD-01

## Context and problem statement

ADR-0010 and DD-01 allow exactly one kind of outbound call: provider communication that the person starts (inference,
model discovery, verification). A first-time, non-technical user meets an empty Settings screen and does not know what
an "endpoint" is. The final review found this to be the largest first-launch obstacle
(`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`, theme D). Most people run Ollama or LM Studio on the
same computer, on fixed ports.

## Decision

- **On the very first launch only**, after the window is shown, BookLoom asks two local addresses for their model list:
  - Ollama at `http://127.0.0.1:11434` (`/api/tags`);
  - LM Studio at `http://127.0.0.1:1234/v1` (`/models`).
- **No other address, host name or port is tried.** It is a listing only: no prompt, no book text and no inference, so
  it does not wait for the inference gate.
- **If a program answers with models,** BookLoom stores it as a provider. It selects the model when there is exactly
  one, or asks the person which one when there are several.
- **If nothing answers,** within a 1 s connect timeout, the Projects screen shows "No AI model found" with Try again,
  which is a person-started check. Nothing is shown as an error.
- **A stored flag records that the look happened,** so it is never repeated by itself, whatever the result. Every later
  check is started by the person.
- **A fresh database counts as a first launch.** Before the first release a database from another schema version is
  renamed aside and a fresh one created (ADR-0043); the flag lives in that database, so the look runs once more after
  such a reset. That is the only way it repeats by itself.
- **It runs only in the desktop window,** never in the command line or the tests (the tests fake the catalog).
- **The code stays where it is:** the listing goes through the existing `:llm` model catalog, so `:llm` is still the
  only module with HTTP code (ArchUnit `no-http-in-core-except-llm` is unchanged).

## Considered options

- **Keep every call person-started, and show a "Find my AI program" button.** Rejected by the owner: the first screen
  should already say whether translation can start.
- **Scan a port range or the local network.** Rejected: it is slower, it is surprising, and it is outside "this
  computer".

## Consequences

- **Positive:** a person with Ollama or LM Studio running sees "AI model … ready ✓" on first launch and can start
  without opening Settings.
- **Negative:** the offline rule gains one exception, which must be stated wherever the rule is stated: `AGENTS.md`,
  `.claude/rules/offline-and-privacy.md`, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md` and DD-01.
- **Neutral:** the egress test allows exactly these two addresses, once, at first launch.

## What would falsify this decision

- The look is seen reaching any address other than the two above.
- Or it happens a second time without the person asking, other than once after a fresh database.
- Or it delays the first window by more than 1 second.

## Links

- Design decisions: DD-01.
- Spec clauses: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, `docs/specification/mockups/README.md`
  ("First launch and the AI model").
- Change: `openspec/changes/finalize-ui-and-persistence/`.
