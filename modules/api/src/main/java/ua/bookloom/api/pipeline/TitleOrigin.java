package ua.bookloom.api.pipeline;

/** Where a book's original title was found, in the order the sources are tried. */
public enum TitleOrigin {
    /** The book's own details: its package or description metadata (an {@code aux:title} segment). */
    DETAILS,
    /** A Markdown book's front-matter {@code title}. */
    FRONT_MATTER,
    /** A Markdown book's first heading. */
    FIRST_HEADING,
    /** The source file's name without its format suffix, because the book names no title. */
    FILE_NAME
}
