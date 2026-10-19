package ua.bookloom.api.project;

/**
 * The Book Brief's chosen policy for rendering character/place names
 * ({@code specs/book-brief/spec.md} "Capture the translation policies").
 */
public enum NamePolicy {

    /** Translate a name that has a natural target-language equivalent. */
    TRANSLATE,

    /** Transliterate a name into the target script/spelling; the default. */
    TRANSLITERATE,

    /** Keep a name in its original source-language spelling. */
    KEEP_ORIGINAL
}
