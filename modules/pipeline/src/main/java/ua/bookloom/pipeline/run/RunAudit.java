package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.pipeline.audit.AuditRecorder;
import ua.bookloom.pipeline.checks.WordValidator;

/** Runs the final audit when a run completes. The audit is advice: a run that completed stays completed. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RunAudit {

    /**
     * Audits the project's accepted segments and stores what the audit finds, when the run completed.
     *
     * @param end the non-null state the run ended in; any other than COMPLETED audits nothing
     * @param stores the non-null stores the run decided into
     * @param words the non-null word validator the run used
     * @param projectId the non-null project id
     */
    public static void after(
            final JobState end, final RunStores stores, final WordValidator words, final String projectId) {
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(stores, "stores");
        if (end != JobState.COMPLETED) {
            return;
        }
        Objects.requireNonNull(words, "words");
        Objects.requireNonNull(projectId, "projectId");
        try {
            audit(stores, words, projectId);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable thrown) {
            // Only the type is logged: a check's message may quote the book.
            log.warn(
                    "Final audit failed project={} type={}; the run stays completed",
                    projectId,
                    thrown.getClass().getName());
        }
    }

    private static void audit(final RunStores stores, final WordValidator words, final String projectId) {
        final Result<List<SuspiciousSegment>> audited = new AuditRecorder(
                        stores.projects(), stores.segments(), stores.glossary(), stores.openProjects(), words)
                .run(projectId);
        if (audited.isErr()) {
            log.warn(
                    "Final audit could not run project={} code={}",
                    projectId,
                    Objects.requireNonNull(audited.error()).code());
        }
    }
}
