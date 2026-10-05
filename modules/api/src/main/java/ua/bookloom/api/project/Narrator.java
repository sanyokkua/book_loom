package ua.bookloom.api.project;

import java.util.Objects;

/**
 * Who narrates the book: the person and, for a first-person narrator, the gender the target language must agree with
 * in its past-tense verbs and adjectives ({@code specs/book-brief/spec.md} "Capture who narrates the book").
 *
 * @param person the narrator's grammatical person
 * @param gender the narrator's gender; {@link Gender#UNKNOWN} when not stated
 */
public record Narrator(NarratorPerson person, Gender gender) {

    private static final Narrator UNSPECIFIED = new Narrator(NarratorPerson.UNSPECIFIED, Gender.UNKNOWN);

    /** Rejects a missing part. */
    public Narrator {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(gender, "gender");
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
