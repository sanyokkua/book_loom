package ua.bookloom.api.project;

/**
 * How a name whose meaning the book plays on is rendered (design D5): the person is asked once in Prepare, never during
 * a run, and until then the name is transcribed with its meaning kept as a note.
 */
public enum MeaningChoice {

    /** The names pass found a window that explains or puns on the name; the person has not answered yet. */
    PENDING,

    /** Sound the name out and keep its meaning as a note; also what an unanswered question leaves. */
    TRANSCRIBE,

    /** Translate the name by its meaning: the next suggestion call asks for it under the Translate policy. */
    TRANSLATE,

    /** Sound the name out and gloss its meaning at its first use in the translation. */
    TRANSCRIBE_GLOSS
}
