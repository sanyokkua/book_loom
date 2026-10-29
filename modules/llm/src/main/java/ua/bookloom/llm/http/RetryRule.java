package ua.bookloom.llm.http;

/**
 * What a matched error row says about trying the call again; the row decides, not the status, because the same status
 * means different things on different engines.
 */
public enum RetryRule {

    /** Sending the same request again cannot help: the request itself must change, or the person must act. */
    NONE,

    /** A passing fault of the server: the same request, sent once more, can succeed. */
    SAME,

    /** The model went astray on this sampling: the same request with another seed can succeed. */
    NEW_SEED,

    /** The server asked to slow down or is busy: wait with growing pauses before sending again. */
    BACKOFF,

    /** The server is getting ready (loading, no model loaded yet): wait for it by itself, without asking the person. */
    AUTO_WAIT
}
