package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
 * the segment does not name is never listed, so the sheet stays as short as the scene. A short name that is part of a
 * character's full name of the same gender ("Bobby" of "Bobby Quine") shares that character's line, and the pronouns
 * the scene shows are listed only when they agree with the set gender (15h.A2).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InjectedCharacters {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * The sheet for some segments.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @param sourceLanguage the source language's tag, which picks the pronouns read as evidence; null reads English
     * @return one line per present character of known gender, in glossary order, with the pronouns the text shows
     *     after the name when they agree with its gender ({@code Lyra — female (she ×2)}), a short name folded into its
     *     full name's line ({@code Bobby Quine (also Bobby) — male}); never null, empty when none is present
     */
    public static List<String> select(
            final List<Segment> segments, final List<GlossaryEntry> glossary, @Nullable final String sourceLanguage) {
        final List<String> texts = Tokens.visibleTexts(segments);
        final List<GlossaryEntry> known = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER && entry.gender() != Gender.UNKNOWN)
                .filter(entry -> !entry.term().isBlank())
                .toList();
        final Map<GlossaryEntry, List<GlossaryEntry>> lines = new LinkedHashMap<>();
        for (final GlossaryEntry entry : known) {
            if (namedIn(entry.term(), texts)) {
                final GlossaryEntry full = fullNameOf(entry, known);
                lines.computeIfAbsent(full, _ -> new ArrayList<>()).add(entry);
            }
        }
        final List<String> sheet = known.stream()
                .filter(lines::containsKey)
                .map(entry -> lineOf(entry, Objects.requireNonNull(lines.get(entry), "named"), texts, sourceLanguage))
                .toList();
        log.trace("Character sheet offered={} selected={}", glossary.size(), sheet.size());
        return sheet;
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

    // The first longer character name of the same gender whose words hold this one's as a whole run, else the entry.
    private static GlossaryEntry fullNameOf(final GlossaryEntry entry, final List<GlossaryEntry> known) {
        final List<String> words = wordsOf(entry.term());
        return known.stream()
                .filter(other -> other.gender() == entry.gender())
                .filter(other -> wordsOf(other.term()).size() > words.size())
                .filter(other -> Collections.indexOfSubList(wordsOf(other.term()), words) >= 0)
                .findFirst()
                .orElse(entry);
    }

    private static List<String> wordsOf(final String term) {
        return List.of(SPACES.split(term.strip()));
    }

    private static String lineOf(
            final GlossaryEntry full,
            final List<GlossaryEntry> named,
            final List<String> texts,
            @Nullable final String sourceLanguage) {
        final List<String> shortNames = named.stream()
                .filter(entry -> !entry.equals(full))
                .map(GlossaryEntry::term)
                .toList();
        final String name =
                shortNames.isEmpty() ? full.term() : full.term() + " (also " + String.join(", ", shortNames) + ")";
        final String line = name + " — " + full.gender().name().toLowerCase(Locale.ROOT);
        PronounGender.Evidence evidence = PronounGender.of(full.term(), texts, sourceLanguage);
        for (final String shortName : shortNames) {
            evidence = evidence.and(PronounGender.of(shortName, texts, sourceLanguage));
        }
        final String agreeing = evidence.compactAgreeing(full.gender());
        return agreeing.isEmpty() ? line : line + " (" + agreeing + ")";
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
