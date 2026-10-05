package ua.bookloom.pipeline.context;

/**
 * The dynamic parts of a draft's context in the order they earn room: when the window is short the lowest-priority
 * section is cut first, and a section whose first item does not fit is left out whole rather than half-shown. The
 * chunk's own text, the static prefix and the examples never compete here; they are reserved before this runs.
 */
public enum ContextSection {

    /** Glossary and locked-name lines — a wrong rendering is the most visible error, so these are cut last. */
    GLOSSARY(1, Relevance.TERM_PRESENT),

    /**
     * Who is who among the characters the segment names, with the gender the glossary knows — a short sheet with a
     * share of its own, so a wrong pronoun or verb ending is prevented without taking the glossary's room.
     */
    CHARACTERS(2, Relevance.TERM_PRESENT),

    /**
     * The established renderings of recurring terms the chunk names — a soft "keep consistent" hint, so it has a share
     * of its own and never takes the glossary's room.
     */
    LEXICON(3, Relevance.TERM_PRESENT),

    /** Earlier translation-memory decisions the segment should stay consistent with. */
    MEMORY(4, Relevance.WHEN_EXISTS),

    /** The unit's last few translated segments, newest kept first. */
    PRECEDING(5, Relevance.SAME_UNIT),

    /** The rolling "book so far" summary — useful, but the cheapest thing to lose. */
    SUMMARY(6, Relevance.WHEN_EXISTS);

    /** The rule that decides whether a section has anything to show for this chunk. */
    public enum Relevance {
        /** Only entries whose term occurs in the chunk. */
        TERM_PRESENT,
        /** Only text from the chunk's own chapter or unit, never an earlier one. */
        SAME_UNIT,
        /** Only when the thing exists; otherwise the section is dropped, never filled with a placeholder. */
        WHEN_EXISTS
    }

    private final int priority;
    private final Relevance relevance;

    ContextSection(final int priority, final Relevance relevance) {
        this.priority = priority;
        this.relevance = relevance;
    }

    /** Lower numbers keep their room first. */
    public int priority() {
        return priority;
    }

    /** The relevance rule this section is filtered by. */
    public Relevance relevance() {
        return relevance;
    }
}
