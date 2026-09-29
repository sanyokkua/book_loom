package ua.bookloom.ui;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;

/** Hand-built engine snapshots whose accepted segments were all accepted without repair. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProgressFixtures {

    /**
     * A translate-stage snapshot with the accepted count also given as its auto-accepted count and no repaired one.
     *
     * @param section the 1-based section
     * @param sections the number of sections
     * @param accepted accepted segments, all auto-accepted
     * @param flagged flagged segments
     * @param pending undecided segments
     * @return the snapshot, in chunk 1 of 1
     */
    public static JobProgress progress(
            final int section, final int sections, final int accepted, final int flagged, final int pending) {
        return new JobProgress(JobStage.TRANSLATE, section, sections, accepted, flagged, pending, 1, 1, accepted, 0);
    }
}
