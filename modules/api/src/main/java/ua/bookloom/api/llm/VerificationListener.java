package ua.bookloom.api.llm;

import org.jspecify.annotations.Nullable;

/**
 * Hears a provider verification as it happens, so the Settings test card can show a request before its reply comes
 * back. Both methods are called on the verifying thread, never the window's; a listener hands what it hears on and
 * returns at once, because the request waits for it. Nothing heard here holds a key or a header value: the exchange is
 * masked exactly as the finished report's is.
 */
public interface VerificationListener {

    /** Hears nothing, for a verification nobody watches. */
    VerificationListener NONE = new VerificationListener() {};

    /**
     * Called just before one model request goes out: the inference stage's probe, or one call of the Advanced
     * features checks, each retry included.
     *
     * @param stage the stage sending it, {@link VerificationStage#INFERENCE} or {@link VerificationStage#ADVANCED}
     * @param check the Advanced check the call belongs to, or {@code null} for the inference stage
     * @param request the request so far: method, address, masked headers and body, no reply and a zero status
     */
    default void requestSent(VerificationStage stage, @Nullable AdvancedCheck check, VerificationExchange request) {}

    /**
     * Called once each stage has its outcome, before the next stage starts.
     *
     * @param outcome the stage's outcome with its exchange, as the finished report will hold it
     */
    default void stageFinished(StageOutcome outcome) {}
}
