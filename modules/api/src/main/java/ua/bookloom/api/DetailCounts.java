package ua.bookloom.api;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads back the token counts {@link SafeDetails#withTokenCounts(int, int)} rendered, so a flag can say "696 of 512
 * tokens" from the typed counts and never from the provider's sentence.
 */
public enum DetailCounts {
// A constant-less enum, so nothing can make an instance: :api has no Lombok for ADR-0024's private constructor,
// and the records-first rule holds every top-level class in an api package to be a record.
;

    private static final Pattern PROMPT_TOKENS = Pattern.compile("(?:\\A|, )promptTokens=(\\d+)");
    private static final Pattern WINDOW_TOKENS = Pattern.compile("(?:\\A|, )windowTokens=(\\d+)");

    /**
     * Reads the tokens a prompt that did not fit needed.
     *
     * @param details the rendered details, or {@code null}
     * @return the count if the details hold one, or empty if they do not
     */
    public static OptionalInt promptTokensIn(@Nullable String details) {
        return countIn(PROMPT_TOKENS, details);
    }

    /**
     * Reads the tokens the model's window holds.
     *
     * @param details the rendered details, or {@code null}
     * @return the count if the details hold one, or empty if they do not
     */
    public static OptionalInt windowTokensIn(@Nullable String details) {
        return countIn(WINDOW_TOKENS, details);
    }

    private static OptionalInt countIn(Pattern key, @Nullable String details) {
        if (details == null) {
            return OptionalInt.empty();
        }
        final Matcher found = key.matcher(details);
        // A digit run longer than an int holds is not a count SafeDetails rendered.
        try {
            return found.find() ? OptionalInt.of(Integer.parseInt(found.group(1))) : OptionalInt.empty();
        } catch (NumberFormatException _) {
            return OptionalInt.empty();
        }
    }
}
