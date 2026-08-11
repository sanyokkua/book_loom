# Architecture Decision Records (ADRs)

ADRs are the durable record of architecturally significant decisions — the heavyweight companion to the lightweight
decision log in `../specification/00_Foundation/04_DESIGN_DECISIONS.md` (the `DD-NN` entries). Each DD that is
architecturally significant is backed by an ADR here; each ADR links back to its DD-NN, the spec clauses it governs, and
(once they exist) the OpenSpec changes that apply it.

Two ADRs are **process** decisions rather than DD-backed architecture: ADR-0016 (OpenSpec is the delivery-tracking
system) and ADR-0017 (the delivery order). Both deviate from clauses in the frozen specification and name those clauses
explicitly — read them before planning work.

- **Format:** `ADR-NNNN-<slug>.md`, following `template.md` and `../implementation_plan/04_ADR_FORMAT.md`.
- **Numbering:** four digits, monotonic, permanent. Never reuse a number.
- **Status:** `proposed → accepted → superseded by ADR-MMMM | deprecated`. A superseded ADR is kept for history; the
  superseding ADR names it.
- **When to write one:** an architecturally significant decision not already settled by the specification (a spec gap
  surfaced during planning), or a change to a previously accepted decision. The `adr-authoring` skill owns the format;
  an OpenSpec change's `design.md` cites the governing ADR rather than restating it.

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
| ADR-0016 | OpenSpec changes replace stories; generated traceability is never built               | process             |
| ADR-0017 | Infrastructure-first delivery order; UI component library parallel with documents     | process             |
| ADR-0018 | Canonical requirement-id form; owners for the three unmapped FR families              | process             |
| ADR-0019 | JavaFX 26 for its built-in headless platform (supersedes the JavaFX 25 pin)           | DD-02               |
| ADR-0020 | Public Domain allowed on the license gate, scoped to `aopalliance` (Guice transitive) | process             |
| ADR-0021 | The nine code directories live under `modules/`; project names are unchanged          | process             |
| ADR-0022 | `ErrorCode.busy` covers the single-instance lock; the enum stays at fifteen constants | DD-14               |
| ADR-0023 | Backend-complete milestone (stub-provider whole-book run) + five backlog interstitials | process             |
| ADR-0024 | Lombok `@NoArgsConstructor(PRIVATE)` for static-utility classes (extends ADR-0014)    | DD-05               |
| ADR-0025 | Reassembly replaces a segment run's inner content, not a single text node             | DD-07, DD-43        |
| ADR-0026 | DRM adjudicated by what is encrypted, not by the algorithm URI                        | EC-EPUB-1           |
| ADR-0027 | A translatable block is recognised structurally, not by a tag whitelist               | DD-07               |
| ADR-0028 | jsoup stays the EPUB XHTML parser, qualified by one pre-parse normalization           | DD-43, DD-49        |
| ADR-0029 | Transcode the output document to UTF-8 when the source charset cannot hold the target | DD-43               |
| ADR-0030 | Synthesize a missing EPUB `mimetype` entry on write rather than refusing the book      | DD-43               |
