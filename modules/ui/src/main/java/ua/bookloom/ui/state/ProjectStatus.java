package ua.bookloom.ui.state;

import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.i18n.MessageKey;

/** What a project row says about the project's translation, with the words and the colour meaning of its chip. */
public enum ProjectStatus {
    /** No run was ever started, or the latest one never began translating. */
    NOT_STARTED(MessageKey.PROJECTS_STATUS_NOT_STARTED, StateMarker.Meaning.WAITING),
    /** The latest run is translating in this window. */
    TRANSLATING(MessageKey.PROJECTS_STATUS_TRANSLATING, StateMarker.Meaning.WORKING),
    /** The latest run was paused; idle, so grey. */
    PAUSED(MessageKey.PROJECTS_STATUS_PAUSED, StateMarker.Meaning.WAITING),
    /** The latest run was cut off when the application closed or was ended; idle, so grey. */
    INTERRUPTED(MessageKey.PROJECTS_STATUS_INTERRUPTED, StateMarker.Meaning.WAITING),
    /** The latest run was stopped by the person; idle, so grey. */
    STOPPED(MessageKey.PROJECTS_STATUS_STOPPED, StateMarker.Meaning.WAITING),
    /** The latest run completed. */
    FINISHED(MessageKey.PROJECTS_STATUS_FINISHED, StateMarker.Meaning.DONE),
    /** The book was written out at least once. */
    EXPORTED(MessageKey.PROJECTS_STATUS_EXPORTED, StateMarker.Meaning.DONE),
    /** The latest run stopped with an error; the stores keep no failure code, so the chip names no cause. */
    FAILED(MessageKey.PROJECTS_STATUS_FAILED, StateMarker.Meaning.FAILED);

    private final MessageKey messageKey;
    private final StateMarker.Meaning meaning;

    ProjectStatus(final MessageKey messageKey, final StateMarker.Meaning meaning) {
        this.messageKey = messageKey;
        this.meaning = meaning;
    }

    /** {@return the catalogue entry of the chip's words} */
    public MessageKey messageKey() {
        return messageKey;
    }

    /** {@return the colour meaning of the chip} */
    public StateMarker.Meaning meaning() {
        return meaning;
    }
}
