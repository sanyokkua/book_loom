package ua.bookloom.pipeline.audit;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Runs the {@link FinalAudit} over a stored project and keeps what it finds as ordinary findings on the records, so
 * the review desk, the counts and the reports read them without a store of their own. Running it again replaces the
 * earlier audit findings: a segment that is now clean loses its mark, a changed one gets its new checks. Only the
 * findings of a changed record are written, each through {@link SegmentRepository#update}, so a run deciding other
 * segments meanwhile is never overwritten.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class AuditRecorder {

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final OpenProjects openProjects;
    private final WordValidator words;

    /**
     * Audits a project and stores the result.
     *
     * @param projectId the non-null project id
     * @return the doubted segments in document order; {@code validation} when the project is unknown or its book is
     *     not open, or the store's own error when a read or a write fails
     */
    public Result<List<SuspiciousSegment>> run(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Final audit starting project={}", projectId);
        final Document document = openProjects.get(projectId);
        final Result<Project> project = projectOf(projectId);
        if (project.isErr() || document == null) {
            return Result.err(project.isErr() ? Objects.requireNonNull(project.error()) : notOpen());
        }
        final Result<List<SegmentRecord>> records = segments.all(projectId);
        final Result<List<GlossaryEntry>> entries = glossary.all(projectId);
        if (records.isErr() || entries.isErr()) {
            return Result.err(Objects.requireNonNull(records.isErr() ? records.error() : entries.error()));
        }
        final FinalAudit.Book book = new FinalAudit.Book(
                Objects.requireNonNull(project.data()).brief(),
                document,
                Objects.requireNonNull(entries.data()),
                words);
        final Map<String, List<QaFinding>> doubted = FinalAudit.scan(book, Objects.requireNonNull(records.data()));
        return store(projectId, Objects.requireNonNull(records.data()), doubted).map(written -> {
            final List<SuspiciousSegment> named = FinalAudit.named(doubted, document);
            log.info(
                    "Final audit finished project={} suspicious={} recordsChanged={}",
                    projectId,
                    named.size(),
                    written);
            return named;
        });
    }

    private Result<Integer> store(
            final String projectId, final List<SegmentRecord> records, final Map<String, List<QaFinding>> doubted) {
        int changed = 0;
        for (final SegmentRecord record : records) {
            final List<QaFinding> fresh = doubted.getOrDefault(record.segmentId(), List.of());
            if (!isChanged(record, fresh)) {
                continue;
            }
            final Result<SegmentRecord> updated = segments.update(projectId, record.segmentId(), stored -> {
                final List<QaFinding> kept = new ArrayList<>(stored.findings().stream()
                        .filter(f -> !AuditFindings.isAudit(f))
                        .toList());
                kept.addAll(fresh);
                return stored.withFindings(kept);
            });
            if (updated.isErr()) {
                return Result.err(Objects.requireNonNull(updated.error()));
            }
            changed++;
        }
        return Result.ok(changed);
    }

    private static boolean isChanged(final SegmentRecord record, final List<QaFinding> fresh) {
        final List<QaFinding> held =
                record.findings().stream().filter(AuditFindings::isAudit).toList();
        return !held.equals(fresh);
    }

    private Result<Project> projectOf(final String projectId) {
        return projects.find(projectId)
                .flatMap(found -> found.map(Result::ok)
                        .orElseGet(() -> Result.err(AppError.of(
                                ErrorCode.validation, "Nothing to audit", "No stored project has this id."))));
    }

    private static AppError notOpen() {
        return AppError.of(ErrorCode.validation, "Nothing to audit", "Open the book again before auditing it.");
    }
}
