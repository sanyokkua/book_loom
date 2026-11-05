package ua.bookloom.pipeline.run;

import java.nio.charset.StandardCharsets;
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
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.lexicon.Lexicon;
import ua.bookloom.pipeline.memory.ChapterSummaryCall;
import ua.bookloom.pipeline.project.ChapterBoundaries;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.util.hash.HashUtil;

/**
 * Writes a chapter's summary right after the chunk commit that finishes it (design D10), on the job thread, so the call
 * is single-flight with every other and the next draft waits for it. The last chapter is never summarised, since no
 * later window reads it. A summary that is not kept or not stored is a warning, never the end of the run.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ChapterSummaries {

    // Masked sources are joined by a character no source holds, so two different chapters never hash the same.
    private static final String HASH_JOIN = "\u0000";

    private final ChapterSummaryCall call;
    private final TranslatePlanner planner;
    private final RunStores stores;
    private final String projectId;
    private final Consumer<JobEvent> emit;
    private final LexiconFilter filter;
    private final Map<String, Segment> segments;
    private final Set<String> tried = new HashSet<>();

    /**
     * Binds the summaries to one run.
     *
     * @param call the non-null chapter-summary call
     * @param planner the non-null planner, whose frame holds the chapters and the brief's switch
     * @param stores the non-null stores the map is read from and the summary saved to
     * @param settings the non-null settings of the run
     * @param emit the non-null receiver of the kept-summary event
     * @param document the non-null book the chapters were found in
     */
    ChapterSummaries(
            final ChapterSummaryCall call,
            final TranslatePlanner planner,
            final RunStores stores,
            final RunSettings settings,
            final Consumer<JobEvent> emit,
            final Document document) {
        this.call = Objects.requireNonNull(call, "call");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.projectId = settings.projectId();
        this.emit = Objects.requireNonNull(emit, "emit");
        this.filter = LexiconFilter.of(
                settings.frame().analysisLanguage(), settings.frame().targetLanguage());
        this.segments = Objects.requireNonNull(document, "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .collect(Collectors.toUnmodifiableMap(Segment::id, Function.identity(), (first, second) -> first));
    }

    /**
     * Summarises the chapter the committed chunk finished, if it finished one.
     *
     * @param work the non-null work list, whose remaining segments tell an open chapter
     * @param items the non-null items of the committed chunk, in reading order
     */
    void afterCommit(final WorkList work, final List<WorkItem> items) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(items, "items");
        if (!planner.frame().brief().summariesOn()) {
            log.debug("No chapter summary: the brief's switch is off");
            return;
        }
        final ChapterBoundaries chapters = planner.frame().chapters();
        final Optional<Chapter> found =
                chapters.chapterOf(items.getLast().segment().id());
        if (found.isEmpty()) {
            log.debug(
                    "No chapter summary: segment {} is in no chapter",
                    items.getLast().segment().id());
            return;
        }
        final Chapter chapter = found.get();
        if (isOpen(work, chapter) || isLast(chapters, chapter) || !tried.add(chapter.key())) {
            return;
        }
        summarize(chapter);
    }

    private boolean isOpen(final WorkList work, final Chapter chapter) {
        final Set<String> ids = Set.copyOf(chapter.segmentIds());
        final long open = work.remaining().stream()
                .filter(item -> ids.contains(item.segment().id()))
                .count();
        log.debug("Chapter {} key={} openSegments={}", chapter.number(), chapter.key(), open);
        return open > 0;
    }

    private static boolean isLast(final ChapterBoundaries chapters, final Chapter chapter) {
        final boolean last = chapter.number() == chapters.chapters().size();
        if (last) {
            log.debug(
                    "No chapter summary for chapter {}: it is the book's last, which no later window reads",
                    chapter.number());
        }
        return last;
    }

    private void summarize(final Chapter chapter) {
        final List<Segment> held = chapter.segmentIds().stream()
                .map(segments::get)
                .filter(Objects::nonNull)
                .toList();
        final Result<List<GlossaryEntry>> glossary = stores.glossary().all(projectId);
        if (glossary.isErr()) {
            log.warn(
                    "Chapter summary not kept chapter={} key={} reason={}",
                    chapter.number(),
                    chapter.key(),
                    Objects.requireNonNull(glossary.error(), "error").code());
            return;
        }
        final BookMap map = planner.map(
                Objects.requireNonNull(glossary.data(), "glossary"), new Lexicon(stores.lexicon()).entries(projectId));
        call.summarize(chapter, Tokens.visibleTexts(held), map, filter).ifPresent(kept -> store(chapter, held, kept));
    }

    private void store(final Chapter chapter, final List<Segment> held, final ChapterSummaryCall.Kept kept) {
        final String sourceHash = HashUtil.sha256Hex(
                String.join(HASH_JOIN, held.stream().map(Segment::masked).toList())
                        .getBytes(StandardCharsets.UTF_8));
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
                    Objects.requireNonNull(saved.error(), "error").code());
            return;
        }
        log.debug(
                "Chapter summary stored chapter={} key={} sourceHash={}", chapter.number(), chapter.key(), sourceHash);
        emit.accept(new ChapterSummaryKept(chapter.key(), chapter.number(), kept.sentences(), kept.text()));
    }
}
