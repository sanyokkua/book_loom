# BookLoom

BookLoom is a desktop application that translates whole books (EPUB, FB2, Markdown, TXT) with a language model that
runs on your own computer (Ollama or LM Studio) or with any OpenAI-compatible endpoint. It translates only the text
and writes the book back in the same format with its structure, identifiers, images and fonts intact.

Opening a book never needs the network. The only network traffic is the requests you start to the model server you
configure. With a local model the book stays on your computer; with a server elsewhere the text being translated is
sent to that server. There is no account, telemetry or update check.

![Translating a book](docs/screens/04-translating.png)

## What it does

- **Formats.** EPUB, FB2 (including `.fb2.zip`), Markdown and TXT. Every format round-trips: the book you write is
  canonical-equal to the one you read, apart from the translated text and a few listed fields (title, author,
  language, image alt text). Verified on a 235-book local corpus.
- **A five-step workflow.** Import, Book Brief (languages, tone, who narrates, how to treat names, the "Also
  translate" switches, quality), Names & style (a glossary the book shares across chapters), Translating, Export.
- **Quality control.** Placeholders protect formatting inside a paragraph; deterministic checks decide each segment;
  a draft that fails gets one repair call and is kept only if it is not worse; a final Proofread stage reads the
  book again. A review desk lists whatever still needs a person.
- **Resumable.** Everything is stored in a local SQLite database; a stopped or crashed run continues where it
  stopped, and a partial book can be exported.
- **Interface** in English and Ukrainian (chosen from the operating system), light and dark themes.
- **Platforms.** Java 25 and JavaFX 26. Unsigned packages for macOS, Linux and Windows are built by the scripts in
  `scripts/`; see [release and packaging](docs/project/release-and-packaging.md).

## How it works

```mermaid
flowchart LR
  A[Import book] --> B[Book Brief]
  B --> C[Names and style]
  C --> D[Run: Prepare]
  D --> E[Run: Translate]
  E --> F[Run: Proofread]
  F --> G[Export]
  E -.-> H[Review desk]
  F -.-> H
```

The book is parsed into a skeleton and an ordered list of text segments. Inline formatting inside a segment is
replaced by placeholders (`⟦g1⟧`), so the model sees plain text. Segments are packed into chunks, drafted in JSON
batches, checked and decided one by one; the placeholders are restored behind a hard gate that checks their count and
the order of paired ones. The skeleton is never regenerated. Details: [translation
pipeline](docs/pipeline/translation-pipeline.md) and [document engine](docs/architecture/document-engine.md).

The quality setting (Fast, Balanced, Max) changes only the fixing call and how far Proofread reads. The review mode
(Unattended, Assisted, Manual) changes only when the run pauses for you.

## Screens

| Import | Book Brief |
|---|---|
| ![Import](docs/screens/01-import.png) | ![Book Brief](docs/screens/02-book-brief.png) |
| **Names & style** | **Export** |
| ![Names and style](docs/screens/03-names-and-style.png) | ![Export](docs/screens/05-export.png) |

Settings has Providers (engine detection, connection tests), Models (per-model facts and overrides), Generation,
Automation, Appearance and Storage & logs: ![Settings, Models tab](docs/screens/06-settings-models.png)

The screens show invented sample books.

## Build and run

JDK 25 is required and is not downloaded for you. Gradle comes from the committed wrapper; always use `./gradlew`.

```bash
./gradlew :app:run                          # the desktop app
./gradlew build                             # compile, lint and test
./gradlew clean build check spotlessCheck   # the full gate (about 20 minutes); what CI and pre-push run
```

Headless, with the same pipeline (quote a path that contains spaces):

```bash
./gradlew -q :app:translate --args="'/path/to/book.epub' --to uk --provider ollama --model <model>"
```

A provider that needs a key stores only the name of an environment variable; see
[provider keys](docs/development/provider-keys.md). First-time setup, IDE, packaging and troubleshooting:
[docs/development/getting-started.md](docs/development/getting-started.md).

## Built with

| | |
|---|---|
| Language and UI | Java 25, JavaFX 26, a custom token-based theme |
| Build | Gradle (Kotlin DSL) with convention plugins in `modules/build-logic`, JPMS modules |
| Injection | Guice 7 |
| Documents | jsoup, JDOM2, commonmark, ICU4J |
| Models | HTTP clients for Ollama's native API and OpenAI-compatible servers (JDK `java.net.http`, Jackson) |
| Storage | SQLite (WAL) through JDBI, state kept as JSON documents |
| Verification | JUnit 5, AssertJ, WireMock, TestFX on the headless platform, ArchUnit, JaCoCo, Spotless, Checkstyle, Error Prone with NullAway, SonarQube (local) |

Dependency versions are in `gradle/libs.versions.toml`.

## Repository layout

```
modules/            api  util  document  llm  persistence  pipeline  ui  app  build-logic
docs/               architecture, pipeline, development, evaluation, project, screens, spec_baseline
openspec/           live behavioural specs (specs/) and the changes that produced them (changes/)
scripts/            gate helpers, packaging, evaluation and fixture tools
AGENTS.md           operating manual for contributors and coding agents
```

The dependency rule is one-way: `:app` and `:ui` (the only modules that see JavaFX) sit above `:pipeline`, which sits
above the services `:document`, `:llm` and `:persistence`, which sit above `:util` and `:api`. See the [repository
map](docs/project/repository-map.md).

## Documentation

Start at [docs/README.md](docs/README.md). The short list:

| Topic | Document |
|---|---|
| Architecture and design | [docs/architecture/overview.md](docs/architecture/overview.md) |
| How translation works | [docs/pipeline/translation-pipeline.md](docs/pipeline/translation-pipeline.md) |
| Build, run, test | [docs/development/getting-started.md](docs/development/getting-started.md) |
| Evaluation of prompts and models | [docs/evaluation/README.md](docs/evaluation/README.md) |
| Scripts, CI and hooks | [docs/project/scripts.md](docs/project/scripts.md), [docs/project/ci-and-hooks.md](docs/project/ci-and-hooks.md) |
| Known limits | [docs/known-limits.md](docs/known-limits.md) |
| Contributing rules | [AGENTS.md](AGENTS.md) |

## Status and limits

The application is feature complete for the workflow above; the last change, `redesign-translation-pipeline`, was archived on 2026-11-12. Its quality is good enough to read and edit, not
proven better than the previous pipeline, and some limits are accepted: narrator gender slips, source-language words
left in Russian to Ukrainian output, and names stored in more than one form. They are listed with their evidence in
[docs/known-limits.md](docs/known-limits.md).

## License

MIT. See [LICENSE](LICENSE).
