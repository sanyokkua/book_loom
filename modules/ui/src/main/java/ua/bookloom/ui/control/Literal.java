package ua.bookloom.ui.control;

import javafx.scene.control.Labeled;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shows a control's words exactly as given. A button, radio, toggle or check box reads an underscore in its text as a
 * keyboard mnemonic and drops it, so a model id or a name from the book ({@code my_model_q4}) must be marked literal;
 * BookLoom uses no mnemonics.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Literal {

    /**
     * Turns mnemonic parsing off.
     *
     * @param labeled the control that shows data
     * @param <T> the control's type
     * @return {@code labeled}, for chaining
     */
    public static <T extends Labeled> T text(final T labeled) {
        labeled.setMnemonicParsing(false);
        return labeled;
    }
}
