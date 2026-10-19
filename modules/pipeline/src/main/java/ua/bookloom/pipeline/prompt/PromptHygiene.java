package ua.bookloom.pipeline.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The no-garbage rule for what a prompt is given besides its instructions: an empty value, a placeholder such as
 * "(none)" and a repeated line cost a small model's window and invite it to echo them, so none reaches the prompt.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptHygiene {

    private static final Pattern FILLER =
            Pattern.compile("^[\\[(]?\\s*(none|n/a|nothing|empty|null|-+|—+)\\s*[])]?\\.?$", Pattern.CASE_INSENSITIVE);

    /**
     * Whether a line says nothing: blank, or only a placeholder such as "(none)", "n/a" or a dash.
     *
     * @param line the line to judge; never null
     * @return {@code true} if the line is empty or filler, {@code false} if it carries content
     */
    public static boolean isFiller(final String line) {
        final String trimmed = line.strip();
        return trimmed.isEmpty()
                || FILLER.matcher(trimmed.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Drops empty and filler lines and repeats, keeping the first of each in order.
     *
     * @param lines the candidate lines; never null
     * @return the clean lines; never null, empty if nothing is left
     */
    public static List<String> clean(final List<String> lines) {
        final Set<String> kept = new LinkedHashSet<>();
        for (final String line : lines) {
            if (!isFiller(line)) {
                kept.add(line);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * A single optional text with its filler removed.
     *
     * @param text the text, or null when there is none
     * @return the text, or null when it is null, blank or filler
     */
    public static @Nullable String cleanText(@Nullable final String text) {
        return text == null || isFiller(text) ? null : text;
    }
}
