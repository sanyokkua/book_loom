package ua.bookloom.pipeline;

import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.PauseReason;

/** Records recoverable pauses without re-logging their original throwable. */
@Slf4j
final class JobPauseLogger {

    private JobPauseLogger() {}

    static void recoveryPause(final AppError error, final PauseReason reason, final JobProgress progress) {
        log.warn(
                "Pausing translation job after recoverable error stage={} reason={} errorCode={} accepted={} flagged={} pending={}",
                progress.stage(),
                reason,
                error.code(),
                progress.accepted(),
                progress.flagged(),
                progress.pending());
    }
}
