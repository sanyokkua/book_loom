package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;

/**
 * What the source text suggests about who narrates the book, found before a run and kept beside the brief without
 * touching it: the person decides, this only lets the application ask
 * ({@code specs/book-brief/spec.md} "Detect first-person narration").
 *
 * @param person how the book is narrated as a whole
 * @param firstPersonShare the share, in {@code [0,1]}, of the book's narration sentences (outside quotes and
 *     dialogue) that have the first-person pronoun as subject
 * @param chapters the 1-based positions of the chapters narrated in the first person, in reading order; empty for a
 *     third-person book
 */
public record NarratorHint(Person person, double firstPersonShare, List<Integer> chapters) {

    /** How a book, or a chapter, is narrated. */
    public enum Person {

        /** Every chapter that has narration is told as "I". */
        FIRST,

        /** No chapter is told as "I". */
        THIRD,

        /** Some chapters are told as "I" and some are not, as in a book that alternates narrators. */
        MIXED
    }

    /** Rejects a missing person and copies the chapters. */
    public NarratorHint {
        Objects.requireNonNull(person, "person");
        chapters = List.copyOf(Objects.requireNonNull(chapters, "chapters"));
    }

    /**
     * Whether the person should be asked who narrates.
     *
     * @return {@code true} when some narration is in the first person, {@code false} for a third-person book
     */
    public boolean suggestsFirstPerson() {
        return person != Person.THIRD;
    }
}
