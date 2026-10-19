package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.pipeline.Tokens;

/**
 * The note a placeholder repair call is given: which tokens the rejected target lost, added or put out of order, and
 * what each affected pair wraps in the text, so a small model is told exactly what to fix instead of a general rule.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GateNotes {

    /** The longest stretch of a pair's source text quoted in the note. */
    private static final int MAX_QUOTED = 60;

    /** The most pairs described, so a heavily formatted paragraph does not crowd out the text itself. */
    private static final int MAX_PAIRS = 3;

    /**
     * Describes what is wrong with {@code rejected}.
     *
     * @param expected the non-null text the model was shown, every token it must return in place
     * @param rejected the non-null reply the gate refused
     * @param pairs the non-null segment's pairs
     * @param ruleMessage the non-null message of the gate that refused the reply, stated first
     * @return the note, one statement per line; never blank
     */
    public static String describe(
            final String expected, final String rejected, final List<PlaceholderPair> pairs, final String ruleMessage) {
        return describe(expected, rejected, pairs, List.of(), ruleMessage);
    }

    /**
     * Describes what is wrong with {@code rejected}, saying of each missing line-break token that it ends a line.
     *
     * @param expected the non-null text the model was shown, every token it must return in place
     * @param rejected the non-null reply the gate refused
     * @param pairs the non-null segment's pairs
     * @param lineBreakTokens the non-null segment's line-break tokens
     * @param ruleMessage the non-null message of the gate that refused the reply, stated first
     * @return the note, one statement per line; never blank
     */
    public static String describe(
            final String expected,
            final String rejected,
            final List<PlaceholderPair> pairs,
            final List<String> lineBreakTokens,
            final String ruleMessage) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(rejected, "rejected");
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(lineBreakTokens, "lineBreakTokens");
        Objects.requireNonNull(ruleMessage, "ruleMessage");
        final Map<String, Integer> difference = difference(expected, rejected);
        final List<String> missing = tokensWith(difference, true);
        final List<String> invented = invented(expected, rejected);
        final List<String> extra = tokensWith(difference, false).stream()
                .filter(token -> invented.stream().noneMatch(token::startsWith))
                .toList();
        final List<PlaceholderPair> reversed = reversed(rejected, pairs);
        final boolean ruleOnly = missing.isEmpty() && extra.isEmpty() && invented.isEmpty() && reversed.isEmpty();
        final List<String> lines = new ArrayList<>(List.of(ruleMessage));
        addListed(lines, "Missing (put each back once): ", missing);
        addLineEnds(lines, missing, lineBreakTokens);
        addListed(lines, "Extra (remove): ", extra);
        invented.forEach(token -> lines.add(token + " is not a placeholder of this text; write names as plain text."));
        reversed.forEach(pair -> lines.add("Out of order: " + pair.close() + " comes before " + pair.open() + "."));
        affected(pairs, missing, reversed, ruleOnly).forEach(pair -> lines.add(wraps(expected, pair)));
        log.debug("Gate note missing={} extra={} invented={} outOfOrder={}", missing, extra, invented, reversed.size());
        return joined(lines);
    }

    private static void addLineEnds(
            final List<String> lines, final List<String> missing, final List<String> lineBreakTokens) {
        missing.stream()
                .filter(lineBreakTokens::contains)
                .forEach(token -> lines.add(token + " ends a line: keep it at the end of the same line as in <Text>."));
    }

    private static String joined(final List<String> lines) {
        final String note = String.join("\n", lines);
        if (log.isTraceEnabled()) {
            log.trace("Gate note lines={} text={}", lines.size(), note);
        }
        return note;
    }

    /** Each token's count in {@code expected} minus its count in {@code rejected}; zero counts left out. */
    private static Map<String, Integer> difference(final String expected, final String rejected) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        Tokens.inOrder(expected).forEach(token -> counts.merge(token, 1, Integer::sum));
        Tokens.inOrder(rejected).forEach(token -> counts.merge(token, -1, Integer::sum));
        counts.values().removeIf(count -> count == 0);
        return counts;
    }

    /**
     * The tokens of {@code rejected} that {@code expected} never holds, each once in reply order: a small model invents
     * one for a name it should have written out, so the note says so rather than only "remove".
     */
    private static List<String> invented(final String expected, final String rejected) {
        final List<String> known = Tokens.inOrder(expected);
        return Tokens.inOrder(rejected).stream()
                .filter(token -> !known.contains(token))
                .distinct()
                .toList();
    }

    private static List<String> tokensWith(final Map<String, Integer> difference, final boolean missing) {
        return difference.entrySet().stream()
                .filter(entry -> missing ? entry.getValue() > 0 : entry.getValue() < 0)
                .map(entry -> Math.abs(entry.getValue()) == 1
                        ? entry.getKey()
                        : entry.getKey() + " ×" + Math.abs(entry.getValue()))
                .toList();
    }

    private static void addListed(final List<String> lines, final String label, final List<String> tokens) {
        if (!tokens.isEmpty()) {
            lines.add(label + String.join(" ", tokens) + ".");
        }
    }

    private static List<PlaceholderPair> reversed(final String rejected, final List<PlaceholderPair> pairs) {
        return pairs.stream()
                .filter(pair -> rejected.contains(pair.open()) && rejected.contains(pair.close()))
                .filter(pair -> rejected.indexOf(pair.close()) < rejected.indexOf(pair.open()))
                .toList();
    }

    // The pairs a missing token or a reversal belongs to; every pair when the gate refused something else, such as a
    // pair emptied of its words or wrapped around the whole text.
    private static List<PlaceholderPair> affected(
            final List<PlaceholderPair> pairs,
            final List<String> missing,
            final List<PlaceholderPair> reversed,
            final boolean ruleOnly) {
        return pairs.stream()
                .filter(pair -> ruleOnly
                        || reversed.contains(pair)
                        || missing.contains(pair.open())
                        || missing.contains(pair.close()))
                .limit(MAX_PAIRS)
                .toList();
    }

    private static String wraps(final String expected, final PlaceholderPair pair) {
        final int open = expected.indexOf(pair.open());
        final int close = expected.indexOf(pair.close());
        if (open < 0 || close < open) {
            return "Keep " + pair.open() + " before " + pair.close() + ".";
        }
        final String inner = Tokens.replace(
                        expected.substring(open + pair.open().length(), close), "")
                .strip();
        final String quoted = inner.length() > MAX_QUOTED ? inner.substring(0, MAX_QUOTED) + "…" : inner;
        return "In <Text>, " + pair.open() + "…" + pair.close() + " wraps \"" + quoted
                + "\" — wrap the translation of exactly that text.";
    }
}
