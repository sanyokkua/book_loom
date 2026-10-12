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
 *     re-check only the latter (15i.B4, 15i.T5)
 * @param evidence the quotes of the book the gender was verified from, kept so they can be checked again once the
 *     glossary changes; never null, empty for a gender the person set or one nobody knows
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
        INFERRED
    }

    /** Rejects a missing part and copies the quotes. */
    public Narrator {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(genderOrigin, "genderOrigin");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    /**
     * A narrator the person set, with no quotes behind it.
     *
     * @param person the narrator's grammatical person
     * @param gender the narrator's gender
     */
    public Narrator(final NarratorPerson person, final Gender gender) {
        this(person, gender, GenderOrigin.PERSON, List.of());
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
     * @return {@code true} for a first-person narrator whose gender is male or female, {@code false} otherwise
     */
    public boolean hasCheckableGender() {
        return person == NarratorPerson.FIRST && (gender == Gender.MALE || gender == Gender.FEMALE);
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
