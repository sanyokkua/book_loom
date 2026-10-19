package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.LexiconReading.Verdict;
import ua.bookloom.pipeline.index.Window;

/**
 * Reads one recurring-term reading reply and decides from it (15i.N7). Each window's answer is read once, by its
 * number, and only for a window the call showed; it counts only when its quote stands in that very window, so a model
 * that answers without reading counts for nothing. The code then keeps the term when most counted windows use it as a
 * term in one sense, drops it when most use it as an ordinary word or the windows show two senses or more, promotes it
 * when most name a thing, and decides nothing otherwise.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconReadingReplies {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String MARK_OPEN = "[[";
    private static final String MARK_CLOSE = "]]";
    private static final int ONE_SENSE = 1;

    /** How a window uses the term; the words are the schema's. */
    private enum Use {
        TERM("term"),
        WORD("word"),
        NAME("name");

        private final String wire;

        Use(final String wire) {
            this.wire = wire;
        }

        String wire() {
            return wire;
        }
    }

    /**
     * What a reply says once its windows are checked.
     *
     * @param terms the counted windows that use the term in its one special sense
     * @param words the counted windows that use it as an ordinary word
     * @param names the counted windows that use it as a proper name
     * @param senses how many senses the model says the windows show; at least 1
     * @param rendering the rendering the model offered, stripped; empty when none
     */
    record Counts(int terms, int words, int names, int senses, String rendering) {

        int counted() {
            return terms + words + names;
        }
    }

    /**
     * Reads a reply against the windows it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param reply the model's reply
     * @param term the term the call read
     * @param windows the windows the call showed, window {@code n} as number {@code n + 1}
     * @return the counts; null when the reply is no JSON object with a window list
     */
    static @Nullable Counts read(
            final ObjectMapper mapper, final String reply, final String term, final List<Window> windows) {
        final JsonNode root = TermReadingReplies.tree(mapper, reply);
        if (root == null || !root.path("windows").isArray()) {
            log.warn("Lexicon reading reply for a term could not be read; it decides nothing");
            log.trace("Lexicon reading reply for {} could not be read: {}", term, reply);
            return null;
        }
        final Set<Integer> answered = new HashSet<>();
        final Map<Use, Integer> usage = new EnumMap<>(Use.class);
        for (final JsonNode node : root.path("windows")) {
            final int number = node.path("window").asInt(0);
            if (number < 1 || number > windows.size() || !answered.add(number)) {
                log.debug("Lexicon reading window {} dropped (shown 1..{} or answered twice)", number, windows.size());
                continue;
            }
            countWindow(node, number, windows.get(number - 1), usage);
        }
        final Counts counts = new Counts(
                usage.getOrDefault(Use.TERM, 0),
                usage.getOrDefault(Use.WORD, 0),
                usage.getOrDefault(Use.NAME, 0),
                Math.max(ONE_SENSE, root.path("senses").asInt(ONE_SENSE)),
                root.path("rendering").asText("").strip());
        log.trace("Lexicon reading of {} read as {}", term, counts);
        return counts;
    }

    private static void countWindow(
            final JsonNode node, final int number, final Window window, final Map<Use, Integer> usage) {
        final String quote = TermReadingReplies.quoteOf(node.path("quote").asText(""));
        if (quote.isEmpty() || !stands(quote, window.text())) {
            log.debug(
                    "Lexicon reading window {}: the quote stands in no window text, so it counts for nothing", number);
            return;
        }
        final String label = node.path("usage").asText("").strip().toLowerCase(Locale.ROOT);
        final Use use = Arrays.stream(Use.values())
                .filter(candidate -> candidate.wire().equals(label))
                .findFirst()
                .orElse(null);
        log.debug("Lexicon reading window {}: usage={}", number, label);
        if (use != null) {
            usage.merge(use, 1, Integer::sum);
        }
    }

    private static boolean stands(final String quote, final String text) {
        return squeezed(text).contains(squeezed(quote));
    }

    private static String squeezed(final String text) {
        return WHITESPACE
                .matcher(text.replace(MARK_OPEN, "").replace(MARK_CLOSE, ""))
                .replaceAll(" ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Decides a term from what its reply said.
     *
     * @param counts the checked reply; never null
     * @return the verdict: undecided when no window counted or none of the uses is the majority
     */
    static Verdict verdictOf(final Counts counts) {
        final int counted = counts.counted();
        if (counted == 0) {
            return Verdict.UNDECIDED;
        }
        if (isMajority(counts.words(), counted) || counts.senses() > ONE_SENSE) {
            return Verdict.DROP;
        }
        if (isMajority(counts.names(), counted)) {
            return Verdict.PROMOTE;
        }
        return isMajority(counts.terms(), counted) ? Verdict.KEEP : Verdict.UNDECIDED;
    }

    private static boolean isMajority(final int share, final int counted) {
        return share * 2 > counted;
    }
}
