package ua.bookloom.pipeline.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * What the brief's synthesis call reads (15i.B4): the book's facts, the knowledge only when its verdict accepted it, and
 * one report per window holding the window's verified readings of genre, audience, voice, register and the narrator
 * with their quotes, and the quotes that name the narrator by a name nobody could verify yet, after the window's own
 * opening words, which the story shape's quotes are copied from (design D6). An answer the code refused never reaches
 * the model, so a quote the book lacks or a refused knowledge cannot steer it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefSynthesisInput {

    // Enough of a window's start to tell a continuing story from a new one, short enough for five reports.
    private static final int OPENING_WORDS = 40;
    private static final Pattern SPACES = Pattern.compile("\\s+");

    // Every field the synthesis decides or reads: the narrator's person tells it whether a gender is wanted.
    private static final List<BriefField> REPORTED = List.of(
            BriefField.GENRE,
            BriefField.AUDIENCE,
            BriefField.VOICE,
            BriefField.REGISTER,
            BriefField.NARRATOR,
            BriefField.NARRATOR_GENDER);

    /**
     * The user template's values.
     *
     * @param facts the book's metadata
     * @param knowledge the verdict on what the model knows of the book
     * @param windows each window's verified answers, in passage order, null for an unreadable one
     * @param samples the windows read, in the same order as {@code windows}
     * @return the values; never null, always with {@code reports}
     */
    static Map<String, String> of(
            final BookFacts facts,
            final Knowledge knowledge,
            final List<@Nullable Map<BriefField, Answer>> windows,
            final List<BookSample> samples) {
        final Map<String, String> user = new HashMap<>();
        putIfPresent(user, "title", facts.title());
        putIfPresent(user, "author", facts.author());
        final Integer year = facts.year();
        putIfPresent(user, "year", year == null ? null : year.toString());
        putIfPresent(user, "publisher", facts.publisher());
        putIfPresent(user, "description", facts.description());
        final BookKnowledgeReply reply = knowledge.reply();
        if (reply != null) {
            user.put("knowledge", knowledgeOf(reply));
        }
        user.put("reports", reports(windows, samples));
        log.debug(
                "Synthesis input: {} reports, knowledge shown {}, facts {}",
                windows.size(),
                reply != null,
                user.keySet());
        return user;
    }

    private static void putIfPresent(final Map<String, String> user, final String slot, final @Nullable String value) {
        if (value != null && !value.isBlank()) {
            user.put(slot, value);
        }
    }

    private static String knowledgeOf(final BookKnowledgeReply reply) {
        final List<String> lines = new ArrayList<>();
        line(lines, "Genre", reply.genre());
        line(lines, "Audience", reply.audience());
        line(lines, "Era and voice", reply.era());
        line(lines, "Narration", reply.narration());
        line(lines, "Summary", reply.summary());
        return String.join("\n", lines);
    }

    private static void line(final List<String> lines, final String label, final @Nullable String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value);
        }
    }

    private static String reports(
            final List<@Nullable Map<BriefField, Answer>> windows, final List<BookSample> samples) {
        final List<String> reports = new ArrayList<>();
        for (int index = 0; index < windows.size(); index++) {
            final String opening = index < samples.size() ? opening(samples.get(index)) : "";
            reports.add("Passage " + (index + 1) + ": " + opening + report(windows.get(index)));
        }
        return String.join("\n", reports);
    }

    // The window's first words, cut with an ellipsis, which the quote check reads as a gap; none for an empty window.
    private static String opening(final BookSample sample) {
        final String[] words = SPACES.split(String.join(" ", sample.passages()).strip());
        if (words.length == 0 || words[0].isEmpty()) {
            return "";
        }
        final boolean cut = words.length > OPENING_WORDS;
        final String shown = String.join(" ", Arrays.copyOf(words, Math.min(words.length, OPENING_WORDS)));
        log.trace("Synthesis report opening words={} cut={} '{}'", Math.min(words.length, OPENING_WORDS), cut, shown);
        return "opening \"" + shown + (cut ? " …" : "") + "\"; ";
    }

    private static String report(final @Nullable Map<BriefField, Answer> window) {
        if (window == null) {
            return "no reading.";
        }
        final String readings = REPORTED.stream()
                .map(field -> reading(field, window.get(field)))
                .collect(Collectors.joining("; "));
        return readings + ".";
    }

    private static String reading(final BriefField field, final @Nullable Answer answer) {
        final String name = BriefReplies.wireName(field);
        if (answer == null || (answer.isNeutral() && answer.quote().isBlank())) {
            return name + " nothing shown";
        }
        if (answer.isNeutral()) {
            return name + " not proved (quote: \"" + answer.quote() + "\")";
        }
        return name + " \"" + answer.value() + "\" (quote: \"" + answer.quote() + "\")";
    }
}
