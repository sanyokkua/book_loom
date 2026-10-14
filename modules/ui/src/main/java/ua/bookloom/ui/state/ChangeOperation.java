package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/** A model or scan operation of the names and style screen whose result the person is shown. */
public enum ChangeOperation {
    /** The glossary's model scan for names. */
    NAME_SCAN(MessageKey.RESULTS_OP_NAME_SCAN),
    /** The glossary's review with the model. */
    NAME_REVIEW(MessageKey.RESULTS_OP_NAME_REVIEW),
    /** The glossary's translate-only action: a suggested target for each row that has none. */
    NAME_TRANSLATE(MessageKey.RESULTS_OP_NAME_TRANSLATE),
    /** The recurring terms' scan: the text first, then the model. */
    TERM_SCAN(MessageKey.RESULTS_OP_TERM_SCAN),
    /** The recurring terms' review with the model. */
    TERM_REVIEW(MessageKey.RESULTS_OP_TERM_REVIEW),
    /** The model's suggestion of a rendering for each recurring term. */
    TERM_TRANSLATE(MessageKey.RESULTS_OP_TERM_TRANSLATE),
    /** The one autonomous pass that prepares both tables; its results hold a Names and a Recurring terms section. */
    AUTO_NAMES(MessageKey.RESULTS_OP_AUTO_NAMES);

    private final MessageKey label;

    ChangeOperation(final MessageKey label) {
        this.label = label;
    }

    /**
     * The operation's name as the results dialog heads itself with it.
     *
     * @return the catalogue key of the name
     */
    public MessageKey label() {
        return label;
    }

    /**
     * Whether the operation works on the glossary.
     *
     * @return {@code true} for the name operations and the automatic pass, {@code false} for the term operations
     */
    public boolean isAboutNames() {
        return this != TERM_SCAN && this != TERM_REVIEW && this != TERM_TRANSLATE;
    }

    /**
     * Whether the operation works on the recurring terms.
     *
     * @return {@code true} for the term operations and the automatic pass, {@code false} for the name operations
     */
    public boolean isAboutTerms() {
        return this == AUTO_NAMES || !isAboutNames();
    }

    /**
     * Whether the results list a section per table, because the operation changed both.
     *
     * @return {@code true} for the automatic pass, {@code false} for the others
     */
    public boolean isSectioned() {
        return this == AUTO_NAMES;
    }
}
