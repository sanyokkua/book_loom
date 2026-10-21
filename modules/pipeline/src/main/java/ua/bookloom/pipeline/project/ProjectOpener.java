package ua.bookloom.pipeline.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;

/**
 * Opens a stored project over its book file and points a book at another file. A file that is gone or is another
 * text is answered as data, and a run that no process is running any more is rewritten as cancelled.
 */
@Slf4j
@RequiredArgsConstructor
final class ProjectOpener {

    /** Registers the opened book and builds the answer; run only once the stored state is read. */
    @FunctionalInterface
    interface Finisher {
        ImportedBook finish(
                Project stored, BookInspection inspection, BookProfile profile, Document document, int segmentCount);
    }

    private final BookInspector inspector;
    private final DocumentPort documents;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final RunRepository runs;
    private final OpenProjects openProjects;
    private final Clock clock;

    Result<ProjectOpening> open(final Project stored, final Finisher finisher) {
        final Path source = stored.source();
        if (!Files.isRegularFile(source)) {
            log.warn("open project={}: the book file is missing", stored.id());
            return Result.ok(new ProjectOpening.BookFileMissing(stored));
        }
        final Document document = documents.open(source).data();
        if (document == null || !document.contentHash().equals(stored.contentHash())) {
            log.warn("open project={}: the book file is unreadable or another text", stored.id());
            if (document != null) {
                documents.close(document);
            }
            return Result.ok(new ProjectOpening.BookFileChanged(stored));
        }
        return closingUnlessImported(documents, document, () -> inspectAndKeep(stored, document, finisher));
    }

    private Result<ProjectOpening> inspectAndKeep(
            final Project stored, final Document document, final Finisher finisher) {
        return inspector.inspect(stored.source()).flatMap(inspection -> {
            if (inspection.verdict() != InspectionVerdict.READABLE) {
                return Result.ok(new ProjectOpening.BookFileChanged(stored));
            }
            return inspector
                    .profile(document)
                    .flatMap(profile -> segments.all(stored.id())
                            .flatMap(records -> interrupted(stored.id())
                                    .map(was -> opened(
                                            stored, inspection, profile, document, records.size(), was, finisher))));
        });
    }

    private ProjectOpening opened(
            final Project stored,
            final BookInspection inspection,
            final BookProfile profile,
            final Document document,
            final int count,
            final boolean interrupted,
            final Finisher finisher) {
        final Document previous = openProjects.remove(stored.id());
        if (previous != null) {
            log.debug("open replaces the open book of project={}", stored.id());
            documents.close(previous);
        }
        return new ProjectOpening.Opened(finisher.finish(stored, inspection, profile, document, count), interrupted);
    }

    // A stored RUNNING or PAUSED run belongs to a process that is gone: it is closed as cancelled, counts kept.
    private Result<Boolean> interrupted(final String projectId) {
        return runs.latest(projectId).flatMap(latest -> {
            final Optional<RunRecord> open =
                    latest.filter(run -> run.state() == JobState.RUNNING || run.state() == JobState.PAUSED);
            if (open.isEmpty()) {
                return Result.ok(false);
            }
            final RunRecord run = open.get();
            log.info("run interrupted project={} run={} was={}", projectId, run.runId(), run.state());
            return runs.save(new RunRecord(
                            run.runId(),
                            run.projectId(),
                            run.startedAt(),
                            clock.instant(),
                            JobState.CANCELLED,
                            run.accepted(),
                            run.flagged(),
                            run.model()))
                    .map(saved -> true);
        });
    }

    Result<BookRelocation> relocate(final Project stored, final Path chosen) {
        final Path normalized = chosen.toAbsolutePath().normalize();
        if (!StoredBooks.isUnchanged(stored, normalized, documents)) {
            log.warn("relocate project={}: the chosen file is another text or unreadable", stored.id());
            return Result.ok(new BookRelocation.DifferentBook(stored));
        }
        return projects.relocateBook(stored.contentHash(), normalized).map(moved -> {
            log.info("book relocated project={} projects={}", stored.id(), moved.size());
            log.trace("book relocated to {}", normalized);
            return new BookRelocation.Relocated(List.copyOf(moved));
        });
    }

    // The opened document is closed whenever the import does not complete, a failure that is thrown included.
    static <T> Result<T> closingUnlessImported(
            final DocumentPort documents, final Document document, final Supplier<Result<T>> finish) {
        boolean kept = false;
        try {
            final Result<T> done = Objects.requireNonNull(finish.get(), "result");
            kept = done.isOk() && !(done.data() instanceof ProjectOpening.BookFileChanged);
            return done;
        } finally {
            if (!kept) {
                documents.close(document);
            }
        }
    }
}
