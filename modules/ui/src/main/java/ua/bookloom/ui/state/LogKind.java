package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/**
 * The kinds of activity-log entry, each with its catalogue message, status role and a non-colour mark.
 *
 * <p>The mark exists because a role's colour alone cannot carry meaning for a colour-blind reader; every kind has its
 * own glyph so the log reads without colour.
 */
public enum LogKind {
    /** A segment was accepted. */
    ACCEPTED(MessageKey.LOG_ACCEPTED, StatusRole.SUCCESS, "✓"),
    /** A segment was accepted after a repair. */
    REPAIRED(MessageKey.LOG_REPAIRED, StatusRole.INFO, "✎"),
    /** A glossary term was applied to a segment. */
    GLOSSARY_APPLIED(MessageKey.LOG_GLOSSARY_APPLIED, StatusRole.INFO, "≡"),
    /** The running summary was updated. */
    SUMMARY_UPDATED(MessageKey.LOG_SUMMARY_UPDATED, StatusRole.INFO, "Σ"),
    /** A segment was sent to the model again. */
    RETRIED(MessageKey.LOG_RETRIED, StatusRole.WARNING, "↻"),
    /** A segment was flagged instead of accepted. */
    SEGMENT_ERROR(MessageKey.LOG_SEGMENT_ERROR, StatusRole.DANGER, "✕"),
    /** A run-level event: a stage started, a pause, a resume, the end. */
    MILESTONE(MessageKey.LOG_MILESTONE, StatusRole.INFO, "◆");

    private final MessageKey messageKey;
    private final StatusRole role;
    private final String mark;

    LogKind(final MessageKey messageKey, final StatusRole role, final String mark) {
        this.messageKey = messageKey;
        this.role = role;
        this.mark = mark;
    }

    /**
     * The catalogue message this kind is worded by.
     *
     * @return a defined catalogue key
     */
    public MessageKey messageKey() {
        return messageKey;
    }

    /**
     * The status role this kind is drawn in.
     *
     * @return the role
     */
    public StatusRole role() {
        return role;
    }

    /**
     * The glyph that identifies this kind without colour.
     *
     * @return a non-blank glyph, distinct from every other kind's
     */
    public String mark() {
        return mark;
    }
}
