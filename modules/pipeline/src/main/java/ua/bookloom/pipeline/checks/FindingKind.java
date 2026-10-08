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
    ALPHABET,

    /** A sentence of the source with no counterpart in the target. */
    SENTENCE_MISSING,

    /** A glossary name the source calls out that the target no longer has. */
    VOCATIVE_MISSING,

    /** Reply structure (braces, brackets, a wrapper label) the model wrote into the text. */
    PROTOCOL_LEAK,

    /** A number the source writes in digits that the target changed or lost. */
    NUMBER_CHANGED,

    /** A glossary name of the source replaced by another glossary name in the target. */
    NAME_SWAP,

    /** A woman's name written with a man's verb ending or declension. */
    NAME_GENDER
}
