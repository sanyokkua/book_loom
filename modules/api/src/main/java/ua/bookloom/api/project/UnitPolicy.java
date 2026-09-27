package ua.bookloom.api.project;

/**
 * The Book Brief's chosen policy for units of measurement mentioned in the text
 * ({@code specs/book-brief/spec.md} "Capture the translation policies").
 */
public enum UnitPolicy {

    /** Keep the source text's units of measurement as written; the default. */
    KEEP,

    /** Convert units of measurement to metric. */
    METRIC
}
