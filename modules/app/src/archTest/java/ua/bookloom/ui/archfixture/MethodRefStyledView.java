package ua.bookloom.ui.archfixture;

import java.util.function.BiConsumer;
import javafx.scene.control.Label;

/**
 * Violation fixture for {@code no-inline-style-in-ui}: {@code setStyle} taken as a method reference instead of
 * called directly, which a call-only rule would let through.
 */
public final class MethodRefStyledView {

    public BiConsumer<Label, String> styler() {
        return Label::setStyle;
    }
}
