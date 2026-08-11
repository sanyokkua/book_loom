/**
 * Small stateless helpers. Depends on {@code ua.bookloom.api} and nothing else internal.
 *
 * <p>Deliberately without {@code com.google.guice}: {@code ua.bookloom.util.paths} runs pre-injector, before the
 * Guice injector is built and before logging is configured (DD-39, ADR-0015).
 */
module ua.bookloom.util {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@NoArgsConstructor` on the `util.hash` static-utility class into an
    // ordinary private constructor and leaves nothing behind at runtime (DD-05, ADR-0014, ADR-0024).
    requires static lombok;
    requires ua.bookloom.api;

    exports ua.bookloom.util.paths;
    exports ua.bookloom.util.hash;
}
