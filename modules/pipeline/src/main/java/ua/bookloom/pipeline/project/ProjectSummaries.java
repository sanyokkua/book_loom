package ua.bookloom.pipeline.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;

/** Builds the project list: each stored project with the decisions that still belong to its file. */
@Slf4j
@RequiredArgsConstructor
final class ProjectSummaries {

    private final DocumentPort documents;
    private final ProjectRepository projects;
    private final SegmentRepository segments;

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
            summaries.add(
                    new ProjectSummary(project, decidedOf(project, Objects.requireNonNull(records.data(), "records"))));
        }
        summaries.sort(Comparator.comparing(
                        (ProjectSummary summary) -> summary.project().lastOpenedAt())
                .reversed()
                .thenComparing(summary -> summary.project().id()));
        log.debug(
                "list projects={} withDecisions={}",
                summaries.size(),
                summaries.stream()
                        .filter(summary -> summary.decidedSegments() > 0)
                        .count());
        return Result.ok(List.copyOf(summaries));
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
