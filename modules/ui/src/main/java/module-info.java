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
    // `@BuildVersion` is a JSR-330 qualifier; Guice honours it but does not re-export the annotation package.
    requires jakarta.inject;
    requires org.slf4j;
    // ICU MessageFormat for the catalogue's plural forms. Ships as an automatic module (Automatic-Module-Name:
    // com.ibm.icu), which the `-Xlint:-requires-automatic` carve-out in `bookloom.java-conventions` accounts for.
    requires com.ibm.icu;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    exports ua.bookloom.ui;

    opens ua.bookloom.ui to
            com.google.guice,
            javafx.fxml;
    opens ua.bookloom.ui.i18n to
            com.google.guice,
            javafx.fxml;
    opens ua.bookloom.ui.notify to
            com.google.guice,
            javafx.fxml;
    opens ua.bookloom.ui.screen to
            com.google.guice,
            javafx.fxml;
    opens ua.bookloom.ui.state to
            com.google.guice,
            javafx.fxml;
    opens ua.bookloom.ui.theme to
            com.google.guice,
            javafx.fxml;
}
