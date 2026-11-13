package ua.bookloom.pipeline.run;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.DetailCounts;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * Words the note of a segment flagged because its model call could not be used, so the review desk says what happened
 * to that passage instead of the provider's heading.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FlagNotes {

    private static final String NO_FIT = "This passage did not fit the model's window. It was flagged.";
    private static final String LOOPED = "The model kept repeating itself here.";

    /**
     * The note for a segment flagged by a model call's error.
     *
     * @param error the error the call answered
     * @return the sentence a person reads; the error's own message when no sentence fits it better
     */
    static String note(final AppError error) {
        Objects.requireNonNull(error, "error");
        final Optional<FailureReason> reason = FailureReason.in(error.details());
        if (error.code() == ErrorCode.contextWindow
                || reason.filter(FailureReason.CONTEXT_OVERFLOW::equals).isPresent()) {
            return overflow(error.details());
        }
        if (reason.filter(FailureReason.REPEAT_LIMIT::equals).isPresent()) {
            log.debug(
                    "Flag note chosen code={} reason={} note=looped", error.code(), FailureReason.REPEAT_LIMIT.token());
            return LOOPED;
        }
        log.debug("Flag note chosen code={} note=message", error.code());
        return error.message();
    }

    private static String overflow(@Nullable final String details) {
        final OptionalInt prompt = DetailCounts.promptTokensIn(details);
        final OptionalInt window = DetailCounts.windowTokensIn(details);
        final boolean counted = prompt.isPresent() && window.isPresent();
        log.debug("Flag note chosen reason=context-overflow counted={}", counted);
        return counted
                ? String.format(
                        Locale.ROOT,
                        "This passage did not fit the model's window (%d of %d tokens). It was flagged.",
                        prompt.getAsInt(),
                        window.getAsInt())
                : NO_FIT;
    }
}
