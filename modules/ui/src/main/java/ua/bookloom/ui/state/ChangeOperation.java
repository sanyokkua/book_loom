package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/** A model or scan operation of the names and style screen whose result the person is shown. */
public enum ChangeOperation {
    /** The glossary's model scan for names. */
    NAME_SCAN(MessageKey.RESULTS_OP_NAME_SCAN),
    /** The glossary's review with the model. */
    NAME_REVIEW(MessageKey.RESULTS_OP_NAME_REVIEW),
    /** The recurring terms' model scan. */
    TERM_SCAN(MessageKey.RESULTS_OP_TERM_SCAN),
    /** The recurring terms' review with the model. */
    TERM_REVIEW(MessageKey.RESULTS_OP_TERM_REVIEW),
    /** The model's suggestion of a rendering for each recurring term. */
    TERM_TRANSLATE(MessageKey.RESULTS_OP_TERM_TRANSLATE);

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
     * Whether the operation works on the recurring terms rather than the glossary.
     *
     * @return {@code true} for the term operations, {@code false} for the name operations
     */
    public boolean isAboutTerms() {
        return this == TERM_SCAN || this == TERM_REVIEW || this == TERM_TRANSLATE;
    }
}
