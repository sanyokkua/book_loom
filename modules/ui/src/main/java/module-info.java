/**
 * JavaFX presentation: FXML views and controllers, viewmodels, the observable state mirror, theming, i18n.
 *
 * <p>With {@code ua.bookloom.app}, one of only two modules that may {@code requires javafx.*}. It reaches the core
 * through {@code :pipeline} and {@code :api} only — never {@code :document}, {@code :llm} or {@code :persistence}
 * directly (docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction).
 */
module ua.bookloom.ui {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires ua.bookloom.pipeline;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.google.guice;

    exports ua.bookloom.ui;

    opens ua.bookloom.ui to
            com.google.guice,
            javafx.fxml;
}
