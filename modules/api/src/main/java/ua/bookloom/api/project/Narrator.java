package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;

/**
 * Who narrates the book: the person and, for a first-person narrator, the gender the target language must agree with
 * in its past-tense verbs and adjectives ({@code specs/book-brief/spec.md} "Capture who narrates the book").
 *
 * @param person the narrator's grammatical person
 * @param gender the narrator's gender; {@link Gender#UNKNOWN} when not stated
 * @param genderOrigin who decided the gender, so a later step can tell the person's choice from the code's and
 *     re-check only the latter (15i.B4, 15i.T5); {@link GenderOrigin#NONE} exactly when the gender is unknown
 * @param evidence the quotes of the book the gender was verified from, kept so they can be checked again once the
 *     glossary changes; while the gender is unknown they are pending evidence, quotes that call the narrator by a name
 *     nobody knew yet (15i.T5). Never null, empty for a gender the person set
 */
public record Narrator(NarratorPerson person, Gender gender, GenderOrigin genderOrigin, List<String> evidence) {

    private static final Narrator UNSPECIFIED = new Narrator(NarratorPerson.UNSPECIFIED, Gender.UNKNOWN);

    /**
     * Who decided the narrator's gender. Apart from a glossary entry's {@link ua.bookloom.api.project.GenderOrigin}
     * because a narrator's gender has one more source, the run's own count of the narrator's words.
     */
    public enum GenderOrigin {
        /** The person chose it, or answered the Start question; nothing the code reads replaces it. */
        PERSON,
        /** Quotes of the book the code checked show it (a name the narrator is called by, a word of address). */
        VERIFIED,
        /** A guess nobody has confirmed, shown as "(suggested)". */
        SUGGESTED,
        /** The run counted the narrator's own past-tense forms in the decided translations (15i.T5). */
        INFERRED,
        /**
         * The gender is unknown, so nothing decided it and nothing is bound: a person who leaves it unstated has chosen
         * nothing a later step must respect, and a code step may fill it.
         */
        NONE
    }

    /**
     * Rejects a missing part and copies the quotes. An unknown gender has no origin whatever the caller passed, and a
     * known one needs an origin: a gender nobody decided cannot be known.
     */
    public Narrator {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(genderOrigin, "genderOrigin");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (gender == Gender.UNKNOWN) {
            genderOrigin = GenderOrigin.NONE;
        } else if (genderOrigin == GenderOrigin.NONE) {
            throw new IllegalArgumentException("a known narrator gender needs an origin");
        }
    }

    /**
     * A narrator the person set, with no quotes behind it; an unknown gender is left without an origin.
     *
     * @param person the narrator's grammatical person
     * @param gender the narrator's gender
     */
    public Narrator(final NarratorPerson person, final Gender gender) {
        this(person, gender, gender == Gender.UNKNOWN ? GenderOrigin.NONE : GenderOrigin.PERSON, List.of());
    }

    /**
     * The same narrator told in another person; the gender keeps its origin and quotes, because only the person
     * changed.
     *
     * @param other the new grammatical person
     * @return a narrator with {@code other} as its person
     */
    public Narrator withPerson(final NarratorPerson other) {
        return new Narrator(Objects.requireNonNull(other, "other"), gender, genderOrigin, evidence);
    }

    /**
     * The narrator a book starts with: nothing is known, so no narrator rule applies.
     *
     * @return the shared unspecified narrator
     */
    public static Narrator unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Whether the gender check has something to hold a text against.
     *
     * @return {@code true} for a first-person narrator whose gender is male or female and not merely suggested (set,
     *     verified or inferred), {@code false} otherwise
     */
    public boolean hasCheckableGender() {
        return person == NarratorPerson.FIRST
                && (gender == Gender.MALE || gender == Gender.FEMALE)
                && genderOrigin != GenderOrigin.SUGGESTED;
    }

    /**
     * Whether the gender is a guess nobody confirmed, which the checks and the style sheet treat as "likely".
     *
     * @return {@code true} if the gender is known and only suggested, {@code false} otherwise
     */
    public boolean hasSuggestedGender() {
        return genderOrigin == GenderOrigin.SUGGESTED;
    }

    /**
     * Whether the person has said anything about the narrator.
     *
     * @return {@code true} unless this is the unspecified narrator
     */
    public boolean isSpecified() {
        return person != NarratorPerson.UNSPECIFIED || gender != Gender.UNKNOWN;
    }
}
