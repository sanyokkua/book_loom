package ua.bookloom.api.project;

/**
 * A glossary entry's grammatical/semantic category
 * ({@code specs/glossary/spec.md} "Show the glossary as an editable table on Names & style").
 */
public enum TermType {

    /** A character's name. */
    CHARACTER,

    /** A place name. */
    PLACE,

    /** A recurring in-world term (an object, concept or organization). */
    TERM,

    /** A title or honorific. */
    TITLE,

    /** A term that fits none of the other categories. */
    OTHER
}
