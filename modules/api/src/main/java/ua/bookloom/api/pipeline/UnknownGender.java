package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Origin;

/**
 * A character in the glossary whose gender nobody has settled — none at all, or only a rule's or the model's likely
 * value — with how often the book names it, so Start can ask about the ones that matter, the least confident first and
 * each with its likely value already chosen.
 *
 * @param term the glossary term of the character
 * @param mentions how many times the book's body text names the character
 * @param suggested whether the glossary holds a gender nobody confirmed, as against no gender at all
 * @param entryId the glossary entry's id, which the answer is stored under; empty when not known
 * @param offered the gender the question starts with chosen: the held rule's or likely value, else
 *     {@link Gender#UNKNOWN}
 * @param origin where the offered gender came from, {@link Origin#RULE} or {@link Origin#LIKELY}; null when none is held
 * @param confidence how sure the model was of a likely gender, from 0 to 1; null when the model was not asked or the
 *     gender is not its answer
 * @param modelGender the model's gender when it disagrees with a rule's, so the row can read "rule: male · model:
 *     female"; null otherwise
 * @param target the character's rendering in the translation so far; null when it has none yet
 */
public record UnknownGender(
        String term,
        int mentions,
        boolean suggested,
        String entryId,
        Gender offered,
        @Nullable Origin origin,
        @Nullable Double confidence,
        @Nullable Gender modelGender,
        @Nullable String target) {

    /** Rejects a missing part or a negative count. */
    public UnknownGender {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(offered, "offered");
        if (mentions < 0) {
            throw new IllegalArgumentException("mentions must not be negative: " + mentions);
        }
    }

    /**
     * A character known by its term only, with no value to offer.
     *
     * @param term the glossary term of the character
     * @param mentions how many times the book's body text names the character
     * @param suggested whether the glossary holds a suggested gender nobody confirmed
     */
    public UnknownGender(final String term, final int mentions, final boolean suggested) {
        this(term, mentions, suggested, "", Gender.UNKNOWN, null, null, null, null);
    }

    /**
     * A character with no gender at all.
     *
     * @param term the glossary term of the character
     * @param mentions how many times the book's body text names the character
     */
    public UnknownGender(final String term, final int mentions) {
        this(term, mentions, false);
    }
}
