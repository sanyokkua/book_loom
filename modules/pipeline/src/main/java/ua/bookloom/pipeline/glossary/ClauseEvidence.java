package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.prompt.AddressTerms;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Decides which name one mention of a form that fits two names belongs to, by the clause around it: a past-tense verb
 * right after it ({@code pastFemale}, {@code pastMale}) makes it that gender's nominative when it can be one, and
 * otherwise rules out the other gender's nominative; a word before it that asks for another case
 * ({@code addressPositions}) rules out every nominative; a gendered form of address before it ({@code addressTerms})
 * keeps that gender. Only a word joined to the mention by spaces counts: a comma or a dash ends the clause. A mention
 * the clause leaves with more than one reading is unsure, and nothing is guessed. The words are the source language
 * file's; a language without them decides nothing.
 *
 * <p>One instance serves one pass and counts what it decided, for the pass's log line ({@link #logSummary}).
 */
@Slf4j
final class ClauseEvidence {

    /** The fewest letters a verb keeps before its past ending. */
    static final int MIN_VERB_STEM = 2;

    private final @Nullable String language;
    private final List<String> pastFemale;
    private final List<String> pastMale;
    private final Set<String> asksOblique;
    private final Map<String, Gender> addressTerms;
    private int decided;
    private int unsure;

    private ClauseEvidence(
            @Nullable final String language,
            final List<String> pastFemale,
            final List<String> pastMale,
            final Set<String> asksOblique,
            final Map<String, Gender> addressTerms) {
        this.language = language;
        this.pastFemale = pastFemale;
        this.pastMale = pastMale;
        this.asksOblique = asksOblique;
        this.addressTerms = addressTerms;
    }

    /**
     * The clause reader of a language.
     *
     * @param language the source language tag, or null when it is not known (nothing is decided)
     * @return a new reader with its counts at zero; never null
     */
    static ClauseEvidence of(@Nullable final String language) {
        if (language == null) {
            return new ClauseEvidence(null, List.of(), List.of(), Set.of(), Map.of());
        }
        final LanguageRules rules = LanguageRules.bundled();
        return new ClauseEvidence(
                language,
                rules.wordsOf(language, "pastFemale"),
                rules.wordsOf(language, "pastMale"),
                rules.wordsOf(language, "addressPositions").stream()
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet()),
                AddressTerms.of(language));
    }

    /**
     * The reading the clause around one mention leaves.
     *
     * @param text the non-null text that holds the mention
     * @param start where the mention starts in {@code text}
     * @param end where it ends
     * @param readings the non-null readings of the written form, from {@link NameForms#candidates}
     * @return the one reading left, or empty when the mention is unsure; a form with one reading is that reading
     */
    Optional<NameForms.Reading> decide(
            final String text, final int start, final int end, final Set<NameForms.Reading> readings) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(readings, "readings");
        if (readings.size() <= 1) {
            return readings.stream().findFirst();
        }
        final String before = wordBefore(text, start);
        Set<NameForms.Reading> left = byVerb(readings, pastGender(wordAfter(text, end)));
        if (before != null && asksOblique.contains(before.toLowerCase(Locale.ROOT))) {
            left = narrowed(left, reading -> !reading.nominative());
        }
        final Gender addressed = before == null ? null : addressTerms.get(before.toLowerCase(Locale.ROOT));
        if (addressed != null) {
            left = narrowed(left, reading -> reading.gender() == addressed);
        }
        final boolean isDecided = left.size() == 1;
        decided += isDecided ? 1 : 0;
        unsure += isDecided ? 0 : 1;
        return isDecided ? left.stream().findFirst() : Optional.empty();
    }

    /** Writes the counts of this pass in one DEBUG line. */
    void logSummary() {
        log.debug("Clause evidence language={} decided={} unsure={}", language, decided, unsure);
    }

    private static Set<NameForms.Reading> byVerb(final Set<NameForms.Reading> readings, @Nullable final Gender verb) {
        if (verb == null) {
            return readings;
        }
        final Set<NameForms.Reading> subject = readings.stream()
                .filter(reading -> reading.nominative() && reading.gender() == verb)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!subject.isEmpty()) {
            return subject;
        }
        return narrowed(
                readings,
                reading -> !reading.nominative() || reading.gender() == verb || reading.gender() == Gender.UNKNOWN);
    }

    // A rule that would leave nothing is not applied: the clause contradicts the table, and says nothing then.
    private static Set<NameForms.Reading> narrowed(
            final Set<NameForms.Reading> readings, final Predicate<NameForms.Reading> keep) {
        final Set<NameForms.Reading> kept =
                readings.stream().filter(keep).collect(Collectors.toCollection(LinkedHashSet::new));
        return kept.isEmpty() ? readings : kept;
    }

    private @Nullable Gender pastGender(@Nullable final String word) {
        if (word == null) {
            return null;
        }
        final String lower = word.toLowerCase(Locale.ROOT);
        final int female = longestEnding(lower, pastFemale);
        final int male = longestEnding(lower, pastMale);
        if (female == 0 && male == 0) {
            return null;
        }
        return female > male ? Gender.FEMALE : Gender.MALE;
    }

    private static int longestEnding(final String lower, final List<String> endings) {
        return endings.stream()
                .filter(ending -> lower.length() - ending.length() >= MIN_VERB_STEM && lower.endsWith(ending))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private static @Nullable String wordAfter(final String text, final int end) {
        int from = end;
        while (from < text.length() && isSpace(text.charAt(from))) {
            from++;
        }
        int to = from;
        while (to < text.length() && isLetter(text.charAt(to))) {
            to++;
        }
        return from == end || to == from ? null : text.substring(from, to);
    }

    private static @Nullable String wordBefore(final String text, final int start) {
        int to = start;
        while (to > 0 && isSpace(text.charAt(to - 1))) {
            to--;
        }
        int from = to;
        while (from > 0 && isLetter(text.charAt(from - 1))) {
            from--;
        }
        return to == start || from == to ? null : text.substring(from, to);
    }

    private static boolean isSpace(final char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    private static boolean isLetter(final char character) {
        return Character.isLetter(character) || Character.getType(character) == Character.NON_SPACING_MARK;
    }
}
