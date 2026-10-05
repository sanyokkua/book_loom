package ua.bookloom.pipeline.reviewer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a reviewer edit says was wrong with the candidate. */
public enum ReviewCriterion {

    /** The candidate says something other than the source. */
    MEANING("meaning", false),

    /** Something of the source is missing from the candidate. */
    OMISSION("omission", false),

    /** Something the source does not say was added. */
    ADDITION("addition", false),

    /** A glossary or lexicon rendering is not used, or one name has two renderings. */
    TERMINOLOGY("terminology", false),

    /** A verb, adjective or pronoun has the wrong gender for its subject. */
    GENDER("gender", false),

    /** Number, case or person does not agree with the word it belongs to. */
    AGREEMENT("agreement", false),

    /** A word that is garbled, coined or not a real word of the target language. */
    INVENTED_WORD("invented-word", false),

    /** A quotation mark or bracket opens and never closes, or closes without opening. */
    QUOTES("quotes", false),

    /** Part of the candidate is in a language other than the target. */
    LANGUAGE("language", false),

    /** Wording that is stiff or unidiomatic but not wrong: a note, never applied and never a blocker. */
    FLUENCY("fluency", true),

    /** A matter of taste in register or word choice: a note, never applied and never a blocker. */
    STYLE("style", true);

    private final String wire;
    private final boolean note;

    ReviewCriterion(final String wire, final boolean note) {
        this.wire = wire;
        this.note = note;
    }

    /**
     * The word the reviewer writes for this criterion.
     *
     * @return a lower-case token, with a hyphen where the name has two words
     */
    public String wire() {
        return wire;
    }

    /**
     * Whether an edit with this criterion is only a note for the person.
     *
     * @return {@code true} for fluency and style, which are recorded but never applied and never block acceptance
     */
    public boolean isNote() {
        return note;
    }

    /**
     * Reads the word a reviewer wrote.
     *
     * @param text the criterion as written, in any letter case, with a hyphen, underscore or space between words
     * @return the criterion, or empty when the word names none
     */
    public static Optional<ReviewCriterion> of(final String text) {
        final String key =
                text.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return Arrays.stream(values())
                .filter(criterion -> criterion.wire.equals(key))
                .findFirst();
    }
}
