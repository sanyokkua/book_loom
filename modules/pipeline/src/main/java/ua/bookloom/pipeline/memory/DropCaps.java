package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.Tokens;

/**
 * Finds a segment's drop caps — a pair wrapping one or two visible characters at the start of a word, glued to its
 * rest, as in
 * {@code ⟦g0⟧“A⟦g1⟧bove} — so the model is shown the whole word ({@code “Above}) and the pair is put back by position
 * after translation ({@code ⟦g0⟧«П⟦g1⟧онад}). Shown with its tokens, the word is cut in two; a small model then
 * translates the halves, drops a token, or wraps the whole paragraph in the drop cap's style.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DropCaps {

    /** The most visible characters a drop cap's pair wraps: an opening quote and a letter. */
    private static final int MAX_WRAPPED = 2;

    /**
     * The tokens of every drop-cap pair of {@code segment}, opening token first.
     *
     * @param segment the non-null segment
     * @return the tokens to fold out of the text the model is shown; empty when the segment has no drop cap
     */
    static List<String> tokensOf(final Segment segment) {
        Objects.requireNonNull(segment, "segment");
        final List<String> tokens = new ArrayList<>();
        for (final PlaceholderPair pair : segment.pairs()) {
            if (pair.language() == null && isDropCap(segment.masked(), pair)) {
                tokens.add(pair.open());
                tokens.add(pair.close());
            }
        }
        if (!tokens.isEmpty() && log.isTraceEnabled()) {
            log.trace("Drop caps segment={} foldedTokens={}", segment.id(), tokens);
        }
        return List.copyOf(tokens);
    }

    /**
     * {@code text} without the given tokens.
     *
     * @param text the non-null text
     * @param tokens the non-null tokens to take out
     * @return the text with every occurrence of each token removed
     */
    static String without(final String text, final List<String> tokens) {
        String result = text;
        for (final String token : tokens) {
            result = result.replace(token, "");
        }
        return result;
    }

    /**
     * Whether {@code text} lacks exactly the folded tokens and holds every other token of {@code expectedMasked} as
     * often as it does — a reply that only needs its drop caps put back, which is no repair a person must check.
     *
     * @param expectedMasked the non-null segment's masked form
     * @param text the non-null reply with its protected spans put back
     * @param folded the non-null folded tokens
     * @return {@code true} if only the folded tokens are missing
     */
    static boolean onlyFoldedMissing(final String expectedMasked, final String text, final List<String> folded) {
        return multiset(without(expectedMasked, folded)).equals(multiset(text));
    }

    private static Map<String, Long> multiset(final String text) {
        return Tokens.inOrder(text).stream().collect(Collectors.groupingBy(token -> token, Collectors.counting()));
    }

    private static boolean isDropCap(final String masked, final PlaceholderPair pair) {
        final int open = masked.indexOf(pair.open());
        final int close = masked.indexOf(pair.close());
        if (open < 0 || close < open) {
            return false;
        }
        final String wrapped = masked.substring(open + pair.open().length(), close);
        final int end = close + pair.close().length();
        final boolean gluedAfter = end < masked.length() && Character.isLetter(masked.codePointAt(end));
        final boolean gluedBefore = open > 0 && Character.isLetter(masked.codePointBefore(open));
        return gluedAfter && !gluedBefore && isShortVisible(wrapped);
    }

    private static boolean isShortVisible(final String wrapped) {
        final long visible = wrapped.codePoints()
                .filter(point -> !Character.isWhitespace(point))
                .count();
        final boolean hasLetter = wrapped.codePoints().anyMatch(Character::isLetter);
        return Tokens.inOrder(wrapped).isEmpty() && visible >= 1 && visible <= MAX_WRAPPED && hasLetter;
    }
}
