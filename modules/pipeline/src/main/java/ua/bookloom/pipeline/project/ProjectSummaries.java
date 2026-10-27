package ua.bookloom.pipeline.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;

/** Builds the project list: each stored project with the decisions that still belong to its file. */
@Slf4j
@RequiredArgsConstructor
final class ProjectSummaries {

    private final DocumentPort documents;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final RunRepository runs;

    Result<List<ProjectSummary>> list() {
        final Result<List<Project>> all = projects.all();
        if (all.isErr()) {
            return Result.err(Objects.requireNonNull(all.error(), "error"));
        }
        final List<ProjectSummary> summaries = new ArrayList<>();
        for (final Project project : Objects.requireNonNull(all.data(), "projects")) {
            final Result<List<SegmentRecord>> records = segments.all(project.id());
            if (records.isErr()) {
                return Result.err(Objects.requireNonNull(records.error(), "error"));
            }
            final List<SegmentRecord> stored = Objects.requireNonNull(records.data(), "records");
            final Result<ProjectSummary> summary = summaryOf(project, stored);
            if (summary.isErr()) {
                return Result.err(Objects.requireNonNull(summary.error(), "error"));
            }
            summaries.add(Objects.requireNonNull(summary.data(), "summary"));
        }
        summaries.sort(Comparator.comparing(
                        (ProjectSummary summary) -> summary.project().lastOpenedAt())
                .reversed()
                .thenComparing(summary -> summary.project().id()));
        logCounts(summaries);
        return Result.ok(List.copyOf(summaries));
    }

    private static void logCounts(final List<ProjectSummary> summaries) {
        log.debug(
                "list projects={} withDecisions={} withRuns={} exported={}",
                summaries.size(),
                summaries.stream().filter(s -> s.decidedSegments() > 0).count(),
                summaries.stream().filter(s -> s.latestRun() != null).count(),
                summaries.stream().filter(s -> s.lastExport() != null).count());
    }

    private Result<ProjectSummary> summaryOf(final Project project, final List<SegmentRecord> records) {
        final Result<Optional<RunRecord>> run = runs.latest(project.id());
        if (run.isErr()) {
            return Result.err(Objects.requireNonNull(run.error(), "error"));
        }
        final Result<Optional<ExportRecord>> export = projects.lastExport(project.id());
        if (export.isErr()) {
            return Result.err(Objects.requireNonNull(export.error(), "error"));
        }
        return Result.ok(new ProjectSummary(
                project,
                decidedOf(project, records),
                records.size(),
                orNull(Objects.requireNonNull(run.data(), "run")),
                orNull(Objects.requireNonNull(export.data(), "export"))));
    }

    private static <T> @Nullable T orNull(final Optional<T> value) {
        return value.orElse(null);
    }

    // Decisions of another text mean nothing, so a changed or missing file reports none; the file is read only when
    // there is something to lose.
    private int decidedOf(final Project project, final List<SegmentRecord> records) {
        final int decided = StoredBooks.countDecided(records);
        if (decided == 0 || StoredBooks.isUnchanged(project, project.source(), documents)) {
            return decided;
        }
        log.debug("project={} source changed or unreadable: its {} decisions are not counted", project.id(), decided);
        return 0;
    }
}
