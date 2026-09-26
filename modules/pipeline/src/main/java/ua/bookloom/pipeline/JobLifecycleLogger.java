package ua.bookloom.pipeline;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Records the two lifecycle milestones of a job: its start with its inputs, and its end with its outcome. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class JobLifecycleLogger {

    static void started(
            final TranslationRequest request, final JobProgressTracker tracker, final Set<PausePoint> pausePoints) {
        log.info(
                "Translation job started format={} source={} destination={} targetLanguage={} sourceLanguage={} pausePoints={} segments={} sections={}",
                tracker.format(),
                request.source(),
                request.destination(),
                request.targetLanguage(),
                request.sourceLanguage(),
                pausePoints,
                tracker.segmentCount(),
                tracker.sectionCount());
    }

    static void ended(final JobReport report) {
        log.info(
                "Translation job ended state={} segments={} accepted={} flagged={} written={}",
                report.end(),
                report.segments(),
                report.accepted(),
                report.flagged(),
                report.written());
    }
}
