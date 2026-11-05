package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;

/** The placeholder sequence a translation of a masked text must return, as the repair and fix prompts state it. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExpectedTokens {

    static final String NO_TOKENS = "(none — write no ⟦gN⟧ token at all; write every name as plain text)";

    /**
     * Returns every placeholder in source order within an already-masked text; the expected sequence comes from the
     * source rather than from a rejected target.
     *
     * @param masked the non-null masked source text
     * @return the tokens joined by single spaces, or the words saying there is none
     */
    public static String sequence(final String masked) {
        Objects.requireNonNull(masked, "masked");
        final List<String> tokens = Tokens.inOrder(masked);
        return tokens.isEmpty() ? NO_TOKENS : String.join(" ", tokens);
    }
}
