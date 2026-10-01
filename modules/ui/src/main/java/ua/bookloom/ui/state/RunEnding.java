package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What a run's end says: a completed run is announced with its counts, and a failed one is routed by its state — a
 * refused start (validation) in place, any other failure in the blocking dialog. A cancellation never gets here,
 * because it ends the run stopped. Kept apart from {@link TranslatingViewModel} so that class stays a readable size.
 * FX thread only.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunEnding {

    /**
     * Routes the failure the mirror holds.
     *
     * @param mirror where the failure is read
     * @param errors where a failure that is not a refusal is shown
     * @return the notice to show in place for a refused start, or {@code null} when the dialog showed it or none was
     *     published
     */
    static RunNotice.@Nullable Refused routeFailure(final StateMirror mirror, final ErrorPresenter errors) {
        final AppError failure = mirror.failure().get();
        if (failure == null) {
            log.warn("the run failed but no failure was published to route");
            return null;
        }
        if (failure.code() == ErrorCode.validation) {
            log.debug("the run was refused with {}: shown in place", failure.code());
            return new RunNotice.Refused(failure);
        }
        log.debug("the run failed with {}: opening the error dialog", failure.code());
        errors.presentRunFailure(failure);
        return null;
    }

    /**
     * Announces a completed run with its accepted and flagged counts.
     *
     * @param mirror where the report or, before it, the running counts are read
     * @param toasts where the announcement goes
     */
    static void announce(final StateMirror mirror, final Toasts toasts) {
        final JobReport report = mirror.report().get();
        final int accepted =
                report != null ? report.accepted() : mirror.accepted().get();
        final int flagged = report != null ? report.flagged() : mirror.flagged().get();
        log.debug("run completed: {} accepted, {} flagged", accepted, flagged);
        if (flagged > 0) {
            toasts.warning(MessageKey.TOAST_RUN_FINISHED_FLAGGED, accepted, flagged);
        } else {
            toasts.success(MessageKey.TOAST_RUN_FINISHED, accepted);
        }
    }
}
