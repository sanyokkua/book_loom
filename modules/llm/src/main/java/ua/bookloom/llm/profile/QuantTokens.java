package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds quant tokens in a lower-cased name. A token sits between name separators; each family of tokens has its own
 * small pattern, so no one expression has to hold them all.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuantTokens {

    private static final String BEFORE = "(?:^|(?<=[-_.:@]))";
    private static final String AFTER = "(?=$|[-_.:@])";

    private static final List<Pattern> FAMILIES = Stream.of(
                    "iq\\d(?:_[a-z]+)?|tq\\d_0",
                    "q\\d(?:_k)?(?:_[sml]|_xl|_[01]|_nl)?(?:_\\d_\\d)?",
                    "bf16|fp16|f16|fp32|f32|fp8|nvfp4|mxfp4(?:_moe)?|mxfp8",
                    "int[48]|[2-8]bit|w4a16(?:-ct)?|w8a8")
            .map(body -> Pattern.compile(BEFORE + "((?:ud-)?(?:" + body + "))" + AFTER))
            .toList();

    /**
     * Where a token sits in a name.
     *
     * @param start the index of its first character
     * @param end the index after its last character
     */
    record Span(int start, int end) {}

    /**
     * The tokens of a name, left to right.
     *
     * @param low the lower-cased name; never null
     * @return the tokens that do not overlap, never null; empty when the name holds none
     */
    static List<Span> find(final String low) {
        final List<Span> all = new ArrayList<>();
        for (final Pattern family : FAMILIES) {
            final Matcher matcher = family.matcher(low);
            while (matcher.find()) {
                all.add(new Span(matcher.start(1), matcher.end(1)));
            }
        }
        all.sort(Comparator.comparingInt(Span::start));
        final List<Span> kept = new ArrayList<>();
        for (final Span span : all) {
            if (kept.isEmpty() || span.start() >= kept.getLast().end()) {
                kept.add(span);
            }
        }
        return kept;
    }

    /**
     * Whether a whole text is one token.
     *
     * @param low the lower-cased text; never null
     * @return {@code true} if the text is exactly one quant token, {@code false} otherwise
     */
    static boolean isToken(final String low) {
        return FAMILIES.stream().anyMatch(family -> family.matcher(low).matches());
    }

    /**
     * The name with each token replaced.
     *
     * @param low the lower-cased name; never null
     * @param replacement the text put where a token was
     * @return the name with every token from {@link #find(String)} replaced
     */
    static String replaceAll(final String low, final String replacement) {
        final StringBuilder out = new StringBuilder();
        int copied = 0;
        for (final Span span : find(low)) {
            out.append(low, copied, span.start()).append(replacement);
            copied = span.end();
        }
        return out.append(low, copied, low.length()).toString();
    }
}
