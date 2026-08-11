/**
 * Document parsing, inline masking, reassembly and repackaging. FX-free. Implements {@code DocumentPort}.
 *
 * <p>{@code opens} its implementation package to Guice so constructor injection can reflect on it — a JPMS module
 * without the {@code opens} fails at runtime, not compile time, which is a failure mode best avoided by never
 * having it (docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md).
 */
module ua.bookloom.document {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires com.google.guice;
    // The SLF4J facade — `DocumentService` is the port boundary that logs a classified failure once
    // (`.claude/rules/logging.md`). No binding is required here: `:app` supplies the Logback provider at runtime.
    requires org.slf4j;
    // EPUB body parsing (design.md D4). Kept out of every other module — a parser reaching outside :document
    // would let a book's DOM be touched from a screen (02_MODULES_AND_LAYERING.md#archunit-rules).
    requires org.jsoup;
    // Strict XML — container.xml, the OPF, and FB2 (design.md D4).
    requires org.jdom2;
    // Markdown ANALYSIS ONLY: the AST locates each leaf block's byte span and the renderer is never invoked
    // (design.md D4). GFM tables is the only extension, because tables are block-level and change segmentation
    // while strikethrough and autolink are inline and change no block boundary. Task 5.2 names the first of these
    // two; the extension module is required alongside it because a JPMS module cannot reach an extension's types
    // through the core module that does not re-export them.
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    // Charset detection on the import path. Ships as an automatic module (Automatic-Module-Name: com.ibm.icu),
    // which the `-Xlint:-requires-automatic` carve-out in `bookloom.java-conventions` already accounts for.
    // Kept out of every other module: a book's encoding is resolved once, here
    // (02_MODULES_AND_LAYERING.md#archunit-rules).
    requires com.ibm.icu;

    exports ua.bookloom.document;

    // Every package Guice must reflect on to construct a reader, a writer or a registry. `document` alone was
    // enough while EPUB was the only format; the three format packages hold injectable services of their own now,
    // and a missing `opens` fails at runtime rather than at compile time
    // (02_Architecture/10_DI_AND_LIFECYCLE.md).
    opens ua.bookloom.document to
            com.google.guice;
    opens ua.bookloom.document.epub to
            com.google.guice;
    opens ua.bookloom.document.fb2 to
            com.google.guice;
    opens ua.bookloom.document.md to
            com.google.guice;
    opens ua.bookloom.document.txt to
            com.google.guice;
}
