package ua.bookloom.api.project;

/**
 * The Book Brief's chosen tone register for the translation
 * ({@code specs/book-brief/spec.md} "Capture the tone and style of the translation").
 */
public enum Register {

    /** Formal, literary prose. */
    FORMAL_LITERARY,

    /** Neither markedly formal nor casual; the default. */
    NEUTRAL,

    /** Casual, conversational prose. */
    CASUAL
}
