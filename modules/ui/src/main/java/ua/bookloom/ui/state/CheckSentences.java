package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The sentence a check's result is read as: what was measured, in the words of the provider's name. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CheckSentences {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final double TENTHS_PER_SECOND = 10.0;

    /**
     * The sentence of a result.
     *
     * @param messages the catalogue the words come from
     * @param chip the finding
     * @param providerName the name the provider is shown by
     * @param modelChosen whether the test was asked about a chosen model; without one, a model list is only counted
     * @return the sentence, or empty when the finding has no sentence of its own and the generic chip words apply
     */
    public static Optional<String> of(
            final Messages messages, final StageChip chip, final String providerName, final boolean modelChosen) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(chip, "chip");
        Objects.requireNonNull(providerName, "providerName");
        if (chip.status() == StageStatus.SKIPPED) {
            return Optional.of(messages.get(MessageKey.SETTINGS_CHECK_SKIPPED, stageName(messages, chip.stage())));
        }
        return switch (chip.stage()) {
            case CONNECTION -> connection(messages, chip, providerName);
            case MODELS -> models(messages, chip, modelChosen);
            case INFERENCE -> inference(messages, chip);
        };
    }

    private static Optional<String> connection(
            final Messages messages, final StageChip chip, final String providerName) {
        final Duration elapsed = chip.elapsed();
        if (chip.status() == StageStatus.FAILED) {
            return Optional.of(messages.get(failedHeadline(chip.error()), providerName));
        }
        return chip.status() == StageStatus.PASSED && elapsed != null
                ? Optional.of(messages.get(
                        MessageKey.SETTINGS_CHECK_CONNECTION_OK, providerName, durationText(messages, elapsed)))
                : Optional.empty();
    }

    // Only silence reads as "does not answer". An unset key variable stops the request before it is sent, and a refused
    // sign-in or an error reply means the server did answer: each headline names that, and the line under it the fix.
    private static MessageKey failedHeadline(final @Nullable AppError error) {
        final ErrorCode code = error == null ? ErrorCode.unreachable : error.code();
        return switch (code) {
            case missingCredential -> MessageKey.SETTINGS_CHECK_CONNECTION_NO_KEY;
            case auth -> MessageKey.SETTINGS_CHECK_CONNECTION_REFUSED;
            case unreachable, timeout, cancelled -> MessageKey.SETTINGS_CHECK_CONNECTION_FAILED;
            case rateLimited,
                    modelNotFound,
                    discoveryFailed,
                    modelUnavailable,
                    upstream,
                    contextWindow,
                    emptyCompletion,
                    validation,
                    internal,
                    busy -> MessageKey.SETTINGS_CHECK_CONNECTION_ANSWERED;
        };
    }

    private static Optional<String> models(final Messages messages, final StageChip chip, final boolean modelChosen) {
        final Integer count = chip.count();
        if (chip.status() == StageStatus.SOFT_PASS) {
            return Optional.of(messages.get(MessageKey.SETTINGS_CHECK_MODELS_SOFT));
        }
        return chip.status() == StageStatus.PASSED && count != null
                ? Optional.of(messages.get(
                        modelChosen ? MessageKey.SETTINGS_CHECK_MODELS_OK : MessageKey.SETTINGS_CHECK_MODELS_LISTED,
                        count))
                : Optional.empty();
    }

    private static Optional<String> inference(final Messages messages, final StageChip chip) {
        final Duration elapsed = chip.elapsed();
        return chip.status() == StageStatus.PASSED && elapsed != null
                ? Optional.of(messages.get(MessageKey.SETTINGS_CHECK_INFERENCE_OK, durationText(messages, elapsed)))
                : Optional.empty();
    }

    /**
     * A measured time as a person reads it.
     *
     * @param messages the catalogue the units come from
     * @param elapsed the time
     * @return milliseconds below a second, else seconds with one decimal
     */
    public static String durationText(final Messages messages, final Duration elapsed) {
        final long millis = elapsed.toMillis();
        return millis < MILLIS_PER_SECOND
                ? messages.get(MessageKey.SETTINGS_DURATION_MS, millis)
                : messages.get(
                        MessageKey.SETTINGS_DURATION_S,
                        Math.round(millis / (double) MILLIS_PER_SECOND * TENTHS_PER_SECOND) / TENTHS_PER_SECOND);
    }

    /**
     * The name of a stage.
     *
     * @param messages the catalogue the name comes from
     * @param stage the stage
     * @return its name in the person's language
     */
    public static String stageName(final Messages messages, final VerificationStage stage) {
        return messages.get(
                switch (stage) {
                    case CONNECTION -> MessageKey.SETTINGS_STAGE_CONNECTION;
                    case MODELS -> MessageKey.SETTINGS_STAGE_MODELS;
                    case INFERENCE -> MessageKey.SETTINGS_STAGE_INFERENCE;
                });
    }
}
