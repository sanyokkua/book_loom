package ua.bookloom.ui.notify;

import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The closed list of occasions on which the application raises a transient message.
 *
 * <p>A message with no named occasion is built, tested and then raised from wherever a screen felt like; making the
 * occasion the only way in keeps the list closed in the type system. Each occasion fixes its severity and its wording.
 * A message about what happens inside a dialog is not an occasion: it is a footer line ({@link Toasts#feedback}).
 */
public enum ToastOccasion {
    /** A book was opened; the argument is its file name. */
    BOOK_OPENED(Severity.SUCCESS, MessageKey.TOAST_BOOK_OPENED),
    /** A chosen file could not be compared with the stored projects and is opened anyway; the argument is its name. */
    IMPORT_UNCHECKED(Severity.WARNING, MessageKey.TOAST_IMPORT_UNCHECKED),
    /** A folder was dropped on the import screen instead of a book file. */
    IMPORT_FOLDER(Severity.WARNING, MessageKey.TOAST_IMPORT_FOLDER),
    /** The stored work could not be read by this version and was moved aside; the argument is the new file name. */
    STORAGE_SET_ASIDE(Severity.WARNING, MessageKey.TOAST_STORAGE_SET_ASIDE),
    /** A translation started; progress is saved as it goes. */
    RUN_STARTED(Severity.INFO, MessageKey.TOAST_RUN_STARTED),
    /** A translation ended with nothing to review; the argument is the translated count. */
    RUN_FINISHED(Severity.SUCCESS, MessageKey.TOAST_RUN_FINISHED),
    /** A translation ended with segments to review; the arguments are the accepted and the flagged counts. */
    RUN_FINISHED_FLAGGED(Severity.WARNING, MessageKey.TOAST_RUN_FINISHED_FLAGGED),
    /** A project's automatic name followed a language change; the argument is the new name. */
    PROJECT_NAME_CHANGED(Severity.INFO, MessageKey.TOAST_PROJECT_NAME_CHANGED),
    /** A review retry was refused because a translation is running. */
    RETRY_REFUSED(Severity.WARNING, MessageKey.REVIEW_BUSY),
    /** The model list could not be read where no dialog was asking for it. */
    MODEL_LIST_UNREADABLE(Severity.WARNING, MessageKey.TOAST_MODEL_LIST_UNREADABLE),
    /** Work that was continued in the background ended; carries the action that opens its results. */
    HIDDEN_WORK_FINISHED(Severity.SUCCESS, MessageKey.TOAST_HIDDEN_WORK_FINISHED),
    /** Other model work was refused while work runs in the background; carries the action that shows its progress. */
    HIDDEN_WORK_REFUSED(Severity.WARNING, MessageKey.WORK_HIDDEN_REFUSED);

    private final Severity severity;
    private final MessageKey key;

    ToastOccasion(final Severity severity, final MessageKey key) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.key = Objects.requireNonNull(key, "key");
    }

    /**
     * How the message is drawn and how long it stays.
     *
     * @return the occasion's severity
     */
    public Severity severity() {
        return severity;
    }

    /**
     * The catalogue entry whose wording the occasion uses.
     *
     * @return the entry; its first line is the title when it has two
     */
    public MessageKey key() {
        return key;
    }
}
