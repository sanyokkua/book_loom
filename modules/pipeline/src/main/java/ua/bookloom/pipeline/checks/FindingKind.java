package ua.bookloom.pipeline.checks;

/** What a deterministic text check found. */
public enum FindingKind {

    /** A word that holds letters of the target script beside a letter of another script or a digit. */
    MIXED_SCRIPT,

    /** A quote pair opened and never closed, closed without opening, or closed by the wrong mark. */
    UNBALANCED_QUOTES,

    /** A whole paragraph that is still in the source language. */
    LEFTOVER_LANGUAGE,

    /** The same word written twice in a row. */
    DUPLICATE_WORD,

    /** A space where none belongs: doubled, before a full stop or comma, inside a bracket. */
    SPACING,

    /** A past-tense word of the narrator's whose gender is not the narrator's. */
    GENDER,

    /** A word that is not a real word of the target language: garbled or coined, written in the right script. */
    UNKNOWN_WORD,

    /** A word with a letter the target language's alphabet does not have. */
    ALPHABET
}
