package ua.bookloom.pipeline.reviewer;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether an edit does what its criterion says, for the criteria whose meaning code can read off the change: an
 * omission fix adds words, an addition fix removes them, a quotes fix touches a quotation mark or bracket, a language fix
 * touches a letter of another script than the candidate's own, a terminology fix renders a name the way the candidate
 * or the glossary already does, and a meaning, terminology, gender or agreement fix does not delete more than half of its
 * quote. A small model that cannot find a defect still fills the
 * slot with a paraphrase under one of these names; such an edit is a false alarm, not evidence, and is ignored.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CriterionFit {

    private static final String PAIR_MARKS = "«»„“”\"()[]{}‹›‘’'";

    /**
     * Checks one edit against its criterion.
     *
     * @param edit the edit as written; never null
     * @param candidate the whole candidate the quote was found in; never null
     * @param renderings the glossary renderings known for the chunk; never null
     * @return {@code true} when the change fits the criterion or the criterion is one code cannot read off the change
     */
    static boolean fits(final ReviewEdit edit, final String candidate, final Collection<String> renderings) {
        final String quote = edit.quote().strip();
        final String replacement = edit.replacement().strip();
        return switch (edit.criterion()) {
            case OMISSION -> replacement.length() > quote.length();
            case ADDITION -> replacement.length() < quote.length();
            case QUOTES -> quote.chars().anyMatch(mark -> PAIR_MARKS.indexOf(mark) >= 0);
            case LANGUAGE -> hasForeignScriptLetter(quote, candidate);
            case TERMINOLOGY ->
                keepsMostOfQuote(quote, replacement) && known(quote, replacement, candidate, renderings);
            case MEANING, GENDER, AGREEMENT -> keepsMostOfQuote(quote, replacement);
            default -> true;
        };
    }

    private static boolean keepsMostOfQuote(final String quote, final String replacement) {
        return replacement.length() * 2 >= quote.length();
    }

    // The words the edit brings in: at least one must already stand in the candidate or be a glossary rendering.
    private static boolean known(
            final String quote, final String replacement, final String candidate, final Collection<String> renderings) {
        final String quoted = quote.toLowerCase(Locale.ROOT);
        final String known = candidate.toLowerCase(Locale.ROOT)
                + " "
                + String.join(" ", renderings).toLowerCase(Locale.ROOT);
        return Arrays.stream(replacement.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                .filter(word -> !word.isEmpty() && !quoted.contains(word))
                .anyMatch(known::contains);
    }

    private static boolean hasForeignScriptLetter(final String quote, final String candidate) {
        final Character.UnicodeScript own = majorityScript(candidate);
        return quote.codePoints()
                .filter(Character::isLetter)
                .anyMatch(letter -> Character.UnicodeScript.of(letter) != own);
    }

    private static Character.UnicodeScript majorityScript(final String text) {
        final Map<Character.UnicodeScript, Integer> counts = new EnumMap<>(Character.UnicodeScript.class);
        text.codePoints()
                .filter(Character::isLetter)
                .forEach(letter -> counts.merge(Character.UnicodeScript.of(letter), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Character.UnicodeScript.COMMON);
    }
}
