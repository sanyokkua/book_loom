package ua.bookloom.ui.screen;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** A primary-button click event to fire at a node a test cannot reach with the pointer. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RowClicks {

    static MouseEvent primary() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }
}
