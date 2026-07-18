# Architecture Decision Records (ADRs)

ADRs are the durable record of architecturally significant decisions — the heavyweight companion to the lightweight
decision log in `../specification/00_Foundation/04_DESIGN_DECISIONS.md` (the `DD-NN` entries). Each DD that is
architecturally significant is backed by an ADR here; each ADR links back to its DD-NN, the spec clauses it governs, and
(once they exist) the stories that apply it.

- **Format:** `ADR-NNNN-<slug>.md`, following `template.md` and `../implementation_plan/04_ADR_FORMAT.md`.
- **Numbering:** four digits, monotonic, permanent. Never reuse a number.
- **Status:** `proposed → accepted → superseded by ADR-MMMM | deprecated`. A superseded ADR is kept for history; the
  superseding ADR names it.
- **When to write one:** an architecturally significant decision not already settled by the specification (a spec gap
  surfaced during planning), or a change to a previously accepted decision. The `architect` agent (and the
  `adr-authoring` skill) own authoring.

## Accepted ADRs

| ADR      | Title                                                                                 | Backs               |
|----------|---------------------------------------------------------------------------------------|---------------------|
| ADR-0001 | Language & UI toolkit — Java 25 + JavaFX 25                                           | DD-02               |
| ADR-0002 | Build tool — Gradle (Kotlin DSL)                                                      | DD-03               |
| ADR-0003 | Document skeleton + segment model                                                     | DD-07               |
| ADR-0004 | Supported formats scope — EPUB / FB2 / MD / TXT                                       | DD-08               |
| ADR-0005 | Provider abstraction — two client implementations (Ollama-native + OpenAI-compatible) | DD-09, DD-10, DD-32 |
| ADR-0006 | Credentials stored as a reference                                                     | DD-11               |
| ADR-0007 | Automatic-first tiered translation pipeline                                           | DD-15…DD-19         |
| ADR-0008 | Inference concurrency — single-flight gate + typed retry                              | DD-12, DD-13        |
| ADR-0009 | Persistence — SQLite + Flyway + JDBI                                                  | DD-20               |
| ADR-0010 | Offline network policy                                                                | DD-01               |
| ADR-0011 | App icon & branding — one background-removed master, per-OS derivation                | DD-29               |
| ADR-0012 | Per-project provider/model binding + change confirmation + preflight verification     | DD-31               |
| ADR-0013 | Response-handling contract — JSON-first, tolerant, repair + text fallback             | DD-33               |
| ADR-0014 | Lombok on services, records for data carriers (hybrid)                                | DD-05               |
| ADR-0015 | Per-OS app paths resolved first, hand-rolled, dev/prod separation                     | DD-39               |
