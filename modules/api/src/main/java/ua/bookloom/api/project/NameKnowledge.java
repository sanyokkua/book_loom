package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the model answered for a bare name before reading any page of the book (design D5): its most probable gender
 * and kind, with how sure it was. It is a hint, never a fact: a glossary entry keeps it beside its own values so the
 * screen can show "likely, low confidence" and a rule that disagrees ("rule: male · model: female"), and nothing that
 * checks a translation reads it.
 *
 * @param gender the gender the model gave; {@link Gender#UNKNOWN} only for a kind that has none
 * @param type the kind the model gave
 * @param placeKind the kind of place, only for {@link TermType#PLACE}, else null
 * @param confidence how sure the model was, from 0 to 1
 */
public record NameKnowledge(
        Gender gender, TermType type, @Nullable PlaceKind placeKind, double confidence) {

    /** Below this confidence a likely gender is shown as "likely, low confidence". */
    public static final double LOW_CONFIDENCE = 0.7;

    /** Rejects a missing part or a confidence outside 0 to 1; a place kind is kept only on a place. */
    public NameKnowledge {
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(type, "type");
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("confidence must be within [0,1], but was " + confidence);
        }
        placeKind = type == TermType.PLACE ? placeKind : null;
    }

    /**
     * Whether the model was less sure than {@link #LOW_CONFIDENCE}.
     *
     * @return {@code true} if the confidence is below the bar, {@code false} otherwise
     */
    public boolean isLowConfidence() {
        return confidence < LOW_CONFIDENCE;
    }

    /**
     * Whether an entry holding this answer shows its gender as "likely, low confidence".
     *
     * @param held the entry's gender
     * @param origin where the entry's gender came from; null when it is unknown
     * @return {@code true} when the held gender is likely, is this answer's and was given below
     *     {@link #LOW_CONFIDENCE}; {@code false} otherwise
     */
    public boolean isLowConfidenceFor(final Gender held, @Nullable final Origin origin) {
        return origin == Origin.LIKELY && held == gender && isLowConfidence();
    }

    /**
     * Whether an entry holding this answer shows both values ("rule: male · model: female"), the rule's staying chosen.
     *
     * @param held the entry's gender
     * @param origin where the entry's gender came from; null when it is unknown
     * @return {@code true} when a rule gave the held gender and this answer names another, {@code false} otherwise
     */
    public boolean disagreesWithRule(final Gender held, @Nullable final Origin origin) {
        return origin == Origin.RULE && gender != Gender.UNKNOWN && gender != held;
    }
}
