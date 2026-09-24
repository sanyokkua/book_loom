/**
 * Provider abstraction, inference, response handling, the single-flight gate and retry. FX-free.
 *
 * <p>The only module permitted to import {@code java.net.http} — the structural seed of the offline invariant:
 * "only one module can open a socket" is a compile-gate fact, not a promise (DD-01).
 */
module ua.bookloom.llm {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires java.net.http;
    requires com.google.guice;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;

    exports ua.bookloom.llm;

    opens ua.bookloom.llm to
            com.google.guice;
    opens ua.bookloom.llm.dto to
            com.fasterxml.jackson.databind;
    opens ua.bookloom.llm.verify to
            com.google.guice;
}
