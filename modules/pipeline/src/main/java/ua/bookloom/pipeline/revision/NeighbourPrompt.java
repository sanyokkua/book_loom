package ua.bookloom.pipeline.revision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.context.InjectedCharacters;
import ua.bookloom.pipeline.context.InjectedLexicon;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * The slots of one check against the neighbours: the paragraph before and after it as source and translation, the
 * glossary renderings of the names the three paragraphs hold, the established renderings of recurring terms, the
 * genders of the characters they name, the rolling summary, the source, its tokens and the text to check. The
 * narrator rides in the system message's style sheet, which every call carries. Each list is cut to a token allowance
 * from its first line, so a long cast or glossary never crowds out the paragraph itself.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NeighbourPrompt {

    static final int MAX_FACT_LINES = 40;
    static final int FACT_TOKENS = 600;
    static final int LEXICON_TOKENS = 400;
    static final int CHARACTER_TOKENS = 200;

    /**
     * The user slots of the check.
     *
     * @param inputs what the pass read as it started
     * @param source the paragraph's opened segment
     * @param masked the paragraph's stored target, masked
     * @param before the paragraph before it, or null at the start of the book
     * @param after the paragraph after it, or null at the end of the book
     * @return the slot values; an absent part is an empty value, which leaves its block out
     */
    static Map<String, String> slots(
            final PassInputs inputs,
            final Segment source,
            final String masked,
            @Nullable final Neighbour before,
            @Nullable final Neighbour after) {
        final List<Segment> present = Stream.of(before, after)
                .filter(Objects::nonNull)
                .map(Neighbour::source)
                .collect(Collectors.toCollection(() -> new ArrayList<>(List.of(source))));
        final Map<String, String> user = bookContext(inputs, present);
        user.put("source", source.masked());
        user.put("text", masked);
        user.put("previous", block(before));
        user.put("next", block(after));
        user.put("tokens", SelfHealCalls.immutableTokens(source.masked()));
        log.debug(
                "Neighbour prompt segmentId={} previous={} next={} filled={}",
                source.id(),
                before != null,
                after != null,
                user.entrySet().stream()
                        .filter(slot -> !slot.getValue().isEmpty())
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList());
        return user;
    }

    // What the run learned of the book that bears on these paragraphs.
    private static Map<String, String> bookContext(final PassInputs inputs, final List<Segment> present) {
        final Map<String, String> user = new HashMap<>();
        user.put("resolvedFacts", facts(inputs, present));
        user.put("lexiconTerms", lexicon(inputs, present));
        user.put("characters", lines(InjectedCharacters.select(present, inputs.glossary()), CHARACTER_TOKENS));
        user.put("summary", Objects.requireNonNullElse(inputs.context().summary(), ""));
        return user;
    }

    private static String block(@Nullable final Neighbour neighbour) {
        if (neighbour == null) {
            return "";
        }
        return "Source: " + DisplayText.of(neighbour.source().masked()) + "\nTranslation: " + neighbour.target();
    }

    // The names and terms the paragraph and its neighbours hold, with the rendering the glossary gives each.
    private static String facts(final PassInputs inputs, final List<Segment> present) {
        final String text = present.stream()
                .map(segment -> Tokens.replace(segment.masked(), " "))
                .reduce("", (joined, next) -> joined + " " + next);
        final List<String> found = inputs.glossary().stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .filter(entry -> TermMatch.occursIn(entry.term(), text))
                .limit(MAX_FACT_LINES)
                .map(NeighbourPrompt::fact)
                .toList();
        return lines(found, FACT_TOKENS);
    }

    private static String lexicon(final PassInputs inputs, final List<Segment> present) {
        final List<String> found =
                InjectedLexicon.select(present, inputs.context().lexicon(), inputs.glossary()).stream()
                        .map(InjectedLexicon::line)
                        .toList();
        return lines(found, LEXICON_TOKENS);
    }

    private static String lines(final List<String> lines, final int allowance) {
        return String.join("\n", InjectedCharacters.within(lines, allowance));
    }

    private static String fact(final GlossaryEntry entry) {
        final String gender = entry.gender() == Gender.UNKNOWN
                ? ""
                : ", " + entry.gender().name().toLowerCase(Locale.ROOT);
        return "- " + entry.term() + " → " + entry.target() + gender;
    }

    /**
     * A paragraph next to the checked one.
     *
     * @param source its opened segment
     * @param target its translation as the person reads it
     */
    record Neighbour(Segment source, String target) {

        /** Rejects a missing part. */
        Neighbour {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }

        /**
         * The neighbour a stored record gives.
         *
         * @param source its opened segment, or null when the book has none there
         * @param record its stored record, or null when it has none
         * @return the neighbour, or null when it has no source or no translation yet
         */
        static @Nullable Neighbour of(@Nullable final Segment source, @Nullable final SegmentRecord record) {
            if (source == null || record == null) {
                return null;
            }
            final String masked =
                    record.userTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
            final String target = masked == null ? "" : DisplayText.of(masked).strip();
            return target.isEmpty() ? null : new Neighbour(source, target);
        }
    }
}
