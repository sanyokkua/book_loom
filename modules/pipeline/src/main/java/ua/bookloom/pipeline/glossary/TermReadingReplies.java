package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.TermReading.Kind;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.Window;

/**
 * Reads one term-reading reply (15i.N4). Each window's answer is read once, by its number, and only for a window the
 * call showed; a label outside the schema's lists reads as unsure or other; the term's flags read as false unless the
 * reply says true. A window's gender vote stands only when {@link QuoteVote} finds its quote in that very window,
 * outside speech and, with an analysis language, holding a pronoun of the claimed gender — so a pronoun quoted from
 * another window, from a speaker's line or about someone else never votes.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermReadingReplies {

    private static final String NONE = "none";
    private static final String MARK_OPEN = "[[";
    private static final String MARK_CLOSE = "]]";

    /**
     * Reads a reply against the windows it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param reply the model's reply
     * @param term the term the call read
     * @param windows the windows the call showed, window {@code n} as number {@code n + 1}
     * @param analysisLanguage the language the code reads the book in, which picks the pronouns; null under Auto
     * @return the reading; {@link TermReading.Reading#none(String)} when the reply is unreadable
     */
    static Reading read(
            final ObjectMapper mapper,
            final String reply,
            final String term,
            final List<Window> windows,
            @Nullable final String analysisLanguage) {
        final JsonNode root = tree(mapper, reply);
        if (root == null || !root.path("windows").isArray()) {
            log.warn("Term reading reply for {} could not be read; it reads nothing", term);
            return Reading.none(term);
        }
        final QuoteVote votes = QuoteVote.of(analysisLanguage);
        final List<WindowReading> read = new ArrayList<>();
        final Set<Integer> answered = new HashSet<>();
        for (final JsonNode node : root.path("windows")) {
            final int number = node.path("window").asInt(0);
            if (number < 1 || number > windows.size() || !answered.add(number)) {
                log.debug(
                        "Term reading of {}: window {} dropped (shown 1..{} or answered twice)",
                        term,
                        number,
                        windows.size());
                continue;
            }
            read.add(windowReading(node, number, windows.get(number - 1), votes));
        }
        final Reading reading = new Reading(
                term,
                read,
                root.path("speaking").asBoolean(false),
                root.path("brand").asBoolean(false));
        log.trace("Term reading of {} read as {}", term, reading);
        return reading;
    }

    private static WindowReading windowReading(
            final JsonNode node, final int number, final Window window, final QuoteVote votes) {
        final String quote = quoteOf(node.path("quote").asText(""));
        final Gender claimed = genderOf(node.path("gender").asText(""));
        final Gender vote = votes.vote(quote, claimed, window.text());
        log.debug(
                "Term reading window {}: usage={} kind={} claimed={} vote={}",
                number,
                node.path("usage").asText(""),
                node.path("kind").asText(""),
                claimed,
                vote);
        return new WindowReading(
                number,
                quote,
                usageOf(node.path("usage").asText("")),
                kindOf(node.path("kind").asText("")),
                claimed,
                vote);
    }

    // The marks the prompt puts round the term are no part of the book; "none" is no quote.
    static String quoteOf(final String raw) {
        final String quote = raw.replace(MARK_OPEN, "").replace(MARK_CLOSE, "").strip();
        return NONE.equalsIgnoreCase(quote) ? "" : quote;
    }

    static Gender genderOf(final String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "male" -> Gender.MALE;
            case "female" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }

    static Usage usageOf(final String word) {
        final String wire = word.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(Usage.values())
                .filter(usage -> usage != Usage.UNSURE && usage.wire().equals(wire))
                .findFirst()
                .orElse(Usage.UNSURE);
    }

    static Kind kindOf(final String word) {
        final String wire = word.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(Kind.values())
                .filter(kind -> kind.wire().equals(wire))
                .findFirst()
                .orElse(Kind.OTHER);
    }

    // A small model may wrap its object in a code fence or a sentence: the object runs from the first brace to the
    // last.
    static @Nullable JsonNode tree(final ObjectMapper mapper, final String reply) {
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        if (from < 0 || to <= from) {
            return null;
        }
        try {
            return mapper.readTree(reply.substring(from, to + 1));
        } catch (IOException unreadable) {
            log.debug(
                    "Term reading reply is no JSON object: {}",
                    unreadable.getClass().getSimpleName());
            return null;
        }
    }
}
