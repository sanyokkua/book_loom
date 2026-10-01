package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tells a text a person can see from one made only of spaces and invisible marks (a no-break space, a zero-width space,
 * a byte-order mark, a soft hyphen), which {@link String#isBlank()} counts as text.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VisibleText {

    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Z}\\p{Cc}\\p{Cf}\\s]+");

    /**
     * Whether the text shows nothing.
     *
     * @param text the non-null text
     * @return {@code true} if it holds only separators, controls and format marks, {@code false} otherwise
     */
    public static boolean isBlank(final String text) {
        Objects.requireNonNull(text, "text");
        return INVISIBLE.matcher(text).replaceAll("").isEmpty();
    }
}
