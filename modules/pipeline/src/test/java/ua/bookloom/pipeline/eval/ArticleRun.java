package ua.bookloom.pipeline.eval;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobState;

/**
 * What one run of the English article into one target left behind.
 *
 * @param target the target language tag
 * @param end the job's terminal state
 * @param elapsed the wall time of the run, translation and export
 * @param calls the model calls that finished
 * @param segments the segments the run decided
 * @param flagged the decided segments left flagged
 * @param outputs every decided segment's stored machine target (masked) by id, in book order; a value is null when
 *     no target was stored
 * @param keptShare the share of the English Markdown's protected spans the exported Markdown holds verbatim
 * @param lostSpans the protected spans the exported Markdown lost; empty when none
 */
record ArticleRun(
        String target,
        JobState end,
        Duration elapsed,
        int calls,
        int segments,
        int flagged,
        Map<String, @Nullable String> outputs,
        double keptShare,
        List<String> lostSpans) {

    /** Copies the collections; the outputs keep their order and their null values. */
    ArticleRun {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(elapsed, "elapsed");
        outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        lostSpans = List.copyOf(lostSpans);
    }

    /** The share of decided segments left flagged; 0 when nothing was decided. */
    double flaggedRate() {
        return segments == 0 ? 0 : flagged / (double) segments;
    }
}
