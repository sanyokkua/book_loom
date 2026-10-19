package ua.bookloom.pipeline.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.JsonReplies;

/**
 * Reads one garbled-word reply and keeps only what the text proves. A model that invents a word it was never shown, or
 * quotes a phrase the text does not hold, is a model guessing, so such an entry is dropped: a finding stands only when
 * its word is a whole word of the text it names and its quote, when given, is words of that text that include it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuspiciousWordsReply {

    private static final String NOTE =
            "This word is not a real word and may be garbled or invented. Check it and use the real word.";

    /**
     * Reads a reply against the texts it answers.
     *
     * @param mapper the tolerant JSON reader's mapper
     * @param replyText the model's reply
     * @param targets the texts that were numbered {@code 1..n}, in order
     * @return per text, the findings that survived verification, in text order; each list is empty when nothing
     *     survived; always as many lists as texts
     */
    static List<List<CheckFinding>> read(
            final ObjectMapper mapper, final String replyText, final List<String> targets) {
        final List<List<CheckFinding>> found = new ArrayList<>();
        targets.forEach(target -> found.add(new ArrayList<>()));
        final JsonNode words = JsonReplies.tolerant(mapper, replyText)
                .map(root -> root.path("words"))
                .orElse(null);
        if (words == null || !words.isArray()) {
            log.debug("Garbled-word reply holds no words array; read as no words");
            return List.copyOf(found.stream().map(List::copyOf).toList());
        }
        int dropped = 0;
        for (final JsonNode node : words) {
            if (!keep(node, targets, found)) {
                dropped++;
            }
        }
        log.debug("Garbled-word reply read: {} entries, {} dropped as unproven", words.size(), dropped);
        return List.copyOf(found.stream().map(List::copyOf).toList());
    }

    private static boolean keep(final JsonNode node, final List<String> targets, final List<List<CheckFinding>> found) {
        final int index = indexOf(node.path("id").asText(""), targets.size());
        final String word = node.path("word").asText("").strip();
        if (index < 0 || word.isEmpty()) {
            return false;
        }
        final String target = targets.get(index);
        final String quote = node.path("quote").asText("").strip();
        if (!quote.isEmpty() && !holds(target, quote, word)) {
            log.trace("Garbled-word quote '{}' is not in the text, entry dropped", quote);
            return false;
        }
        final TextSpan span = spanOf(target, word).orElse(null);
        if (span == null || found.get(index).stream().anyMatch(f -> f.span().start() == span.start())) {
            return false;
        }
        found.get(index).add(new CheckFinding(FindingKind.UNKNOWN_WORD, span, NOTE, false));
        log.trace("Garbled-word finding '{}' at {}..{}", word, span.start(), span.end());
        return true;
    }

    private static int indexOf(final String id, final int count) {
        try {
            final int number = Integer.parseInt(id.strip());
            return number >= 1 && number <= count ? number - 1 : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static boolean holds(final String target, final String quote, final String word) {
        final String lowerQuote = quote.toLowerCase(Locale.ROOT);
        return target.toLowerCase(Locale.ROOT).contains(lowerQuote)
                && lowerQuote.contains(word.toLowerCase(Locale.ROOT));
    }

    // The first whole word of the text that is the word, so a word inside a longer word proves nothing.
    private static Optional<TextSpan> spanOf(final String target, final String word) {
        final Matcher matcher = Words.WORD.matcher(target);
        while (matcher.find()) {
            if (matcher.group().equalsIgnoreCase(word)) {
                return Optional.of(new TextSpan(matcher.start(), matcher.end(), matcher.group()));
            }
        }
        return Optional.empty();
    }
}
