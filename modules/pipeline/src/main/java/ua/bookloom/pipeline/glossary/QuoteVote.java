package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.index.Speech;
import ua.bookloom.pipeline.prompt.Pronouns;

/**
 * Checks the quote a term reading gives for a gender (15i.N4): the vote stands only when the quote is in the window it
 * answers, word for word (blanks and apostrophes aside), and in that window's narration — outside quotation marks and
 * outside a speaker's dash-led line — holds a pronoun of the claimed gender. With no pronoun table for the book's
 * language (Auto, or a language whose file lists none) the code cannot read the pronoun, so a quote that stands
 * wholly in the narration is all it checks.
 */
@Slf4j
final class QuoteVote {

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ]*");
    private static final Pattern BLANKS = Pattern.compile("\\s+");
    private static final Pattern EDGES = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final String APOSTROPHES = "'’ʼ";
    private static final String NOT_AFTER_LETTER = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String NOT_BEFORE_LETTER = "(?![\\p{L}\\p{M}\\p{N}])";

    private final @Nullable Set<String> female;
    private final @Nullable Set<String> male;

    private QuoteVote(final @Nullable Set<String> female, final @Nullable Set<String> male) {
        this.female = female;
        this.male = male;
    }

    /**
     * The check for a book's language.
     *
     * @param analysisLanguage the language the code reads the book in; null under Auto
     * @return the check; it reads pronouns only when the language lists both genders' pronouns
     */
    static QuoteVote of(@Nullable final String analysisLanguage) {
        if (analysisLanguage == null) {
            log.debug("Quote vote under Auto: the quote's place in the narration is all the code checks");
            return new QuoteVote(null, null);
        }
        final Set<String> feminine = new HashSet<>(Pronouns.female(analysisLanguage));
        feminine.addAll(Pronouns.femaleObject(analysisLanguage));
        final Set<String> masculine = new HashSet<>(Pronouns.male(analysisLanguage));
        masculine.addAll(Pronouns.maleObject(analysisLanguage));
        if (feminine.isEmpty() || masculine.isEmpty()) {
            log.debug("Quote vote in {}: no pronoun table, the quote's place is all the code checks", analysisLanguage);
            return new QuoteVote(null, null);
        }
        return new QuoteVote(Set.copyOf(feminine), Set.copyOf(masculine));
    }

    /**
     * The vote a quote stands for.
     *
     * @param quote the model's quote, marks removed; empty when it quoted none
     * @param claimed the gender the model claimed
     * @param windowText the text of the window the answer is about
     * @return {@code claimed} when the window bears the quote out, else {@link Gender#UNKNOWN}
     */
    Gender vote(final String quote, final Gender claimed, final String windowText) {
        final String core = EDGES.matcher(quote).replaceAll("");
        if (claimed == Gender.UNKNOWN || core.isEmpty()) {
            return Gender.UNKNOWN;
        }
        final Matcher found = patternOf(core).matcher(windowText);
        while (found.find()) {
            if (bearsOut(windowText, found.start(), found.end(), claimed)) {
                return claimed;
            }
        }
        log.debug("Quote vote {} dropped: no narration pronoun of that gender where the quote stands", claimed);
        return Gender.UNKNOWN;
    }

    /**
     * Whether a quote stands in a window: its words in order, as whole words, blanks and apostrophes aside.
     *
     * @param quote the model's quote, marks removed; empty when it quoted none
     * @param windowText the text of the window the quote is said to come from
     * @return {@code true} if the window holds the quote, {@code false} otherwise or when the quote has no letter
     */
    static boolean stands(final String quote, final String windowText) {
        final String core = EDGES.matcher(quote).replaceAll("");
        final boolean found =
                !core.isEmpty() && patternOf(core).matcher(windowText).find();
        log.debug("Quote of {} characters stands in its window: {}", quote.length(), found);
        return found;
    }

    private boolean bearsOut(final String text, final int start, final int end, final Gender claimed) {
        final Matcher word = WORD.matcher(text).region(start, end);
        boolean anyWord = false;
        while (word.find()) {
            anyWord = true;
            final boolean narration = Speech.isNarration(text, word.start());
            if (female == null || male == null) {
                if (!narration) {
                    return false;
                }
            } else if (narration && pronounsOf(claimed).contains(word.group().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return anyWord && female == null;
    }

    private Set<String> pronounsOf(final Gender gender) {
        return Objects.requireNonNull(gender == Gender.FEMALE ? female : male, "pronouns");
    }

    // The quote's words in order, any blanks between them, any apostrophe for an apostrophe, and whole words only, so
    // a quoted "he" is never the "he" inside "she" or "the".
    private static Pattern patternOf(final String quote) {
        final StringBuilder regex = new StringBuilder(NOT_AFTER_LETTER);
        final List<String> parts = BLANKS.splitAsStream(quote).toList();
        for (int index = 0; index < parts.size(); index++) {
            regex.append(index > 0 ? "\\s+" : "");
            parts.get(index)
                    .codePoints()
                    .forEach(letter -> regex.append(
                            APOSTROPHES.indexOf(letter) >= 0 ? "['’ʼ]" : Pattern.quote(Character.toString(letter))));
        }
        return Pattern.compile(regex.append(NOT_BEFORE_LETTER).toString());
    }
}
