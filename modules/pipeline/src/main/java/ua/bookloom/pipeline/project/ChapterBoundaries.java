package ua.bookloom.pipeline.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * A book's chapters, taken from its body structure by one rule for every format (design D10), so a chapter's summary
 * key and the place a batch ends come out the same on every run and every Continue. The story is the segments of the
 * non-auxiliary {@link UnitRole#BODY} units in reading order. The chapter level is the shallowest level of listed
 * structure nodes reaching the story that holds at least two of them (a single top node is looked through); each of
 * its nodes starts a chapter that runs to the next start, so a sub-heading stays inside and a spine file the
 * navigation does not list continues the chapter before it, while story text before the first start joins chapter 1.
 * A chapter under {@link #CHAPTER_MIN_TOKENS} joins the next, and a short last chapter joins the one before it. A book
 * with no such level is cut into pseudo-chapters of at most {@link #PSEUDO_CHAPTER_TOKENS} at paragraph ends.
 */
@Slf4j
public final class ChapterBoundaries {

    /** The fewest source tokens a chapter holds; a shorter one joins its neighbour. */
    public static final int CHAPTER_MIN_TOKENS = 300;

    /** The most source tokens a pseudo-chapter of a book with no chapter level holds, unless one paragraph is more. */
    public static final int PSEUDO_CHAPTER_TOKENS = 3_000;

    private static final String KEY_SEPARATOR = ":";
    private static final String NO_CHAPTER = "";

    private final List<Chapter> chapters;
    private final Map<String, Chapter> bySegment;

    /**
     * One chapter of the story.
     *
     * @param key the chapter's stable key, its first segment's unit id and position joined by a colon
     * @param number the chapter's place in the book, counting from 1
     * @param unitId the unit its first segment is in
     * @param firstSegment the position of its first segment in that unit's segments; zero or more
     * @param segmentCount how many story segments it holds; positive
     * @param segmentIds its segments' ids, in reading order; copied
     * @param tokens its source tokens, estimated in the source language
     * @param pseudo {@code true} if the book has no chapter level and the chapter was cut by size, {@code false}
     *     otherwise
     */
    public record Chapter(
            String key,
            int number,
            String unitId,
            int firstSegment,
            int segmentCount,
            List<String> segmentIds,
            int tokens,
            boolean pseudo) {

        /** Rejects a missing part and copies the ids. */
        public Chapter {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(unitId, "unitId");
            segmentIds = List.copyOf(Objects.requireNonNull(segmentIds, "segmentIds"));
        }
    }

    /** The story in reading order, with each segment's tokens and each unit's place in it. */
    private record Story(
            List<Segment> segments, List<Integer> prefix, Map<String, Integer> offsets, Map<String, Integer> sizes) {

        int size() {
            return segments.size();
        }

        int tokens(final int from, final int to) {
            return prefix.get(to) - prefix.get(from);
        }

        int offsetOf(final String unitId) {
            return Objects.requireNonNull(offsets.get(unitId), unitId);
        }
    }

    /** A listed structure node that reaches the story, with the earliest story position its subtree reaches. */
    private record Kept(String title, int start, List<Kept> children) {}

    /** A run of story positions, {@code from} included and {@code to} excluded. */
    private record Range(int from, int to) {}

    private ChapterBoundaries(final List<Chapter> chapters) {
        this.chapters = List.copyOf(chapters);
        final Map<String, Chapter> map = new HashMap<>();
        chapters.forEach(chapter -> chapter.segmentIds().forEach(id -> map.put(id, chapter)));
        this.bySegment = Map.copyOf(map);
    }

    /**
     * Finds the chapters of a book.
     *
     * @param document the non-null opened book
     * @param structure the non-null structure tree its inspection derived, in navigation order
     * @param sourceLanguage the language the book is written in, or {@code null} when unknown, for the token estimate
     * @return the chapters; none when the book has no story segment
     */
    public static ChapterBoundaries of(
            final Document document, final List<StructureNode> structure, final @Nullable String sourceLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(structure, "structure");
        log.debug(
                "Finding chapters document={} units={} topNodes={} sourceLanguage={}",
                document.id(),
                document.units().size(),
                structure.size(),
                sourceLanguage);
        final Story story = storyOf(document, sourceLanguage);
        if (story.size() == 0) {
            log.debug("No chapters in document={}: it has no story segment", document.id());
            return new ChapterBoundaries(List.of());
        }
        final List<Kept> level = chapterLevel(prune(structure, story));
        final boolean pseudo = level.size() < 2;
        final List<Range> cut = pseudo ? pseudoRanges(story) : nodeRanges(level, story);
        final List<Chapter> found = numbered(merged(cut, story), story, pseudo);
        log.debug(
                "Chapters of document={} pseudo={} cut={} kept={} firstSegments={}",
                document.id(),
                pseudo,
                cut.size(),
                found.size(),
                found.stream().map(Chapter::key).toList());
        return new ChapterBoundaries(found);
    }

    /**
     * Lists the chapters.
     *
     * @return never null; the chapters in reading order, empty when the book has no story
     */
    public List<Chapter> chapters() {
        return chapters;
    }

    /**
     * Finds the chapter a segment is in.
     *
     * @param segmentId the non-null segment id
     * @return the chapter if the segment is in the story, or empty if it is not (front or back matter, an auxiliary
     *     unit, an unknown id)
     */
    public Optional<Chapter> chapterOf(final String segmentId) {
        return Optional.ofNullable(bySegment.get(Objects.requireNonNull(segmentId, "segmentId")));
    }

    /**
     * Tells whether two segments are in the same chapter.
     *
     * @param first the non-null id of one segment
     * @param second the non-null id of the other
     * @return {@code true} if both are in the story and in one chapter, {@code false} otherwise
     */
    public boolean sameChapter(final String first, final String second) {
        final Optional<Chapter> chapter = chapterOf(first);
        return chapter.isPresent() && chapter.equals(chapterOf(second));
    }

    /**
     * Cuts consecutive segments where a chapter ends, so a chunk or a batch built from one part never holds two.
     * Segments outside the story stay together as long as they follow each other.
     *
     * @param segments the non-null segments, in reading order
     * @return never null; the runs of segments of one chapter each, in order; empty for no segment
     */
    public List<List<Segment>> split(final List<Segment> segments) {
        Objects.requireNonNull(segments, "segments");
        final List<List<Segment>> parts = new ArrayList<>();
        @Nullable String open = null;
        for (final Segment segment : segments) {
            final String key = chapterOf(segment.id()).map(Chapter::key).orElse(NO_CHAPTER);
            if (!key.equals(open)) {
                parts.add(new ArrayList<>());
                open = key;
            }
            parts.getLast().add(segment);
        }
        log.debug("Split segments={} into chapterParts={}", segments.size(), parts.size());
        return parts.stream().map(List::copyOf).toList();
    }

    private static Story storyOf(final Document document, final @Nullable String sourceLanguage) {
        final List<Segment> segments = new ArrayList<>();
        final Map<String, Integer> offsets = new HashMap<>();
        final Map<String, Integer> sizes = new HashMap<>();
        for (final Unit unit : document.units()) {
            if (unit.role() == UnitRole.BODY && !unit.isAuxiliary()) {
                offsets.put(unit.id(), segments.size());
                sizes.put(unit.id(), unit.segments().size());
                segments.addAll(unit.segments());
            }
        }
        final List<Integer> prefix = new ArrayList<>(List.of(0));
        segments.forEach(
                segment -> prefix.add(prefix.getLast() + TokenEstimator.estimate(segment.masked(), sourceLanguage)));
        log.debug("Story of document={} segments={} tokens={}", document.id(), segments.size(), prefix.getLast());
        return new Story(List.copyOf(segments), List.copyOf(prefix), Map.copyOf(offsets), Map.copyOf(sizes));
    }

    // Only listed nodes cut; one that reaches no story segment, with nothing under it that does, is dropped.
    private static List<Kept> prune(final List<StructureNode> nodes, final Story story) {
        final List<Kept> kept = new ArrayList<>();
        for (final StructureNode node : nodes) {
            if (!node.listed()) {
                log.trace("Chapter rule passes over unlisted node unit={}", node.unitId());
                continue;
            }
            final List<Kept> children = prune(node.children(), story);
            final OptionalInt start = IntStream.concat(
                            ownStart(node, story).stream(), children.stream().mapToInt(Kept::start))
                    .min();
            if (start.isPresent()) {
                kept.add(new Kept(node.title(), start.getAsInt(), children));
            }
        }
        return kept;
    }

    private static OptionalInt ownStart(final StructureNode node, final Story story) {
        final String unitId = node.unitId();
        final Integer size = unitId == null ? null : story.sizes().get(unitId);
        if (unitId == null || size == null) {
            return OptionalInt.empty();
        }
        final Integer first = node.firstSegment();
        final int position = first == null ? 0 : first;
        return position < size ? OptionalInt.of(story.offsetOf(unitId) + position) : OptionalInt.empty();
    }

    private static List<Kept> chapterLevel(final List<Kept> top) {
        List<Kept> level = top;
        int depth = 0;
        while (level.size() == 1) {
            log.debug("Chapter rule looks through the single node at depth={}", depth);
            level = level.getFirst().children();
            depth++;
        }
        log.debug("Chapter level depth={} nodes={}", depth, level.size());
        if (log.isTraceEnabled()) {
            log.trace("Chapter level titles {}", level.stream().map(Kept::title).toList());
        }
        return level;
    }

    // The first chapter starts at the story's first segment, so text before the first listed node joins it.
    private static List<Range> nodeRanges(final List<Kept> level, final Story story) {
        final List<Integer> starts = new ArrayList<>(
                level.stream().map(Kept::start).sorted().distinct().toList());
        starts.set(0, 0);
        final List<Range> ranges = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            final int to = index + 1 < starts.size() ? starts.get(index + 1) : story.size();
            ranges.add(new Range(starts.get(index), to));
        }
        return ranges;
    }

    // A cut never falls right after a heading: the heading then opens the next pseudo-chapter.
    private static List<Range> pseudoRanges(final Story story) {
        final List<Range> ranges = new ArrayList<>();
        int from = 0;
        for (int position = 1; position < story.size(); position++) {
            if (story.tokens(from, position + 1) <= PSEUDO_CHAPTER_TOKENS) {
                continue;
            }
            final boolean afterHeading = story.segments().get(position - 1).kind() == SegmentKind.HEADING;
            final int cut = afterHeading && position - 1 > from ? position - 1 : position;
            log.debug("Pseudo-chapter closes before position={} afterHeading={}", cut, afterHeading);
            ranges.add(new Range(from, cut));
            from = cut;
        }
        ranges.add(new Range(from, story.size()));
        return ranges;
    }

    private static List<Range> merged(final List<Range> ranges, final Story story) {
        final List<Range> kept = new ArrayList<>();
        int open = -1;
        for (final Range range : ranges) {
            open = open < 0 ? range.from() : open;
            if (story.tokens(open, range.to()) >= CHAPTER_MIN_TOKENS) {
                kept.add(new Range(open, range.to()));
                open = -1;
            } else {
                log.debug("Chapter from={} to={} is short; it joins the next", open, range.to());
            }
        }
        if (open >= 0 && kept.isEmpty()) {
            kept.add(new Range(open, story.size()));
        } else if (open >= 0) {
            log.debug("Last chapter from={} is short; it joins the one before", open);
            kept.set(kept.size() - 1, new Range(kept.getLast().from(), story.size()));
        }
        return kept;
    }

    private static List<Chapter> numbered(final List<Range> ranges, final Story story, final boolean pseudo) {
        return IntStream.range(0, ranges.size())
                .mapToObj(index -> chapter(index + 1, ranges.get(index), story, pseudo))
                .toList();
    }

    private static Chapter chapter(final int number, final Range range, final Story story, final boolean pseudo) {
        final List<Segment> segments = story.segments().subList(range.from(), range.to());
        final String unitId = segments.getFirst().unit();
        final int firstSegment = range.from() - story.offsetOf(unitId);
        return new Chapter(
                unitId + KEY_SEPARATOR + firstSegment,
                number,
                unitId,
                firstSegment,
                segments.size(),
                segments.stream().map(Segment::id).toList(),
                story.tokens(range.from(), range.to()),
                pseudo);
    }
}
