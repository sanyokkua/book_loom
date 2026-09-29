package ua.bookloom.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The one {@code ⟦gN⟧} placeholder-token pattern, so no class keeps its own copy of it. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Tokens {

    private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");

    /**
     * Every placeholder token of a text.
     *
     * @param text any text, masked or not; never null
     * @return the tokens in text order, repeats included; never null, empty when there are none
     */
    public static List<String> inOrder(final String text) {
        Objects.requireNonNull(text, "text");
        final Matcher matcher = TOKEN.matcher(text);
        final List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * Replaces every placeholder token of a text.
     *
     * @param text any text, masked or not; never null
     * @param replacement the text each token becomes; taken literally, never null
     * @return the text with each token replaced
     */
    public static String replace(final String text, final String replacement) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(replacement, "replacement");
        return TOKEN.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }
}
