package ua.bookloom.pipeline.narrator;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * How much of a text the characters say: the letters inside quoted speech or a dash dialogue against all its letters,
 * as {@link NarratorDetector#narration} blanks them. The brief suggestion reads it per window, and the run's book rules
 * read it over the whole story to decide whether the dialogue rules are worth sending.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DialogueShare {

    private static final int PERCENT = 100;

    /**
     * The spoken and total letters of some paragraphs.
     *
     * @param spoken the letters inside speech; zero or more, never above {@code letters}
     * @param letters every letter; zero or more
     */
    public record Tally(long spoken, long letters) {

        /**
         * The share of spoken letters.
         *
         * @return from 0 to 1; 0 for a text with no letter
         */
        public double fraction() {
            return letters == 0 ? 0 : (double) spoken / letters;
        }

        /**
         * The share of spoken letters, rounded to a whole percent.
         *
         * @return from 0 to 100; 0 for a text with no letter
         */
        public int percent() {
            return letters == 0 ? 0 : (int) Math.round((double) spoken * PERCENT / letters);
        }
    }

    /**
     * Counts the spoken letters of paragraphs, each read on its own so an unclosed quote never runs into the next one.
     *
     * @param paragraphs the non-null paragraphs, masked or display text
     * @param languageTag the language whose quote marks apply, or null for the general table
     * @return the tally; never null
     */
    public static Tally of(final List<String> paragraphs, @Nullable final String languageTag) {
        Objects.requireNonNull(paragraphs, "paragraphs");
        long letters = 0;
        long spoken = 0;
        for (final String paragraph : paragraphs) {
            final String narration = NarratorDetector.narration(paragraph, languageTag);
            letters += paragraph.chars().filter(Character::isLetter).count();
            spoken += narration.length() == paragraph.length() ? spokenLetters(paragraph, narration) : 0;
        }
        return new Tally(spoken, letters);
    }

    // The letters the narration blanked: what the characters say.
    private static long spokenLetters(final String paragraph, final String narration) {
        long count = 0;
        for (int index = 0; index < paragraph.length(); index++) {
            if (Character.isLetter(paragraph.charAt(index)) && narration.charAt(index) == ' ') {
                count++;
            }
        }
        return count;
    }
}
