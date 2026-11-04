package ua.bookloom.pipeline.run;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.typography.TypographyChoices;
import ua.bookloom.pipeline.typography.TypographyNormalizer;

/**
 * Re-checks the decided segments by code when a Brief key that shapes the stored text changes. Only the apostrophe and
 * the quote style do so today: the typography pass is run again over the stored target with the new keys, and a
 * segment it would still change gets a finding the review desk lists under "Worth a second look". The default address
 * and the declared conventions have no check that reads stored text yet, so a change to them re-checks nothing.
 * Nothing is drafted again and no target is rewritten; a status stays as it was, so a person decides what to do.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class BriefKeyRecheck {

    /** The {@code raisedBy} of the finding this re-check stores; the audit prefix lists it as a doubted segment. */
    public static final String RAISED_BY = AuditFindings.PREFIX + "normalised";

    private static final String KIND = "fluency";

    private final SegmentRepository segments;
    private final OpenProjects openProjects;

    /**
     * Re-checks a project's decided segments after a brief was saved.
     *
     * @param projectId the non-null project id
     * @param oldBrief the non-null brief before the change
     * @param newBrief the non-null brief as saved
     * @return how many segments carry the finding afterwards; 0 when no key that shapes stored text changed or the
     *     book is not open; the store's error when a read or a write fails
     */
    public Result<Integer> recheck(final String projectId, final BookBrief oldBrief, final BookBrief newBrief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(oldBrief, "oldBrief");
        Objects.requireNonNull(newBrief, "newBrief");
        final boolean typographyChanged = !Objects.equals(oldBrief.apostrophe(), newBrief.apostrophe())
                || !Objects.equals(oldBrief.quoteStyle(), newBrief.quoteStyle());
        final Document document = openProjects.get(projectId);
        final String target = newBrief.targetLanguage();
        log.debug(
                "Brief key re-check project={} typographyChanged={} open={} target={}",
                projectId,
                typographyChanged,
                document != null,
                target);
        if (!typographyChanged || document == null || target == null) {
            return Result.ok(0);
        }
        final Result<List<SegmentRecord>> records = segments.all(projectId);
        if (records.isErr()) {
            return Result.err(Objects.requireNonNull(records.error()));
        }
        final Keys keys = new Keys(
                TypographyChoices.of(oldBrief),
                TypographyChoices.of(newBrief),
                target,
                newBrief.alsoTranslate().keptKinds());
        return store(projectId, Objects.requireNonNull(records.data()), sourcesOf(document), keys);
    }

    /**
     * Adds the re-check to a stored change. A failed re-check must not undo a stored brief, so it is a warning and
     * the change is answered as saved with nothing to look at.
     *
     * @param before the non-null brief before the change
     * @param change the non-null answer of the save
     * @return the change with {@link ProjectChange.Saved#rechecked()} filled when it was a save; any other answer as
     *     it is
     */
    public ProjectChange after(final BookBrief before, final ProjectChange change) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(change, "change");
        if (!(change instanceof ProjectChange.Saved saved)) {
            return change;
        }
        final String id = saved.project().id();
        final Result<Integer> counted = recheck(id, before, saved.project().brief());
        if (counted.isErr()) {
            log.warn(
                    "Brief key re-check failed project={} code={}",
                    id,
                    Objects.requireNonNull(counted.error()).code());
            return change;
        }
        return new ProjectChange.Saved(saved.project(), Objects.requireNonNull(counted.data()));
    }

    private record Keys(TypographyChoices before, TypographyChoices after, String target, Set<SegmentKind> kept) {}

    private Result<Integer> store(
            final String projectId,
            final List<SegmentRecord> records,
            final Map<String, Segment> sources,
            final Keys keys) {
        int carrying = 0;
        for (final SegmentRecord record : records) {
            final Segment source = sources.get(record.segmentId());
            if (source == null || !isRechecked(record, keys.kept())) {
                continue;
            }
            final boolean doubted = isDoubted(source, record, keys);
            carrying += doubted ? 1 : 0;
            final Result<SegmentRecord> updated = doubted == carriesFinding(record)
                    ? Result.ok(record)
                    : segments.update(projectId, record.segmentId(), stored -> swap(stored, doubted));
            if (updated.isErr()) {
                return Result.err(Objects.requireNonNull(updated.error()));
            }
        }
        log.info("Brief key re-check finished project={} carrying={}", projectId, carrying);
        return Result.ok(carrying);
    }

    // A text that the old keys would also have changed was never normalised; that is not this change's doing.
    private static boolean isDoubted(final Segment source, final SegmentRecord record, final Keys keys) {
        final String stored = Objects.requireNonNull(record.maskedMachineTarget(), "masked target");
        final String now = normalised(source, stored, keys.target(), keys.after());
        return !now.equals(stored) && !now.equals(normalised(source, stored, keys.target(), keys.before()));
    }

    private static String normalised(
            final Segment source, final String stored, final String target, final TypographyChoices choices) {
        return TypographyNormalizer.normalise(source.masked(), stored, target, choices)
                .text();
    }

    private static SegmentRecord swap(final SegmentRecord stored, final boolean add) {
        final List<QaFinding> kept = new ArrayList<>(stored.findings().stream()
                .filter(finding -> !RAISED_BY.equals(finding.raisedBy()))
                .toList());
        if (add) {
            kept.add(new QaFinding(
                    KIND,
                    Severity.LOW,
                    "The apostrophe or the quote marks differ from the Book Brief's choice after it was changed.",
                    RAISED_BY));
        }
        return stored.withFindings(kept);
    }

    private static boolean carriesFinding(final SegmentRecord record) {
        return record.findings().stream().anyMatch(finding -> RAISED_BY.equals(finding.raisedBy()));
    }

    private static boolean isRechecked(final SegmentRecord record, final Set<SegmentKind> kept) {
        return (record.status() == SegmentStatus.ACCEPTED || record.status() == SegmentStatus.FLAGGED)
                && !record.reviewed()
                && record.userTarget() == null
                && record.maskedMachineTarget() != null
                && record.path() != SegmentPath.VERBATIM
                && record.path() != SegmentPath.SOURCE_KEPT
                && !record.isKeptAsSource(kept);
    }

    private static Map<String, Segment> sourcesOf(final Document document) {
        final Map<String, Segment> byId = new LinkedHashMap<>();
        for (final Unit unit : document.units()) {
            unit.segments().forEach(segment -> byId.put(segment.id(), segment));
        }
        return byId;
    }
}
