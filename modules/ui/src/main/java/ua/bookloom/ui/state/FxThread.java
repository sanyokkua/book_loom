package ua.bookloom.ui.state;

import javafx.application.Platform;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Runs an outcome where the scene graph may be touched: at once on the FX thread, else later on it. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FxThread {

    static void run(final Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
