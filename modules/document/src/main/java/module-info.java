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

    exports ua.bookloom.document;

    opens ua.bookloom.document to
            com.google.guice;
}
