package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The {@code ⟦gN⟧} formatting tokens one text holds that another lacks, for the review editor's token banner. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewTokens {

    private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");

    /**
     * The tokens of {@code from} that {@code in} lacks, counting repeats.
     *
     * @param from the non-null text whose tokens are wanted
     * @param in the non-null text checked for them
     * @return each token of {@code from} as many times as {@code in} lacks it, in {@code from}'s order; empty if none
     */
    static List<String> missing(final String from, final String in) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(in, "in");
        final Map<String, Integer> available = new HashMap<>();
        tokensOf(in).forEach(token -> available.merge(token, 1, Integer::sum));
        final List<String> missing = new ArrayList<>();
        for (final String token : tokensOf(from)) {
            if (available.getOrDefault(token, 0) > 0) {
                available.merge(token, -1, Integer::sum);
            } else {
                missing.add(token);
            }
        }
        return List.copyOf(missing);
    }

    private static List<String> tokensOf(final String text) {
        final List<String> tokens = new ArrayList<>();
        final Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
