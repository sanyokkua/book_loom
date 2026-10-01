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
    ACCEPTED(MessageKey.LOG_ACCEPTED, StatusRole.SUCCESS, "✓", "ok"),
    /** One attempt of a model call was answered; its first argument names the call's kind. */
    MODEL_CALL(MessageKey.LOG_MODEL_CALL, StatusRole.INFO, "⇄", "call"),
    /** One attempt of a model call failed, timed out or was refused; its first argument names the call's kind. */
    CALL_FAILED(MessageKey.LOG_CALL_FAILED, StatusRole.WARNING, "⚠", "fail"),
    /** A segment the checks did not accept entered a repair round. */
    ROUND(MessageKey.LOG_ROUND, StatusRole.INFO, "↺", "round"),
    /** The glossary grew from a name scan, or a segment was reused from the translation memory. */
    GLOSSARY_APPLIED(MessageKey.LOG_GLOSSARY_APPLIED, StatusRole.INFO, "≡", "mem"),
    /** The rolling summary was refreshed. */
    SUMMARY_UPDATED(MessageKey.LOG_SUMMARY_UPDATED, StatusRole.INFO, "Σ", "sum"),
    /** The run resumed after a provider error. */
    RETRIED(MessageKey.LOG_RETRIED, StatusRole.WARNING, "↻", "retry"),
    /** A segment was flagged instead of accepted. */
    SEGMENT_ERROR(MessageKey.LOG_SEGMENT_ERROR, StatusRole.DANGER, "✕", "err"),
    /** A run-level event: a stage started, a pause, a resume, the end. */
    MILESTONE(MessageKey.LOG_MILESTONE, StatusRole.INFO, "◆", "info");

    private final MessageKey messageKey;
    private final StatusRole role;
    private final String mark;
    private final String tag;

    LogKind(final MessageKey messageKey, final StatusRole role, final String mark, final String tag) {
        this.messageKey = messageKey;
        this.role = role;
        this.mark = mark;
        this.tag = tag;
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

    /**
     * The short tag that starts this kind's entry in the log.
     *
     * @return one of {@code ok}, {@code call}, {@code fail}, {@code round}, {@code mem}, {@code sum}, {@code retry},
     *     {@code err} or {@code info}
     */
    public String tag() {
        return tag;
    }

    /**
     * Whether the entry's first argument is a call kind's token, which the log words by the catalogue's call names.
     *
     * @return {@code true} for a model call's line, {@code false} otherwise
     */
    public boolean namesACall() {
        return this == MODEL_CALL || this == CALL_FAILED;
    }

    /**
     * Whether the entry reports something that went wrong, which the log's errors-only view keeps.
     *
     * @return {@code true} for a warning or a danger, {@code false} otherwise
     */
    public boolean isTrouble() {
        return role == StatusRole.WARNING || role == StatusRole.DANGER;
    }
}
