package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;

/**
 * The stages every whole-book eval runs the way the window does, through the production classes over one prepared
 * book: the autonomous names pass and the unattended translation job. A failed stage is noted in {@code errors} and the
 * run goes on, as the window lets it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WholeBookStages {

    /** Runs the names pass; returns the calls the probe has seen so far. */
    static int namesPass(
            final Prepared prepared,
            final StageProbe probe,
            @Nullable final Integer window,
            final List<String> errors) {
        log.debug("Names pass project={} window={}", prepared.projectId(), window);
        final Result<?> result = SequenceJobs.namesPass(prepared).run(prepared.projectId(), probe, window, event -> {});
        note("names pass", result, errors);
        return probe.mark();
    }

    /** Runs the translation job to its end and returns how it ended; a failed run is {@link JobState#FAILED}. */
    static JobState translate(
            final Prepared prepared,
            final StageProbe probe,
            @Nullable final Integer window,
            final List<String> errors) {
        log.debug("Translation project={} window={}", prepared.projectId(), window);
        final Result<JobReport> result = SequenceJobs.run(prepared, probe, ReviewMode.UNATTENDED, window, event -> {});
        note("translation", result, errors);
        return result.isOk() ? Objects.requireNonNull(result.data()).end() : JobState.FAILED;
    }

    /** Records a failed stage as {@code phase: code}. */
    static void note(final String phase, final Result<?> result, final List<String> errors) {
        if (result.isErr()) {
            errors.add(phase + ": " + Objects.requireNonNull(result.error()).code());
            log.warn("Eval {} ended in {}", phase, result.error().code());
        }
    }
}
