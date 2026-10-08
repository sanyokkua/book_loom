package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * The character gender sheet of a segment: one {@code name — gender} line for each glossary character the segment
 * names whose gender is known, so a model never has to guess a pronoun or a verb ending from a name alone. A character
 * the segment does not name is never listed, so the sheet stays as short as the scene.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InjectedCharacters {

    /**
     * The sheet for some segments.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @param sourceLanguage the source language's tag, which picks the pronouns read as evidence; null reads English
     * @return one line per present character of known gender, in glossary order, with the pronouns the text shows
     *     after the name when it shows any ({@code Lyra — female (she ×2)}); never null, empty when none is present
     */
    public static List<String> select(
            final List<Segment> segments, final List<GlossaryEntry> glossary, @Nullable final String sourceLanguage) {
        final List<String> texts = Tokens.visibleTexts(segments);
        final List<String> lines = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER && entry.gender() != Gender.UNKNOWN)
                .filter(entry -> !entry.term().isBlank() && namedIn(entry.term(), texts))
                .map(entry -> lineOf(entry, texts, sourceLanguage))
                .toList();
        log.trace("Character sheet offered={} selected={}", glossary.size(), lines.size());
        return lines;
    }

    /**
     * The sheet for some segments, without pronoun evidence.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @return one {@code name — gender} line per present character of known gender; never null
     */
    public static List<String> select(final List<Segment> segments, final List<GlossaryEntry> glossary) {
        return select(segments, glossary, null);
    }

    private static String lineOf(
            final GlossaryEntry entry, final List<String> texts, @Nullable final String sourceLanguage) {
        final String line = entry.term() + " — " + entry.gender().name().toLowerCase(Locale.ROOT);
        final String evidence =
                PronounGender.of(entry.term(), texts, sourceLanguage).compact();
        return evidence.isEmpty() ? line : line + " (" + evidence + ")";
    }

    /**
     * The lines that fit a token allowance, from the first, so a long cast is cut at the end and never half-shown.
     *
     * @param lines the sheet's lines
     * @param allowance the tokens the sheet may take, from {@link ContextBudget#characterAllowance(int)}
     * @return the leading lines whose estimated size stays within {@code allowance}
     */
    public static List<String> within(final List<String> lines, final int allowance) {
        final List<String> kept = new ArrayList<>();
        int tokens = 0;
        for (final String line : lines) {
            tokens += TokenEstimator.estimate(line, null);
            if (tokens > allowance) {
                break;
            }
            kept.add(line);
        }
        return kept;
    }

    private static boolean namedIn(final String term, final List<String> texts) {
        return texts.stream().anyMatch(text -> TermMatch.isNamedIn(term, text));
    }
}
