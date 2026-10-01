package ua.bookloom.pipeline.review;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.ForeignMarking;

/**
 * The reads the review panel and the Export screen make: the queue behind each filter chip and one segment's view.
 * Each read takes the switches of the brief as they are now, so a segment the person switched to "kept as source"
 * leaves every list but All segments. It logs nothing itself: a read is a pure view over the stored records.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReviewQueries {

    private static final String GLOSSARY_KIND = "glossary";
    private static final String OMISSION_KIND = "omission";

    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;

    /**
     * Lists the segments behind a filter chip.
     *
     * @param projectId the non-null project id
     * @param filter the non-null filter
     * @return the matching views in document order, or {@code validation} when the project is unknown or has no open
     *     book; never null, empty when nothing matches
     */
    public Result<List<SegmentView>> queue(final String projectId, final ReviewFilter filter) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(filter, "filter");
        return read(projectId)
                .map(reading -> reading.records().stream()
                        .filter(record -> reading.matches(record, filter))
                        .map(reading::view)
                        .toList());
    }

    /**
     * Reads one segment.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment's view, or {@code validation} when the project is unknown, has no open book, or holds no
     *     such segment
     */
    public Result<SegmentView> segment(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        return read(projectId)
                .flatMap(reading -> reading.records().stream()
                        .filter(record -> record.segmentId().equals(segmentId))
                        .findFirst()
                        .map(record -> Result.ok(reading.view(record)))
                        .orElseGet(() -> Result.err(refusal("This project holds no segment " + segmentId + "."))));
    }

    private Result<Reading> read(final String projectId) {
        final Result<Optional<Project>> project = projects.find(projectId);
        if (project.isErr()) {
            return Result.err(Objects.requireNonNull(project.error()));
        }
        final Optional<Project> found = Objects.requireNonNull(project.data());
        final Document document = openProjects.get(projectId);
        if (found.isEmpty() || document == null) {
            return Result.err(refusal("Open the book again before reviewing it."));
        }
        final Result<List<SegmentRecord>> records = segments.all(projectId);
        final Result<List<Deferral>> open = deferrals.open(projectId);
        if (records.isErr() || open.isErr()) {
            return Result.err(Objects.requireNonNull(records.isErr() ? records.error() : open.error()));
        }
        return Result.ok(new Reading(
                found.get().brief(),
                Objects.requireNonNull(records.data()),
                Objects.requireNonNull(open.data()),
                sourcesOf(document),
                SegmentLocators.of(document)));
    }

    private static Map<String, Segment> sourcesOf(final Document document) {
        final Map<String, Segment> byId = new HashMap<>();
        for (final Unit unit : document.units()) {
            unit.segments().forEach(segment -> byId.put(segment.id(), segment));
        }
        return byId;
    }

    private static AppError refusal(final String message) {
        return AppError.of(ErrorCode.validation, "Nothing to review", message);
    }

    /** What one read sees: the stored records, the opened book they came from and the brief's current switches. */
    private record Reading(
            BookBrief brief,
            List<SegmentRecord> records,
            List<Deferral> open,
            Map<String, Segment> sources,
            Map<String, SegmentLocator> locators) {

        private static final String MISSING = "The stored segment is not in the opened book: ";

        boolean matches(final SegmentRecord record, final ReviewFilter filter) {
            final boolean kept = record.isKeptAsSource(keptKinds());
            final boolean listed = !kept && record.status() == SegmentStatus.FLAGGED;
            return switch (filter) {
                case ALL_SEGMENTS -> kept || record.status() != SegmentStatus.PENDING;
                case ALL_FLAGGED -> listed;
                case NAMES -> listed && hasFinding(record, GLOSSARY_KIND);
                case OMISSIONS -> listed && hasFinding(record, OMISSION_KIND);
                case FOREIGN_KEPT -> listed && holdsForeignText(record);
            };
        }

        SegmentView view(final SegmentRecord record) {
            final Segment segment =
                    Objects.requireNonNull(sources.get(record.segmentId()), MISSING + record.segmentId());
            final SegmentLocator locator =
                    Objects.requireNonNull(locators.get(record.segmentId()), MISSING + record.segmentId());
            return new SegmentView(
                    record.segmentId(),
                    locator.text(),
                    record.kind(),
                    record.status(),
                    segment.masked(),
                    DisplayText.of(segment.masked()),
                    record.maskedMachineTarget(),
                    record.userTarget(),
                    record.maskedUserTarget(),
                    record.findings(),
                    record.judgeScore(),
                    record.isKeptAsSource(keptKinds()) ? SegmentPath.SOURCE_KEPT : record.path(),
                    record.reviewed(),
                    record.context(),
                    Proposals.waitingOn(open, record.segmentId())
                            .map(Deferral::proposal)
                            .orElse(null),
                    record.rejectedTarget());
        }

        private boolean holdsForeignText(final SegmentRecord record) {
            final Segment segment =
                    Objects.requireNonNull(sources.get(record.segmentId()), MISSING + record.segmentId());
            final String source = brief.sourceLanguage();
            return ForeignMarking.isMarked(segment, source, brief.foreignPassages())
                    || ProtectedSpans.mask(segment, source, brief.foreignPassages(), List.of()).spans().stream()
                            .anyMatch(span -> span.check() == CheckName.KEPT_RUN);
        }

        private Set<SegmentKind> keptKinds() {
            return brief.alsoTranslate().keptKinds();
        }

        private static boolean hasFinding(final SegmentRecord record, final String kind) {
            return record.findings().stream().anyMatch(finding -> finding.kind().equals(kind));
        }
    }
}
