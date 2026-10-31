package ua.bookloom.api.project;

import org.jspecify.annotations.Nullable;

/**
 * Where a glossary value (a target, a type, a gender) came from (design D3). The rank is the precedence of an
 * automatic update: a value from a lower origin never replaces a higher one by itself, and nothing automatic
 * replaces the person's value; the person may always edit any value.
 */
public enum Origin {

    /** The person typed, imported, confirmed or locked the value; no automatic update replaces it. */
    USER(0),

    /** The book proved the value: a verified model reading, a pronoun or past-tense count the code made. */
    BOOK(1),

    /** A rule of the code or a bundled list (a given-names file, a lock rule) set the value. */
    RULE(2),

    /** A guess nobody has confirmed (an unverified model answer, a weak count); shown as likely, never enforced. */
    LIKELY(3);

    /** The precedence rank: a lower rank is a higher origin. */
    private final int rank;

    Origin(final int rank) {
        this.rank = rank;
    }

    /**
     * Whether a value of this origin may replace a held value of the given origin.
     *
     * @param held the held value's origin, or null when no value is held
     * @return {@code true} if this origin is the person's, nothing is held, or the held value is automatic and of this
     *     origin or a lower one; {@code false} otherwise
     */
    public boolean mayReplace(@Nullable final Origin held) {
        return held == null || this == USER || (held != USER && rank <= held.rank);
    }

    /**
     * Whether the enforcing checks read a value of this origin.
     *
     * @return {@code true} for the person's and the book's values, {@code false} for a rule's or a guess
     */
    public boolean isEnforced() {
        return this == USER || this == BOOK;
    }
}
