package ua.bookloom.pipeline.export;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/** What an export says of its consistency pass: what the pass did, or, when it was off, what still waits. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportSummaries {

    static ConsistencySummary ran(final ConsistencyReport pass, final boolean withModel) {
        final ConsistencySummary.Status status =
                withModel ? ConsistencySummary.Status.RAN : ConsistencySummary.Status.RAN_WITHOUT_MODEL;
        log.debug(
                "export consistency summary status={} termSubstitutions={} genderReRenders={} openDeferrals={}"
                        + " neighbourFixes={} checks={}",
                status,
                pass.termSubstitutions(),
                pass.genderReRenders(),
                pass.openDeferrals(),
                pass.neighbourFixes(),
                pass.checks());
        return new ConsistencySummary(
                status,
                pass.termSubstitutions(),
                pass.genderReRenders(),
                pass.openDeferrals(),
                pass.neighbourFixes(),
                pass.checks(),
                pass.awaitingGender());
    }

    // With the pass switched off nothing ran, yet the segments that wait on a character's gender still wait: the
    // export names them from the open deferrals all the same.
    static ConsistencySummary notRun(final ConsistencyReport waiting) {
        log.debug(
                "export consistency summary status=NOT_RUN openDeferrals={} awaitingGender={}",
                waiting.openDeferrals(),
                waiting.awaitingGender().size());
        return waiting.openDeferrals().isEmpty()
                ? ConsistencySummary.NOT_RUN
                : new ConsistencySummary(
                        ConsistencySummary.Status.NOT_RUN,
                        0,
                        0,
                        waiting.openDeferrals(),
                        0,
                        ConsistencyChecks.NONE,
                        waiting.awaitingGender());
    }
}
