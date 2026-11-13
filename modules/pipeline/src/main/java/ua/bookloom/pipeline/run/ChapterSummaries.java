package ua.bookloom.pipeline.run;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.lexicon.Lexicon;
import ua.bookloom.pipeline.memory.ChapterSummaryCall;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.pipeline.project.ChapterStandings;
import ua.bookloom.pipeline.project.ChapterStandings.Standing;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * Writes the chapters' summaries (design D10) on the job thread, so each call is single-flight with every other and the
 * next draft waits for it: before a run's first draft, the summary of every finished chapter that has none or whose
 * stored one was written from other source text (a Continue after a stop, a failure, a switch turned on mid-book or a
 * changed book); and during the run, the summary of the chapter a chunk commit finishes. The plan holds one step per
 * chapter while the switch is on. The last chapter is never summarised, since no later window reads it: its step, and a
 * stored summary's, are done at once with no call. A summary that is not kept or not stored is a warning, never the end
 * of the run, and is tried again on the next Continue; a pause aborting the call makes it again on resume, and a stop
 * keeps nothing of it. The switch is read from the stored brief at each of these places, since the person may change it
 * while the run is paused: turned on, the finished chapters are summarised at the next chunk's entry or commit, as a new
 * run's first pass would; turned off, no further summary is written and each finished chapter's planned step is done at
 * once with no call.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ChapterSummaries {

    private static final String ERROR = "error";
    private static final String STEP_LABEL = "summary";

    private final ChapterSummaryCall call;
    private final TranslatePlanner planner;
    private final RunStores stores;
    private final String projectId;
    private final Consumer<JobEvent> emit;
    private final LexiconFilter filter;
    private final Map<String, Segment> segments;
    private final RoutedCalls routed;
    private final StepAnnouncer steps;
    // The chapters this run has settled — summarised, tried, or done with no call — so none is asked twice in a run.
    private final Set<String> tried = new HashSet<>();
    // The switch as last read, so a turn from off to on is seen once.
    private boolean wasOn;
    private boolean planned;

    /**
     * Binds the summaries to one run.
     *
     * @param runSteps the non-null steps of the run: the chapter-summary call, the planner whose frame holds the
     *     chapters and which reads the brief's switch as stored now, and the announcer of the run's steps
     * @param stores the non-null stores the map is read from and the summary saved to
     * @param settings the non-null settings of the run
     * @param emit the non-null receiver of the kept-summary event
     * @param document the non-null book the chapters were found in
     * @param routed the non-null router that answers a call a pause or a stop aborted
     */
    ChapterSummaries(
            final RunSteps runSteps,
            final RunStores stores,
            final RunSettings settings,
            final Consumer<JobEvent> emit,
            final Document document,
            final RoutedCalls routed) {
        Objects.requireNonNull(runSteps, "runSteps");
        this.call = runSteps.summary();
        this.planner = runSteps.planner();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.projectId = settings.projectId();
        this.emit = Objects.requireNonNull(emit, "emit");
        this.filter = LexiconFilter.of(
                settings.frame().analysisLanguage(), settings.frame().targetLanguage());
        this.segments = Objects.requireNonNull(document, "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .collect(Collectors.toUnmodifiableMap(Segment::id, Function.identity(), (first, second) -> first));
        this.routed = Objects.requireNonNull(routed, "routed");
        this.steps = runSteps.calls().steps();
    }

    /**
     * Plans one summary step per chapter and writes every finished chapter's missing or stale summary, in reading
     * order, before the run's first draft. A store that cannot be read skips the pass: the run goes on without it.
     *
     * @param work the non-null work list, whose remaining segments tell an open chapter
     * @return empty to go on, or how a stop during a summary call ended the run
     */
    Optional<RunEnd> beforeDrafts(final WorkList work) {
        Objects.requireNonNull(work, "work");
        wasOn = planner.summariesOn();
        if (!wasOn) {
            log.debug("No chapter summaries before drafts: the brief's switch is off");
            return Optional.empty();
        }
        return writeFinished(work);
    }

    /**
     * Writes every finished chapter's missing or stale summary at a chunk's entry when the switch was turned on since
     * it was last read, as the run's first pass would have; nothing otherwise.
     *
     * @param work the non-null work list, whose remaining segments tell an open chapter
     * @return empty to go on, or how a stop during a summary call ended the run
     */
    Optional<RunEnd> beforeChunk(final WorkList work) {
        Objects.requireNonNull(work, "work");
        return turnedOn() ? writeFinished(work) : Optional.empty();
    }

    // Reads the switch now; true once for each turn from off to on.
    private boolean turnedOn() {
        final boolean on = planner.summariesOn();
        final boolean turned = on && !wasOn;
        wasOn = on;
        log.debug("Chapter summaries switch on={} turnedOn={}", on, turned);
        return turned;
    }

    private Optional<RunEnd> writeFinished(final WorkList work) {
        final List<Chapter> chapters = planner.frame().chapters().chapters();
        if (!planned) {
            steps.planned(StepKind.SUMMARY, chapters.size(), false);
            planned = true;
        }
        final Result<List<ChapterSummary>> read = stores.summaries().list(projectId);
        if (read.isErr()) {
            log.warn(
                    "Chapter summaries not read; none is written now reason={}",
                    Objects.requireNonNull(read.error(), ERROR).code());
            return Optional.empty();
        }
        return writeMissing(work, chapters, Objects.requireNonNull(read.data(), "summaries"));
    }

    // Only the chapters the book has now are looked at: a stored key the current boundaries give no chapter is left.
    private Optional<RunEnd> writeMissing(
            final WorkList work, final List<Chapter> chapters, final List<ChapterSummary> stored) {
        final Map<String, ChapterSummary> byKey = stored.stream()
                .collect(Collectors.toMap(ChapterSummary::chapterKey, Function.identity(), (first, second) -> second));
        final Map<Standing, Integer> counts = new EnumMap<>(Standing.class);
        for (final Chapter chapter : chapters) {
            final Optional<RunEnd> end = beforeDraft(work, chapter, byKey.get(chapter.key()), counts);
            if (end.isPresent()) {
                return end;
            }
        }
        final int missing = counts.getOrDefault(Standing.MISSING, 0);
        final int stale = counts.getOrDefault(Standing.STALE, 0);
        // Written after every commit now, so a pass that finds nothing to write is not a milestone.
        if (missing + stale > 0) {
            log.info(
                    "chapter summaries written missing={} stale={} stored={}",
                    missing,
                    stale,
                    counts.getOrDefault(Standing.STORED, 0));
        } else {
            log.debug("Chapter summaries: nothing to write stored={}", counts.getOrDefault(Standing.STORED, 0));
        }
        return Optional.empty();
    }

    private Optional<RunEnd> beforeDraft(
            final WorkList work,
            final Chapter chapter,
            @Nullable final ChapterSummary stored,
            final Map<Standing, Integer> counts) {
        final Standing standing = standing(work, chapter, stored);
        log.debug("Chapter {} key={} summary={}", chapter.number(), chapter.key(), standing);
        counts.merge(standing, 1, Integer::sum);
        return switch (standing) {
            case OPEN -> Optional.empty();
            case LAST, STORED -> {
                settled(chapter);
                yield Optional.empty();
            }
            case STALE, MISSING -> tried.add(chapter.key()) ? summarize(work, chapter) : Optional.empty();
        };
    }

    private Standing standing(final WorkList work, final Chapter chapter, @Nullable final ChapterSummary stored) {
        return ChapterStandings.standing(
                isOpen(work, chapter), planner.frame().chapters(), chapter, stored, held(chapter));
    }

    /**
     * Writes the summary of every chapter that is finished and has none after a chunk is committed: the chapter the
     * chunk finished, and any chapter that was finished by a skip or the review desk, whose segments no chunk of this
     * run commits.
     *
     * @param work the non-null work list, whose remaining segments tell an open chapter
     * @param items the non-null items of the committed chunk, in reading order
     * @return empty to go on, or how a stop during the summary call ended the run
     */
    Optional<RunEnd> afterCommit(final WorkList work, final List<WorkItem> items) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(items, "items");
        if (turnedOn()) {
            log.debug("Chapter summaries switch turned on; summarising the finished chapters");
            return writeFinished(work);
        }
        if (!wasOn) {
            closedOffAfter(work, items.getLast());
            return Optional.empty();
        }
        return writeFinished(work);
    }

    /**
     * Writes the summary of every finished chapter that has none, once the last chunk is committed and before the run
     * reports completion, so a chapter finished by a skip or the review desk at the end is not left to the next
     * Continue.
     *
     * @param work the non-null work list, whose remaining segments tell an open chapter
     * @return empty to go on, or how a stop during a summary call ended the run
     */
    Optional<RunEnd> beforeCompletion(final WorkList work) {
        Objects.requireNonNull(work, "work");
        if (!planner.summariesOn()) {
            log.debug("No chapter summaries at the end: the brief's switch is off");
            return Optional.empty();
        }
        return writeFinished(work);
    }

    // With the switch off, a step planned while it was on is done at once for the chapter the chunk finished.
    private void closedOffAfter(final WorkList work, final WorkItem last) {
        final Optional<Chapter> found =
                planner.frame().chapters().chapterOf(last.segment().id());
        if (found.isPresent() && !isOpen(work, found.get())) {
            closedOff(found.get());
        }
    }

    private boolean isOpen(final WorkList work, final Chapter chapter) {
        final Set<String> ids = Set.copyOf(chapter.segmentIds());
        final long open = work.remaining().stream()
                .filter(item -> ids.contains(item.segment().id()))
                .count();
        log.debug("Chapter {} key={} openSegments={}", chapter.number(), chapter.key(), open);
        return open > 0;
    }

    // A chapter finished while the switch is off: a step planned while it was on is done at once. The chapter is not
    // settled, so a switch turned on again later in the run still summarises it.
    private void closedOff(final Chapter chapter) {
        log.debug("No chapter summary chapter={} key={}: the brief's switch is off", chapter.number(), chapter.key());
        if (planned && !tried.contains(chapter.key())) {
            steps.instant(StepKind.SUMMARY, STEP_LABEL, chapter.key(), firstId(chapter), StepResult.NO_CALL);
        }
    }

    // A chapter that needs no call: its step is done at once, once per run.
    private void settled(final Chapter chapter) {
        if (tried.add(chapter.key())) {
            steps.instant(StepKind.SUMMARY, STEP_LABEL, chapter.key(), firstId(chapter), StepResult.NO_CALL);
        }
    }

    // The step stays open when a stop ends the run inside the call; a pause makes the whole call again on resume.
    private Optional<RunEnd> summarize(final WorkList work, final Chapter chapter) {
        steps.started(StepKind.SUMMARY, STEP_LABEL, chapter.key(), firstId(chapter));
        final List<Segment> held = held(chapter);
        final Result<List<GlossaryEntry>> glossary = stores.glossary().all(projectId);
        if (glossary.isErr()) {
            log.warn(
                    "Chapter summary not kept chapter={} key={} reason={}",
                    chapter.number(),
                    chapter.key(),
                    Objects.requireNonNull(glossary.error(), ERROR).code());
            steps.ended(StepKind.SUMMARY, chapter.key(), StepResult.FAILED);
            return Optional.empty();
        }
        final BookMap map = planner.map(
                Objects.requireNonNull(glossary.data(), "glossary"), new Lexicon(stores.lexicon()).entries(projectId));
        final List<String> lines = Tokens.visibleTexts(held);
        final List<String> decided = decidedTexts(chapter);
        return answered(
                chapter,
                held,
                routed.untilAnswered(work, null, () -> call.summarize(chapter, lines, decided, map, filter)));
    }

    // The chapter's decided target text in reading order, which the summary's words are checked against; a store that
    // cannot be read gives none, and the check is then off for this chapter.
    private List<String> decidedTexts(final Chapter chapter) {
        final Result<List<SegmentRecord>> read = stores.segments().all(projectId);
        if (read.isErr()) {
            log.warn(
                    "Decided text of chapter {} not read; the summary's words are not checked reason={}",
                    chapter.number(),
                    Objects.requireNonNull(read.error(), ERROR).code());
            return List.of();
        }
        final Map<String, SegmentRecord> byId = Objects.requireNonNull(read.data(), "records").stream()
                .collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (first, second) -> second));
        final List<String> decided = chapter.segmentIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(record -> record.status() != SegmentStatus.PENDING)
                .flatMap(record -> record.effectiveTarget().stream())
                .toList();
        log.debug("Decided text of chapter {} key={} segments={}", chapter.number(), chapter.key(), decided.size());
        return decided;
    }

    private Optional<RunEnd> answered(
            final Chapter chapter, final List<Segment> held, final Step<Optional<ChapterSummaryCall.Kept>> answer) {
        return switch (answer) {
            case Step.Stopped<Optional<ChapterSummaryCall.Kept>>(final RunEnd end) -> {
                log.debug("Chapter summary call chapter={} key={} ended with the run", chapter.number(), chapter.key());
                yield Optional.of(end);
            }
            case Step.Done<Optional<ChapterSummaryCall.Kept>>(final Optional<ChapterSummaryCall.Kept> kept) -> {
                final boolean stored = kept.isPresent() && store(chapter, held, kept.get());
                steps.ended(StepKind.SUMMARY, chapter.key(), stored ? StepResult.DONE : StepResult.FAILED);
                yield Optional.empty();
            }
        };
    }

    private List<Segment> held(final Chapter chapter) {
        return chapter.segmentIds().stream()
                .map(segments::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String firstId(final Chapter chapter) {
        return chapter.segmentIds().getFirst();
    }

    private boolean store(final Chapter chapter, final List<Segment> held, final ChapterSummaryCall.Kept kept) {
        final String sourceHash = ChapterStandings.sourceHash(held);
        final Result<ChapterSummary> saved = stores.summaries()
                .save(new ChapterSummary(
                        projectId,
                        chapter.key(),
                        chapter.unitId(),
                        chapter.firstSegment(),
                        chapter.segmentCount(),
                        kept.text(),
                        sourceHash));
        if (saved.isErr()) {
            log.warn(
                    "Chapter summary not stored chapter={} key={} reason={}",
                    chapter.number(),
                    chapter.key(),
                    Objects.requireNonNull(saved.error(), ERROR).code());
            return false;
        }
        log.debug(
                "Chapter summary stored chapter={} key={} sourceHash={}", chapter.number(), chapter.key(), sourceHash);
        emit.accept(new ChapterSummaryKept(chapter.key(), chapter.number(), kept.sentences(), kept.text()));
        return true;
    }
}
