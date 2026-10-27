/**
 * The translation engine: chunking, context assembly, the tiered translate/QA/review/self-heal loop. FX-free.
 * Implements {@code TranslationEngine}.
 *
 * <p>The single orchestration seam {@code :ui} talks to, which is why it is the only core module allowed to depend
 * on {@code :document}, {@code :llm} and {@code :persistence} together.
 */
module ua.bookloom.pipeline {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires ua.bookloom.document;
    requires ua.bookloom.llm;
    requires ua.bookloom.persistence;
    requires com.google.guice;
    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;

    exports ua.bookloom.pipeline;

    opens ua.bookloom.pipeline to
            com.google.guice;
    opens ua.bookloom.pipeline.prompt to
            com.google.guice;
    opens ua.bookloom.pipeline.project to
            com.google.guice;
    opens ua.bookloom.pipeline.reviewer to
            com.google.guice;
    opens ua.bookloom.pipeline.heal to
            com.google.guice;
    opens ua.bookloom.pipeline.lexicon to
            com.google.guice;
    opens ua.bookloom.pipeline.glossary to
            com.google.guice;
    opens ua.bookloom.pipeline.export to
            com.google.guice;
    opens ua.bookloom.pipeline.run to
            com.google.guice;
    opens ua.bookloom.pipeline.review to
            com.google.guice;
    opens ua.bookloom.pipeline.audit to
            com.google.guice;
    opens ua.bookloom.pipeline.revision to
            com.google.guice;
    opens ua.bookloom.pipeline.setup to
            com.google.guice;
    opens ua.bookloom.pipeline.batch to
            com.google.guice;
    opens ua.bookloom.pipeline.checks to
            com.google.guice;
    opens ua.bookloom.pipeline.chunk to
            com.google.guice;
    opens ua.bookloom.pipeline.context to
            com.google.guice;
    opens ua.bookloom.pipeline.dial to
            com.google.guice;
    opens ua.bookloom.pipeline.index to
            com.google.guice;
    opens ua.bookloom.pipeline.labels to
            com.google.guice;
    opens ua.bookloom.pipeline.memory to
            com.google.guice;
    opens ua.bookloom.pipeline.narrator to
            com.google.guice;
    opens ua.bookloom.pipeline.qa to
            com.google.guice;
    opens ua.bookloom.pipeline.typography to
            com.google.guice;
}
