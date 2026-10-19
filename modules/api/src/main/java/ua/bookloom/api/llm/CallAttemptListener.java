package ua.bookloom.api.llm;

import ua.bookloom.api.ErrorCode;

/**
 * Hears each attempt of one model call, so a caller can show a stalled request attempt by attempt instead of as one
 * clock that keeps counting across the retries. Called on the thread that sends the call.
 */
public interface CallAttemptListener {

    /** A listener that hears nothing, for a caller that does not watch attempts. */
    CallAttemptListener NONE = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            // Nothing watches the attempts.
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            // Nothing watches the attempts.
        }
    };

    /**
     * An attempt has left for the provider.
     *
     * @param attempt the non-null attempt
     */
    void started(CallAttempt attempt);

    /**
     * An attempt was answered with a failure; another attempt may follow.
     *
     * @param attempt the non-null attempt that failed
     * @param code the non-null failure's code
     */
    void failed(CallAttempt attempt, ErrorCode code);
}
