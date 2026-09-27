package ua.bookloom.api.project;

/**
 * The Book Brief's chosen policy for a passage already written in a language other than the book's source
 * ({@code specs/book-brief/spec.md} "Capture the translation policies").
 */
public enum ForeignPassagePolicy {

    /** Leave the passage untranslated in its original language; the default. */
    KEEP,

    /** Translate the passage like any other text. */
    TRANSLATE,

    /** Translate the passage and add a note marking it as originally foreign. */
    TRANSLATE_WITH_NOTE
}
