package ua.bookloom.pipeline.project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.ChapterTally;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.pipeline.project.ChapterStandings.Standing;

/**
 * Counts an open project's chapters for the Book Brief's chapter-summaries line: a chapter is finished when none of
 * its segments is still to be decided (the rule the run's work list follows: a pending record that is not kept as
 * source), and a finished chapter is to be summarised when {@link ChapterStandings} says the next Continue writes it.
 */
@Slf4j
@RequiredArgsConstructor
final class ChapterTallies {

    private final SegmentRepository segments;
    private final SummaryRepository summaries;
    private final OpenProjects openProjects;

    /**
     * Tallies one stored project.
     *
     * @param project the non-null stored project
     * @return the tally; a {@code validation} error when its book is not open, or the storage error that stopped a read
     */
    Result<ChapterTally> tally(final Project project) {
        Objects.requireNonNull(project, "project");
        final Document document = openProjects.get(project.id());
        final ChapterBoundaries chapters = openProjects.chapters(project.id(), project.brief());
        if (document == null || chapters == null) {
            log.debug("chapter tally project={}: no book is open", project.id());
            return Result.err(ProjectErrors.unknownProject(project.id()));
        }
        final Set<SegmentKind> kept = project.brief().alsoTranslate().keptKinds();
        return segments.all(project.id())
                .flatMap(records -> summaries
                        .list(project.id())
                        .map(stored -> counted(project.id(), document, chapters, open(records, kept), stored)));
    }

    // The ids the run's work list would still queue.
    private static Set<String> open(final List<SegmentRecord> records, final Set<SegmentKind> kept) {
        return records.stream()
                .filter(record -> record.status() == SegmentStatus.PENDING && !record.isKeptAsSource(kept))
                .map(SegmentRecord::segmentId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ChapterTally counted(
            final String projectId,
            final Document document,
            final ChapterBoundaries chapters,
            final Set<String> open,
            final List<ChapterSummary> stored) {
        final Map<String, Segment> byId = new HashMap<>();
        document.units().forEach(unit -> unit.segments().forEach(segment -> byId.putIfAbsent(segment.id(), segment)));
        final Map<String, ChapterSummary> byKey = stored.stream()
                .collect(Collectors.toMap(ChapterSummary::chapterKey, Function.identity(), (first, second) -> second));
        int finished = 0;
        int toSummarise = 0;
        for (final Chapter chapter : chapters.chapters()) {
            final boolean isOpen = chapter.segmentIds().stream().anyMatch(open::contains);
            final List<Segment> held = chapter.segmentIds().stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .toList();
            final Standing standing =
                    ChapterStandings.standing(isOpen, chapters, chapter, byKey.get(chapter.key()), held);
            finished += isOpen ? 0 : 1;
            toSummarise += standing.needsSummary() ? 1 : 0;
        }
        final ChapterTally tally = new ChapterTally(chapters.chapters().size(), finished, toSummarise);
        log.debug(
                "chapter tally project={} chapters={} finished={} toSummarise={}",
                projectId,
                tally.chapters(),
                tally.finished(),
                tally.toSummarise());
        return tally;
    }
}
