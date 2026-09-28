package ua.bookloom.document.mask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;

/**
 * Reads the order of a segment's paired tokens in one text: whether each pair opens before it closes and nests with
 * the others, whether a pair still holds text, and which pair each line break sits in (task 5.8, ADR-0040).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PairStructure {

    /**
     * Whether the pairs' tokens in {@code text} form a properly nested sequence: each closing token meets its own
     * pair as the innermost open one. Atomic tokens are ignored.
     *
     * @param text a masked form or a target
     * @param pairs the segment's pairs
     * @return {@code true} if every pair opens before it closes and no two overlap
     */
    static boolean isProperlyNested(String text, List<PlaceholderPair> pairs) {
        final Map<String, PlaceholderPair> byOpen = new HashMap<>();
        final Map<String, PlaceholderPair> byClose = new HashMap<>();
        index(pairs, byOpen, byClose);
        final Deque<PlaceholderPair> open = new ArrayDeque<>();
        for (final String token : Placeholders.tokensOf(text)) {
            if (byOpen.containsKey(token)) {
                open.push(byOpen.get(token));
            } else if (byClose.containsKey(token) && !byClose.get(token).equals(open.poll())) {
                return false;
            }
        }
        return open.isEmpty();
    }

    /**
     * Whether {@code pair} holds a non-whitespace character outside tokens in {@code text}. A pair whose tokens are
     * not both present holds none.
     *
     * @param text a masked form or a target
     * @param pair the pair to inspect
     * @return {@code true} if text stands between the pair's tokens
     */
    static boolean holdsText(String text, PlaceholderPair pair) {
        final int from = text.indexOf(pair.open());
        final int to = text.indexOf(pair.close());
        if (from < 0 || to < from) {
            return false;
        }
        final String between = text.substring(from + pair.open().length(), to);
        return !between.replaceAll("⟦g\\d+⟧", "").isBlank();
    }

    /**
     * The innermost pair open at each line-break token of {@code lineBreakTokens} in {@code text}.
     *
     * @param text a masked form or a target whose pairs are properly nested
     * @param pairs the segment's pairs
     * @param lineBreakTokens the segment's line-break tokens
     * @return a map from each line-break token present in {@code text} to its innermost pair, or to {@code null}
     *     when it sits outside every pair
     */
    static Map<String, @Nullable PlaceholderPair> enclosingPairs(
            String text, List<PlaceholderPair> pairs, List<String> lineBreakTokens) {
        final Map<String, PlaceholderPair> byOpen = new HashMap<>();
        final Map<String, PlaceholderPair> byClose = new HashMap<>();
        index(pairs, byOpen, byClose);
        final Deque<PlaceholderPair> open = new ArrayDeque<>();
        final Map<String, @Nullable PlaceholderPair> enclosing = new HashMap<>();
        for (final String token : Placeholders.tokensOf(text)) {
            if (byOpen.containsKey(token)) {
                open.push(byOpen.get(token));
            } else if (byClose.containsKey(token)) {
                open.poll();
            } else if (lineBreakTokens.contains(token)) {
                enclosing.put(token, open.peek());
            }
        }
        return enclosing;
    }

    private static void index(
            List<PlaceholderPair> pairs, Map<String, PlaceholderPair> byOpen, Map<String, PlaceholderPair> byClose) {
        for (final PlaceholderPair pair : pairs) {
            byOpen.put(pair.open(), pair);
            byClose.put(pair.close(), pair);
        }
    }
}
