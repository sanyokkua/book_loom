package ua.bookloom.api.pipeline;

/** Where one model call stands, as its snapshot shows it. */
public enum CallState {

    /** An attempt has gone out and no reply has come back yet. */
    WAITING,

    /** The provider answered; the snapshot holds the reply. */
    ANSWERED,

    /** The attempt failed; a later attempt of the same call may still go out and wait again. */
    FAILED,

    /** A stop or a pause ended the call before it was answered. */
    CANCELLED
}
