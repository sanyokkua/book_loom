package ua.bookloom.app.cli;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobStage;

/**
 * The time a run spent in each of its three stages, read from the stage-started events: a stage lasts until the next
 * one starts or the run ends, and a stage the run never entered took no time. Fed from the job's thread and read once
 * the job's run has returned.
 */
@Slf4j
final class StageTimes {

    private final Map<JobStage, Duration> spent = new EnumMap<>(JobStage.class);
    private @Nullable JobStage current;
    private @Nullable Instant since;

    /**
     * Starts a stage, ending the one in progress.
     *
     * @param stage the non-null stage that starts
     * @param at the moment it starts
     */
    void started(final JobStage stage, final Instant at) {
        Objects.requireNonNull(stage, "stage");
        ended(at);
        log.debug("run stage started stage={} at={}", stage, at);
        current = stage;
        since = at;
    }

    /**
     * Ends the stage in progress, if any.
     *
     * @param at the moment it ends
     */
    void ended(final Instant at) {
        Objects.requireNonNull(at, "at");
        final @Nullable JobStage stage = current;
        final @Nullable Instant start = since;
        if (stage == null || start == null) {
            return;
        }
        spent.merge(stage, Duration.between(start, at), Duration::plus);
        current = null;
        since = null;
    }

    /** {@return the time spent in each stage, every stage named; zero for one the run never entered} */
    Map<JobStage, Duration> spent() {
        final Map<JobStage, Duration> all = new EnumMap<>(JobStage.class);
        for (final JobStage stage : JobStage.values()) {
            all.put(stage, spent.getOrDefault(stage, Duration.ZERO));
        }
        return all;
    }
}
