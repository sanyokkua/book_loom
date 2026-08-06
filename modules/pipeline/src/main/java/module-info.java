/**
 * The translation engine: chunking, context assembly, the tiered translate/QA/judge/self-heal loop. FX-free.
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

    exports ua.bookloom.pipeline;

    opens ua.bookloom.pipeline to
            com.google.guice;
}
