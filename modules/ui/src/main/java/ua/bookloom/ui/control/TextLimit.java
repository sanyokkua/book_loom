package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Caps how many characters a free-text field takes: typing stops at the cap and a paste keeps only what fits. A field
 * the brief sends with every model call is capped, so a pasted page cannot be sent hundreds of times.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextLimit {

    /**
     * Caps {@code input} at {@code max} characters.
     *
     * @param input the field; its own text formatter is replaced
     * @param max the most characters it takes, at least one
     * @param <T> the field's type
     * @return {@code input}
     */
    public static <T extends TextInputControl> T of(final T input, final int max) {
        Objects.requireNonNull(input, "input");
        input.setTextFormatter(new TextFormatter<String>(change -> capped(change, max)));
        return input;
    }

    private static TextFormatter.Change capped(final TextFormatter.Change change, final int max) {
        final int over = change.getControlNewText().length() - max;
        if (over <= 0 || !change.isContentChange()) {
            return change;
        }
        final String added = change.getText();
        int keep = Math.max(0, added.length() - over);
        if (keep > 0 && Character.isHighSurrogate(added.charAt(keep - 1))) {
            keep--;
        }
        change.setText(added.substring(0, keep));
        // The caret follows the shortened insertion instead of pointing past the end of the text.
        final int caret = change.getRangeStart() + keep;
        change.selectRange(caret, caret);
        return change;
    }
}
