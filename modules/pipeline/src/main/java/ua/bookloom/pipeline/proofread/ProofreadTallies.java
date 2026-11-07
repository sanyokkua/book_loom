package ua.bookloom.pipeline.proofread;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadProgress;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Where a stored project's Proofread stage stands, with no model call (design D15 "Resume", "Estimate"): the windows a
 * round has already read are counted from the stored fingerprints, one per window, and the windows left are planned
 * by the stage's own rules from the mark list as it stands now. A window whose focus all holds a verdict of the round
 * (or, for a first-round window, of Max's whole-book round) is read; the others are what a Continue reads.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ProofreadTallies implements ProofreadProgress {

    private final OpenProjects open;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final DeferralRepository deferrals;
    private final DialProfiles profiles;

    /** What one round's windows read now. */
    private record Round(int round, List<ProofreadWindow> windows) {}

    @Override
    public Result<Optional<ProofreadTally>> tally(final String projectId, final int batchSize) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("Proofread tally project={} batchSize={}", projectId, batchSize);
            final @Nullable Document document = open.get(projectId);
            if (document == null) {
                return Result.err(
                        AppError.of(ErrorCode.validation, "This project is not open", "No open project has this id."));
            }
            return projects.find(projectId)
                    .flatMap(found -> found.map(project -> segments.all(projectId)
                                    .flatMap(records -> tallied(project, document, records, Math.max(1, batchSize))))
                            .orElseGet(() -> Result.ok(Optional.empty())));
        } catch (RuntimeException cause) {
            log.error("Failed to count the proofread windows project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "The proofread windows could not be counted",
                    "The stored project could not be read.",
                    null,
                    cause));
        }
    }

    private Result<Optional<ProofreadTally>> tallied(
            final Project project, final Document document, final List<SegmentRecord> records, final int batchSize) {
        final BookBrief brief = project.brief();
        final ProofreadCoverage coverage = profiles.of(brief.dial()).proofreadCoverage();
        final boolean drafting = records.stream().anyMatch(record -> record.status() == SegmentStatus.PENDING);
        if (coverage == ProofreadCoverage.NONE || drafting || brief.targetLanguage() == null) {
            log.debug("Proofread tally: none (coverage={} drafting={})", coverage, drafting);
            return Result.ok(Optional.empty());
        }
        final String projectId = project.id();
        return glossary.all(projectId)
                .flatMap(entries -> lexicon.all(projectId)
                        .flatMap(terms -> deferrals
                                .open(projectId)
                                .map(waiting -> rounds(
                                        new MarkList.Book(brief, document, entries, terms),
                                        records,
                                        waiting,
                                        coverage,
                                        batchSize))));
    }

    private static Optional<ProofreadTally> rounds(
            final MarkList.Book book,
            final List<SegmentRecord> records,
            final List<Deferral> waiting,
            final ProofreadCoverage coverage,
            final int batchSize) {
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final Set<SegmentKind> kept = book.brief().alsoTranslate().keptKinds();
        final Predicate<String> readable = id -> byId.containsKey(id) && MarkList.isListed(byId.get(id), kept);
        final List<String> order = MarkList.storySegments(book.document()).stream()
                .map(Segment::id)
                .toList();
        final List<Mark> marks = MarkList.of(book, records, waiting);
        final Optional<ProofreadTally> first = left(
                new Round(WindowFingerprint.ROUND_MARKED, ProofreadWindows.of(marks, order, readable, batchSize)),
                byId);
        if (first.isPresent() || coverage != ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH) {
            return first;
        }
        final Map<String, List<String>> reasons =
                marks.stream().collect(Collectors.toMap(Mark::segmentId, Mark::reasons, (one, other) -> one));
        return left(
                new Round(
                        WindowFingerprint.ROUND_WHOLE_BOOK,
                        ProofreadWindows.tiles(order, readable, batchSize, reasons)),
                byId);
    }

    // The windows already read are the round's distinct stored fingerprints; the windows left those not yet read.
    private static Optional<ProofreadTally> left(final Round round, final Map<String, SegmentRecord> byId) {
        final String prefix = "r" + round.round() + ":";
        final long read = byId.values().stream()
                .map(SegmentRecord::proofread)
                .filter(Objects::nonNull)
                .map(ProofreadVerdict::fingerprint)
                .filter(fingerprint -> fingerprint.startsWith(prefix))
                .distinct()
                .count();
        final long unread = round.windows().stream()
                .filter(window -> !window.focusIds().stream().allMatch(id -> isRead(byId.get(id), round, prefix)))
                .count();
        log.debug("Proofread tally round={} read={} left={}", round.round(), read, unread);
        if (unread == 0) {
            return Optional.empty();
        }
        final int windows = Math.toIntExact(read + unread);
        return Optional.of(new ProofreadTally(round.round(), windows, (int) read, (int) read + 1));
    }

    private static boolean isRead(final @Nullable SegmentRecord record, final Round round, final String prefix) {
        final @Nullable ProofreadVerdict verdict = record == null ? null : record.proofread();
        return verdict != null
                && (verdict.fingerprint().startsWith(prefix)
                        || (round.round() == WindowFingerprint.ROUND_MARKED
                                && WindowFingerprint.isWholeBook(verdict.fingerprint())));
    }
}
