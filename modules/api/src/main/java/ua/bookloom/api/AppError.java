package ua.bookloom.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The single failure type for the whole application.
 *
 * <p>There is deliberately no per-module error hierarchy. Eight error shapes would make the UI's rendering logic a
 * switch over eight types instead of one, and would let each module invent its own idea of what is safe to show a
 * user. One record, one classification enum, one allowlist.
 *
 * <p><strong>Privacy.</strong> {@code details} may only ever hold text produced by {@link SafeDetails} — the
 * allowlisted technical fields shown in an expandable panel. It must never be built by concatenating an exception
 * message, a request or response body, or a file's contents. {@code cause} is internal: it exists so the boundary
 * that first constructs the error can log it once, and is never rendered to the user.
 *
 * @param code the typed classification callers branch on
 * @param title a short user-facing heading
 * @param message a one-sentence user-facing explanation
 * @param details allowlisted technical detail from {@link SafeDetails}, or {@code null}
 * @param retryable derived from {@code code}; see {@link ErrorCode#isRetryable()}
 * @param cause the originating throwable for logs and diagnosis only, or {@code null}
 */
public record AppError(
        ErrorCode code,
        String title,
        String message,
        @Nullable String details,
        boolean retryable,
        @Nullable Throwable cause) {

    /**
     * Validates the invariants the rest of the application is entitled to assume.
     *
     * <p>The {@code retryable} check is the load-bearing one. The component exists because the specification fixes
     * the record's shape, but retryability is a property of the <em>code</em>, not a per-call-site judgement — so a
     * mismatch is rejected here rather than allowed to become two layers that disagree about whether the same
     * failure is worth retrying.
     */
    public AppError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
        if (retryable != code.isRetryable()) {
            throw new IllegalArgumentException(
                    "retryable must be derived from the code: " + code + " is retryable=" + code.isRetryable());
        }
    }

    /**
     * Builds an error with {@code retryable} derived from the code, and no technical detail.
     *
     * @param code the typed classification
     * @param title a short user-facing heading
     * @param message a one-sentence user-facing explanation
     * @return the error
     */
    public static AppError of(ErrorCode code, String title, String message) {
        Objects.requireNonNull(code, "code");
        return new AppError(code, title, message, null, code.isRetryable(), null);
    }

    /**
     * Builds an error with {@code retryable} derived from the code.
     *
     * @param code the typed classification
     * @param title a short user-facing heading
     * @param message a one-sentence user-facing explanation
     * @param details allowlisted detail from {@link SafeDetails}, or {@code null}
     * @param cause the originating throwable for logging, or {@code null}
     * @return the error
     */
    public static AppError of(
            ErrorCode code, String title, String message, @Nullable String details, @Nullable Throwable cause) {
        Objects.requireNonNull(code, "code");
        return new AppError(code, title, message, details, code.isRetryable(), cause);
    }
}
