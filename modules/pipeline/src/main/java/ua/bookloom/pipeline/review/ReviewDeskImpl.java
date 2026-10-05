package ua.bookloom.pipeline.review;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.AuditRecorder;

/**
 * The review panel's one seam: every action and read is delegated to its part, and whatever escapes a part is caught
 * here and answered as {@code internal}, logged once with its cause. It holds no status logic of its own — the status
 * machine is {@link SegmentActions}', the retry {@link RetryDraft}'s, the reads {@link ReviewQueries}' and the counts
 * {@link ReviewCounting}'s.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReviewDeskImpl implements ReviewDesk {

    private static final String ACTIONS = "SegmentActions";

    private final SegmentActions actions;
    private final ReviewQueries queries;
    private final RetryDraft retryDraft;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final AuditRecorder audits;

    @Override
    public Result<SegmentRecord> accept(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("accept project={} segment={} part={}", projectId, segmentId, ACTIONS);
        return guarded("accept", segmentId, () -> actions.accept(projectId, segmentId));
    }

    @Override
    public Result<SegmentRecord> saveEdit(final String projectId, final String segmentId, final String maskedText) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedText, "maskedText");
        log.debug(
                "saveEdit project={} segment={} length={} part={}", projectId, segmentId, maskedText.length(), ACTIONS);
        return guarded("saveEdit", segmentId, () -> actions.saveEdit(projectId, segmentId, maskedText));
    }

    @Override
    public Result<SegmentRecord> revert(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("revert project={} segment={} part={}", projectId, segmentId, ACTIONS);
        return guarded("revert", segmentId, () -> actions.revert(projectId, segmentId));
    }

    @Override
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(model, "model");
        log.debug(
                "retry project={} segment={} hasNote={} lowerTemperature={} part=RetryDraft",
                projectId,
                segmentId,
                note != null && !note.isBlank(),
                lowerTemperature);
        return guarded("retry", segmentId, () -> retryDraft.retry(projectId, segmentId, note, lowerTemperature, model));
    }

    @Override
    public Result<SegmentRecord> skip(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("skip project={} segment={} part={}", projectId, segmentId, ACTIONS);
        return guarded(
                "skip", segmentId, () -> actions.skip(projectId, segmentId).flatMap(next -> recordOf(projectId, next)));
    }

    @Override
    public Result<SegmentRecord> acceptProposal(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("acceptProposal project={} segment={} part={}", projectId, segmentId, ACTIONS);
        return guarded("acceptProposal", segmentId, () -> actions.acceptProposal(projectId, segmentId));
    }

    @Override
    public Result<List<SegmentView>> queue(final String projectId, final ReviewFilter filter) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(filter, "filter");
        return guarded("queue", null, () -> queries.queue(projectId, filter));
    }

    @Override
    public Result<SegmentView> segment(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        return guarded("segment", segmentId, () -> queries.segment(projectId, segmentId));
    }

    @Override
    public Result<ReviewCounts> counts(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded(
                "counts",
                null,
                () -> projectOf(projectId)
                        .flatMap(project -> segments.all(projectId)
                                .map(records -> ReviewCounting.count(
                                        records, project.brief().alsoTranslate().keptKinds()))));
    }

    @Override
    public Result<List<SuspiciousSegment>> audit(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("audit project={} part=AuditRecorder", projectId);
        return guarded("audit", null, () -> audits.run(projectId));
    }

    private Result<SegmentRecord> recordOf(final String projectId, final String segmentId) {
        return segments.find(projectId, segmentId)
                .flatMap(found -> found.map(Result::ok).orElseGet(() -> notFound(segmentId)));
    }

    private Result<Project> projectOf(final String projectId) {
        return projects.find(projectId).flatMap(found -> found.map(Result::ok).orElseGet(() -> notStored()));
    }

    private static <T> Result<T> notFound(final String segmentId) {
        return Result.err(AppError.of(
                ErrorCode.validation, "Segment not found", "This project holds no segment " + segmentId + "."));
    }

    private static <T> Result<T> notStored() {
        return Result.err(AppError.of(ErrorCode.validation, "Project not found", "This project is not stored."));
    }

    private static <T> Result<T> guarded(
            final String method, @Nullable final String segmentId, final Supplier<Result<T>> call) {
        try {
            return Objects.requireNonNull(call.get(), "part result");
        } catch (Throwable cause) {
            log.error("Review desk {} failed segment={} code={}", method, segmentId, ErrorCode.internal, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "The review desk failed",
                    "The review action could not be completed.",
                    null,
                    cause));
        }
    }
}
