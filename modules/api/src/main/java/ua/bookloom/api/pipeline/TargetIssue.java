package ua.bookloom.api.pipeline;

/**
 * Why a glossary target is refused or flagged against the target language's rules (design D4). The window words each
 * one in the display language, so the code carries no English reason text.
 */
public enum TargetIssue {

    /** A letter the target language's file forbids. */
    FORBIDDEN_LETTER,

    /** A Latin letter in a target of another script, the term no identifier and not kept as written. */
    LATIN,

    /** A stress mark the language asks to remove, or a target that was nothing but stress marks. */
    STRESS_MARK,

    /** No capital at all for a capitalised name. */
    CAPITALISATION,

    /** A name's words, counted by their hyphenated parts, differ in number from the term's. */
    WORD_COUNT,

    /** The other alternative of a convention the Book Brief stores (the hard g spelled {@code ґ} where it uses {@code г}). */
    CONVENTION,

    /** Not one of the forms the accepted target's name table generates. */
    RESPELLING,

    /** A review flag: the target ends like an oblique or a vocative form, not the dictionary form. */
    OBLIQUE_ENDING,

    /** A review flag: the target counts another number of words than its term. */
    WORD_COUNT_DIFFERS;

    /**
     * Whether this issue is a review flag, computed when a row is shown and never stored, rather than a refusal.
     *
     * @return {@code true} for {@link #OBLIQUE_ENDING} and {@link #WORD_COUNT_DIFFERS}, {@code false} otherwise
     */
    public boolean isFlag() {
        return this == OBLIQUE_ENDING || this == WORD_COUNT_DIFFERS;
    }
}
