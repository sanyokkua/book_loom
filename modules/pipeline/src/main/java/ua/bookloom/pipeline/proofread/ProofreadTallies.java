package ua.bookloom.pipeline.proofread;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Where a stored project's Proofread stage stands, with no model call (design D15 "Resume", "Estimate"): the round's
 * windows are planned by the stage's own rules from the mark list as it stands now — the deferrals the code part would
 * settle left out, as the run leaves them out — and a window is read when its focus holds the very fingerprint the run
 * compares ({@link WindowFingerprint#isRead}), so the Continue line and the run's own "continues at window" name the
 * same window. The code part's own work left — a renamed target not yet swept, a gender deferral the text already
 * agrees with — is answered apart, at every dial.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ProofreadTallies implements ProofreadProgress {

    private static final String PROJECT_NOT_READ = "The stored project could not be read.";

    private final OpenProjects open;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final DeferralRepository deferrals;
    private final DialProfiles profiles;

    /** What the windows of a stored project are planned and fingerprinted from. */
    private record Story(
            MarkList.Book book,
            Map<String, SegmentRecord> byId,
            Map<String, Segment> segments,
            BookMap map,
            int batchSize) {}

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
                    ErrorCode.internal, "The proofread windows could not be counted", PROJECT_NOT_READ, null, cause));
        }
    }

    @Override
    public Result<Boolean> codeWorkLeft(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("Proofread code work project={}", projectId);
            return projects.find(projectId)
                    .flatMap(found -> found.map(this::codeWork).orElseGet(() -> Result.ok(false)));
        } catch (RuntimeException cause) {
            log.error("Failed to read the proofread code work project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "The proofread work could not be read", PROJECT_NOT_READ, null, cause));
        }
    }

    private Result<Boolean> codeWork(final Project project) {
        final String projectId = project.id();
        final @Nullable String target = project.brief().targetLanguage();
        if (target == null) {
            log.debug("Proofread code work: none, no target language");
            return Result.ok(false);
        }
        return deferrals.open(projectId).flatMap(waiting -> {
            // A TERM deferral that carries a proposal was swept already and waits on the person, not on a Continue.
            if (waiting.stream()
                    .anyMatch(deferral -> deferral.reason() == DeferralReason.TERM && deferral.proposal() == null)) {
                log.debug("Proofread code work: a renamed target waits for the sweep");
                return Result.ok(true);
            }
            return glossary.all(projectId)
                    .flatMap(entries -> segments.all(projectId)
                            .map(records ->
                                    resolvable(waiting, byId(records), MarkList.byTerm(entries), project, target) > 0));
        });
    }

    private static long resolvable(
            final List<Deferral> waiting,
            final Map<String, SegmentRecord> byId,
            final Map<String, GlossaryEntry> byTerm,
            final Project project,
            final String target) {
        final long count = waiting.stream()
                .filter(deferral -> DeferralSettler.resolves(
                        deferral, byId, byTerm, project.brief().narrator(), target))
                .count();
        log.debug("Proofread code work: gender deferrals the code would settle={}", count);
        return count;
    }

    private Result<Optional<ProofreadTally>> tallied(
            final Project project, final Document document, final List<SegmentRecord> records, final int batchSize) {
        final BookBrief brief = project.brief();
        final ProofreadCoverage coverage = profiles.of(brief.dial()).proofreadCoverage();
        final boolean drafting = records.stream().anyMatch(record -> record.status() == SegmentStatus.PENDING);
        final @Nullable String target = brief.targetLanguage();
        if (coverage == ProofreadCoverage.NONE || drafting || target == null) {
            log.debug("Proofread tally: none (coverage={} drafting={})", coverage, drafting);
            return Result.ok(Optional.empty());
        }
        final String projectId = project.id();
        return glossary.all(projectId)
                .flatMap(entries -> lexicon.all(projectId)
                        .flatMap(terms -> deferrals.open(projectId).map(waiting -> {
                            final Map<String, SegmentRecord> byId = byId(records);
                            final List<Deferral> left = unsettled(waiting, byId, entries, project, target);
                            final Story story = new Story(
                                    new MarkList.Book(brief, document, entries, terms),
                                    byId,
                                    segmentsOf(document),
                                    BookMap.of(entries, terms, brief, document.units()),
                                    batchSize);
                            return rounds(story, MarkList.of(story.book(), records, left), coverage);
                        })));
    }

    // The run settles these before it lists the marks, so the tally lists the marks without them too.
    private static List<Deferral> unsettled(
            final List<Deferral> waiting,
            final Map<String, SegmentRecord> byId,
            final List<GlossaryEntry> entries,
            final Project project,
            final String target) {
        final Map<String, GlossaryEntry> byTerm = MarkList.byTerm(entries);
        return waiting.stream()
                .filter(deferral -> !DeferralSettler.resolves(
                        deferral, byId, byTerm, project.brief().narrator(), target))
                .toList();
    }

    private static Optional<ProofreadTally> rounds(
            final Story story, final List<Mark> marks, final ProofreadCoverage coverage) {
        final Set<SegmentKind> kept = story.book().brief().alsoTranslate().keptKinds();
        final Predicate<String> readable = MarkList.readable(story.byId(), kept);
        final Predicate<String> focusable = MarkList.focusable(story.byId(), kept);
        final List<String> order = List.copyOf(story.segments().keySet());
        final Optional<ProofreadTally> first = left(
                WindowFingerprint.ROUND_MARKED,
                ProofreadWindows.of(marks, order, readable, focusable, story.batchSize()),
                story);
        if (first.isPresent() || coverage != ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH) {
            return first;
        }
        return left(
                WindowFingerprint.ROUND_WHOLE_BOOK,
                ProofreadWindows.tiles(order, readable, focusable, story.batchSize(), MarkList.reasonsById(marks)),
                story);
    }

    // Counted as the stage counts a Continue: the windows the round holds now, those already read with the
    // fingerprint they have now, and the first one left.
    private static Optional<ProofreadTally> left(
            final int round, final List<ProofreadWindow> windows, final Story story) {
        final List<ProofreadWindow> unread =
                windows.stream().filter(window -> !isRead(round, window, story)).toList();
        log.debug("Proofread tally round={} windows={} left={}", round, windows.size(), unread.size());
        if (unread.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProofreadTally(
                round, windows.size(), windows.size() - unread.size(), windows.indexOf(unread.getFirst()) + 1));
    }

    private static boolean isRead(final int round, final ProofreadWindow window, final Story story) {
        final String fingerprint = WindowFingerprint.of(
                round,
                window,
                WindowReading.texts(window, story.segments(), story.byId()),
                story.map(),
                story.book().brief());
        // A window names only readable segments, so each holds a record.
        return WindowFingerprint.isRead(
                fingerprint,
                window,
                id -> Objects.requireNonNull(story.byId().get(id), id).proofread());
    }

    private static Map<String, SegmentRecord> byId(final List<SegmentRecord> records) {
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        return byId;
    }

    private static Map<String, Segment> segmentsOf(final Document document) {
        final Map<String, Segment> byId = new LinkedHashMap<>();
        MarkList.storySegments(document).forEach(segment -> byId.put(segment.id(), segment));
        return byId;
    }
}
