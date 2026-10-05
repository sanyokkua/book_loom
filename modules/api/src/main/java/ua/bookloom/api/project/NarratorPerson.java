package ua.bookloom.api.project;

/**
 * The grammatical person a book is narrated in, as the Book Brief records it
 * ({@code specs/book-brief/spec.md} "Capture who narrates the book").
 */
public enum NarratorPerson {

    /** The person has not said; no narrator rule is applied. */
    UNSPECIFIED,

    /** The narrator says "I". */
    FIRST,

    /** The narrator stays outside the story. */
    THIRD
}
