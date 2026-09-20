package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.PauseReason;

/** Records recoverable pauses without re-logging their original throwable. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class JobPauseLogger {

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
