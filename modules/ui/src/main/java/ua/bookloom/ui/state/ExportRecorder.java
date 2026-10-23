package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.project.ExportRecord;

/**
 * Keeps a written and checked export on its project, so the Export screen's tiles read what was written after a
 * restart. Only the window records; the command line keeps nothing. A store that fails costs one WARN line and never
 * the export the person already has on disk. Called off the FX thread.
 */
@Slf4j
final class ExportRecorder {

    private final ProjectRepository projects;
    private final Supplier<Instant> clock;

    ExportRecorder(final ProjectRepository projects, final Supplier<Instant> clock) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Stores the export's figures as the project's last export.
     *
     * @param projectId the project the book belongs to
     * @param report what the export wrote and checked
     */
    void record(final String projectId, final ExportReport report) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(report, "report");
        try {
            final Result<ExportRecord> stored = projects.recordExport(projectId, recordOf(report, clock.get()));
            final AppError error = stored.error();
            if (error != null) {
                log.warn("export record not stored project={} code={}", projectId, error.code());
                return;
            }
            log.info("export recorded project={} written={}", projectId, report.written());
        } catch (RuntimeException failed) {
            log.warn("export record not stored project={}: the store failed", projectId, failed);
        }
    }

    /**
     * The record an export's report gives.
     *
     * @param report what the export wrote and checked
     * @param at when it was written
     * @return never null
     */
    static ExportRecord recordOf(final ExportReport report, final Instant at) {
        return new ExportRecord(
                at,
                report.destination(),
                report.written(),
                report.pending(),
                report.sourceKept(),
                report.flaggedWritten(),
                report.autoAccepted(),
                report.reviewed(),
                report.verifiedSegments(),
                report.sideFiles());
    }
}
