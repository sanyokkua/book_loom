package ua.bookloom.pipeline;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.WorkList;

/** Records the two lifecycle milestones of a job: its start with its inputs, and its end with its outcome. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class JobLifecycleLogger {

    static void started(
            final RunRequest request,
            final RunStart.Started run,
            final WorkList work,
            final Set<PausePoint> pausePoints) {
        log.info(
                "Translation job started project={} format={} sourceLanguage={} targetLanguage={} mode={} dial={} contextSize={} pausePoints={} segments={} sections={}",
                request.projectId(),
                run.document().format(),
                run.project().brief().sourceLanguage(),
                run.project().brief().targetLanguage(),
                request.mode(),
                run.project().brief().dial(),
                TokenBudget.EFFECTIVE_CONTEXT,
                pausePoints,
                work.segmentCount(),
                work.sectionCount());
    }

    static void ended(final JobReport report, final long elapsedMillis) {
        log.info(
                "Translation job ended state={} segments={} accepted={} flagged={} elapsedMs={}",
                report.end(),
                report.segments(),
                report.accepted(),
                report.flagged(),
                elapsedMillis);
    }
}
