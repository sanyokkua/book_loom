package ua.bookloom.ui.state;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.ui.control.StateMarker.Meaning;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.RunStateView.Chip;
import ua.bookloom.ui.state.RunStateView.Text;

/**
 * The AI-model chip and the live line of each run-state row (README "Run states", G11 colours). Pure; logs nothing,
 * because the row is worked out again on every tick of the run's clocks.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunChips {

    static Chip of(final RunRow row, final RunStateInputs in) {
        final String provider = in.model().provider();
        return switch (row) {
            case READY, READY_TO_CONTINUE -> new Chip(Meaning.WAITING, Text.of(MessageKey.RUN_CHIP_READY));
            case CANNOT_START ->
                in.model().chosen()
                        ? new Chip(Meaning.WAITING, Text.of(MessageKey.RUN_CHIP_READY))
                        : new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_NO_MODEL));
            case TRANSLATING, PAUSING, STOPPING -> new Chip(Meaning.WORKING, Text.of(MessageKey.RUN_CHIP_ANSWERING));
            case PAUSED, STOPPED, FINISHED_TO_REVIEW, FINISHED_NONE ->
                new Chip(Meaning.WAITING, Text.of(MessageKey.RUN_CHIP_IDLE));
            case WAITING ->
                isUnreachable(outageCode(in))
                        ? new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_UNREACHABLE, provider))
                        : new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_PROGRAM_ERROR, provider));
            case PAUSED_AFTER_ERROR ->
                isUnreachable(in.pausedOn())
                        ? new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_UNREACHABLE, provider))
                        : new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_ERROR));
            case STOPPED_TRYING ->
                new Chip(
                        Meaning.FAILED,
                        Text.of(MessageKey.RUN_CHIP_GAVE_UP, provider, TranslationJob.DEFAULT_MAX_OUTAGE.toHours()));
            case MODEL_MISSING -> new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_MISSING));
            case STUCK -> new Chip(Meaning.ATTENTION, Text.of(MessageKey.RUN_CHIP_STUCK, stuckClock(in)));
            case FAILED -> new Chip(Meaning.FAILED, Text.of(MessageKey.RUN_CHIP_ERROR));
        };
    }

    /**
     * What a waiting run last heard from its AI program: the latest probe's answer, else the call it paused on.
     *
     * @param in what is known about the run
     * @return the code, or {@code null} when nothing answered at all
     */
    static @Nullable ErrorCode outageCode(final RunStateInputs in) {
        final RecoveryState recovery = in.recovery();
        final ErrorCode probed = recovery == null ? null : recovery.probeFailure();
        return probed != null ? probed : in.pausedOn();
    }

    /**
     * Whether an error says the AI program did not answer, rather than that it answered with an error; a pause whose
     * code is not known is told as not answering, the commonest cause.
     */
    static boolean isUnreachable(final @Nullable ErrorCode code) {
        return code == null || code == ErrorCode.unreachable || code == ErrorCode.timeout;
    }

    static String stuckClock(final RunStateInputs in) {
        final WaitingCall waiting = in.waiting();
        return waiting == null ? RunStateView.clock(Duration.ZERO) : RunStateView.clock(waiting.attemptWaited());
    }

    static Text liveLine(final RunRow row, final RunStateInputs in) {
        final Object span = RunStateView.orNone(in.span());
        final Object continuesAt = RunStateView.orNone(in.continuesAt());
        return switch (row) {
            case READY -> Text.of(MessageKey.RUN_LIVE_READY);
            case READY_TO_CONTINUE -> Text.of(MessageKey.RUN_LIVE_CONTINUES, continuesAt);
            case CANNOT_START -> Text.of(MessageKey.RUN_LIVE_CANNOT_START);
            case TRANSLATING -> Text.of(MessageKey.RUN_LIVE_RUNNING, span);
            case PAUSING -> Text.of(MessageKey.RUN_LIVE_PAUSING, span);
            case PAUSED, PAUSED_AFTER_ERROR, STOPPED_TRYING, MODEL_MISSING -> Text.of(MessageKey.RUN_LIVE_PAUSED, span);
            case WAITING -> Text.of(MessageKey.RUN_LIVE_WAITING, in.model().provider(), span);
            case STUCK -> Text.of(MessageKey.RUN_LIVE_STUCK, span);
            case STOPPING -> Text.of(MessageKey.RUN_LIVE_STOPPING, span);
            case STOPPED -> Text.of(MessageKey.RUN_LIVE_STOPPED, continuesAt);
            case FINISHED_TO_REVIEW, FINISHED_NONE -> Text.of(MessageKey.RUN_LIVE_FINISHED);
            case FAILED -> Text.of(MessageKey.RUN_LIVE_FAILED, continuesAt);
        };
    }
}
