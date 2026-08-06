package ua.bookloom.document.archfixture;

import javafx.scene.control.Label;

/**
 * Violation fixture for {@code fx-free-core}: a {@code :document} class depending on JavaFX. The core must run
 * headless under JUnit and WireMock; a toolkit dependency here would make the parser untestable without a
 * display (DD-06).
 */
public final class FxDependentParser {

    public Label render(String text) {
        return new Label(text);
    }
}
