package ua.bookloom.api.project;

/**
 * Who wrote a lexicon entry's {@code chosen} rendering (15i.N7): a person's rendering is never replaced by a model's
 * or by a file, while a model's is a hint a person or a file may replace.
 */
public enum RenderingOrigin {

    /** The person typed it or imported it from a file; an entry written before origins existed counts as this. */
    PERSON,

    /** A reading of the term in its windows wrote it and nobody has confirmed it yet. */
    MODEL
}
