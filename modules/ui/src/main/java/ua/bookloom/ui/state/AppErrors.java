package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** The one shape of a failure a service threw instead of returning, so every such error reads the same. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AppErrors {

    /**
     * An internal error with no details: the cause's text may hold anything, so it never reaches the person.
     *
     * @param message what could not be done, in the person's words
     * @param cause what was thrown, kept for the log only; {@code null} when nothing was
     * @return the error
     */
    static AppError unexpected(final String message, final @Nullable Throwable cause) {
        return AppError.of(ErrorCode.internal, "Unexpected error", message, null, cause);
    }
}
