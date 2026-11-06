package ua.bookloom.api.pipeline;

/**
 * Which kind of translation memory a {@link MemoryUpdated} event reports a change to.
 */
public enum MemoryKind {

    /** The project's glossary. */
    GLOSSARY,

    /** The project's translation-memory store. */
    TM
}
