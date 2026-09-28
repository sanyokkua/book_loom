package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The one display-text rule the checks, name scans, memory and events share, so they never disagree on length. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisplayText {

    private static final Pattern PLACEHOLDER = Pattern.compile("⟦g\\d+⟧");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Strips placeholder tokens from masked text and normalizes its whitespace.
     *
     * @param masked the masked segment text; never null
     * @return the text without any {@code ⟦gN⟧} token, each whitespace run one space, trimmed; possibly empty
     */
    public static String of(final String masked) {
        Objects.requireNonNull(masked, "masked");
        return WHITESPACE
                .matcher(PLACEHOLDER.matcher(masked).replaceAll(""))
                .replaceAll(" ")
                .strip();
    }
}
