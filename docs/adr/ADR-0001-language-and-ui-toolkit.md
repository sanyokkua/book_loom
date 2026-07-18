# ADR-0001 — Build the desktop application on Java 25 (LTS) with a JavaFX 25 (LTS) UI

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The product is a local-first, offline desktop application that translates whole books by orchestrating a locally hosted
Large Language Model. It must run identically on Windows, macOS, and Linux, ship as a native installer, keep a
responsive UI while long translation jobs run in the background, parse and re-emit structured document formats with
structure and text preserved (canonical-equal round-trip, DD-43), and enforce clean boundaries between a headless core
and the UI.

The choice of implementation language and UI toolkit is the most far-reaching technical decision in the project: it
constrains the concurrency model, the packaging story, the library ecosystem available for document and HTTP work, and
the shape of every module boundary. This ADR fixes that choice.

## Decision drivers

- **True cross-platform parity.** The three desktop OSes must render and behave the same; per-OS UI forks are
  unacceptable for a small team.
- **Real multithreading.** Translation fans out I/O to a local model while keeping the UI responsive; a runtime without
  a global interpreter lock is strongly preferred.
- **Native desktop feel.** The product is a desktop tool with tables, trees, side-by-side panes, and dialogs — not a
  document viewer or a browser app.
- **Strong modelling primitives.** Records, sealed types, and pattern matching materially reduce boilerplate for the
  document model, the provider profiles, and the `Result`/`AppError` envelope.
- **Enforceable module boundaries.** The core (document, llm, pipeline, persistence) must stay free of any UI
  dependency, ideally enforced by the platform itself.
- **Mature, offline-friendly library ecosystem** for XML round-trip, ZIP containers, SQLite, and HTTP.
- **Native packaging** to per-OS installers without a bundled browser runtime.
- **Small-team maintainability** over a multi-year horizon; long-term-support runtimes reduce churn.

## Considered options

- **Option A — Java 25 (LTS) + JavaFX 25 (LTS).** A single JVM runtime with a first-party retained-mode UI toolkit,
  packaged via jpackage.
- **Option B — Python + PySide (Qt).** A dynamic language with mature Qt bindings for the desktop UI.
- **Option C — Go + a web front-end (embedded webview).** A compiled backend with the UI authored as HTML/CSS/JS
  rendered in a system or bundled webview.

## Decision outcome

Chosen: **Option A — Java 25 (LTS) + JavaFX 25 (LTS)**, because it is the only option that simultaneously delivers
identical cross-platform rendering, genuine no-GIL multithreading, a native-feeling retained-mode control set, and
platform-enforced module boundaries, all on a long-term-support runtime with a deep offline library ecosystem.

Java 25 is an LTS release; JavaFX 25 is the matching LTS of the toolkit. The core modules (`:api`, `:util`, `:document`,
`:llm`, `:pipeline`, `:persistence`) remain UI-free; only `:ui` and `:app` may depend on JavaFX, a boundary enforced by
both the Java Platform Module System and ArchUnit. Records, sealed interfaces, and pattern matching are used throughout
the document model, the provider profiles, and the error envelope. Native delivery is via jpackage (see
`docs/adr/ADR-0002-build-tool-gradle.md` and `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`).

### Consequences

Positive:

- One codebase renders and behaves identically on all three OSes; no per-platform UI fork.
- Platform threads and virtual threads give real parallel I/O fan-out with no interpreter lock; long jobs run off the UI
  thread cleanly.
- Records/sealed/pattern-matching cut boilerplate and make the data model transparent and immutable by default.
- JPMS turns "the core must not see the UI" into a compile-time and runtime guarantee, not a convention.
- Mature offline libraries exist for every core need (XML round-trip, ZIP, SQLite, HTTP in the JDK itself).

Negative:

- JavaFX is not bundled with the JDK; it is an explicit dependency and adds packaging complexity (mitigated by the
  openjfx and jlink/jpackage tooling in ADR-0002).
- The JavaFX contributor pool and third-party control ecosystem are smaller than the web front-end world; some controls
  (toggle switches, notifications) come from community libraries.
- Bundled runtime images make installers larger than a single Go binary.

Neutral:

- Commits the team to JVM tooling and idioms across the whole stack.
- FXML-based views plus a Guice controller factory become the standard UI construction pattern (see
  `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`).

## Pros and cons of the options

### Option A — Java 25 + JavaFX 25

Pros:

- Identical rendering and behaviour across Windows, macOS, and Linux from one toolkit.
- No global interpreter lock; platform and virtual threads give true concurrency for I/O fan-out.
- Records, sealed types, and pattern matching fit the document/provider/error models exactly.
- JPMS enforces the FX-free-core boundary at the platform level.
- LTS runtime and toolkit reduce upgrade churn; deep, offline-capable library ecosystem.

Cons:

- JavaFX must be packaged explicitly; installers bundle a runtime image and are larger.
- Smaller UI-control and contributor ecosystem than web front-ends; some widgets need community libraries.
- jpackage cannot cross-compile, so each installer is built on its own OS.

### Option B — Python + PySide (Qt)

Pros:

- Qt is a genuinely native-feeling, mature desktop toolkit with a rich control set.
- Fast to prototype; large data/ML library ecosystem.

Cons:

- The global interpreter lock throttles CPU-bound parallelism and complicates the concurrency model for background jobs;
  true parallel fan-out needs multiprocessing.
- No compile-time module boundary enforcement; keeping a UI-free core is a convention, not a guarantee.
- Dynamic typing weakens the guarantees around the document model and the error envelope.
- Packaging a Python + Qt app to clean per-OS installers is workable but fiddly, and Qt licensing must be managed
  carefully.

### Option C — Go + web front-end (embedded webview)

Pros:

- Small, fast single-binary backend with excellent concurrency primitives (goroutines).
- Front-end authored with familiar web technologies.

Cons:

- The UI is HTML/CSS/JS in a webview, not a native retained-mode toolkit; rendering depends on the host webview and can
  differ per OS.
- Go lacks generics-era ergonomics for rich sum types; no sealed/record equivalent for the document and error models,
  and no pattern matching.
- A two-language (Go + JS) stack doubles the toolchain and the boundary-enforcement problem.
- Bundling or depending on a system webview reintroduces cross-platform inconsistency — the very thing the product must
  avoid.

## Links

- Design decisions: DD-02 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-02-java-javafx`)
- Spec clauses: `docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`,
  `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
  `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
  `docs/specification/03_NonFunctional/01_QUALITY_ATTRIBUTES.md`
- Stories: none yet
