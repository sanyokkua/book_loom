package ua.bookloom.api.project;

/**
 * The Book Brief's chosen policy for a footnote's body
 * ({@code specs/book-brief/spec.md} "Capture the translation policies").
 */
public enum FootnotePolicy {

    /** Translate the footnote body; the default. */
    TRANSLATE,

    /** Leave the footnote body untranslated. */
    KEEP
}
