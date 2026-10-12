package ua.bookloom.pipeline.index;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;

/**
 * A book read once into word postings, the reading order and each segment's place in it, so a name scan, a window or a
 * sharing question is answered from this structure instead of re-reading the book. Built from the segments' masked
 * text (each placeholder token read as one space); nothing here knows a language or a word list, only how the book
 * writes its words. Immutable after the build, so any thread may read it.
 */
@Slf4j
public final class BookIndex {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final IndexData data;
    private final long buildMillis;

    private BookIndex(final IndexData data, final long buildMillis) {
        this.data = data;
        this.buildMillis = buildMillis;
    }

    /**
     * Reads a book into an index.
     *
     * @param document the opened book; never null
     * @return the index; never null
     */
    public static BookIndex of(final Document document) {
        Objects.requireNonNull(document, "document");
        final long started = System.nanoTime();
        final IndexData data = IndexBuilder.build(document);
        final long millis = (System.nanoTime() - started) / NANOS_PER_MILLI;
        log.debug(
                "Book index of {}: {} segments, {} word forms, {} ms",
                document.id(),
                data.infos().size(),
                data.stats().size(),
                millis);
        return new BookIndex(data, millis);
    }

    /**
     * Counts the segments, auxiliary unit included.
     *
     * @return how many segments the book holds
     */
    public int size() {
        return data.infos().size();
    }

    /**
     * Counts the word forms, whatever their letter case.
     *
     * @return how many distinct word forms the book writes
     */
    public int distinctWords() {
        return data.stats().size();
    }

    /**
     * Tells what the build cost, for the log.
     *
     * @return how long the build took, in milliseconds
     */
    public long buildMillis() {
        return buildMillis;
    }

    /**
     * What the index knows of a segment.
     *
     * @param ordinal the position in reading order, from 0 to {@code size() - 1}
     * @return the segment's info; never null
     * @throws IndexOutOfBoundsException if {@code ordinal} is outside the book
     */
    public SegmentInfo segment(final int ordinal) {
        return data.infos().get(ordinal);
    }

    /**
     * Finds a segment's place in reading order.
     *
     * @param segmentId the segment's id; never null
     * @return its ordinal, or empty for an id the book does not hold
     */
    public OptionalInt ordinalOf(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        final Integer ordinal = data.ordinalById().get(segmentId);
        return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
    }

    /**
     * How the book writes a word.
     *
     * @param word the word in any letter case and either apostrophe; never null
     * @return its counts; all zero (first ordinal -1) for a word the book never writes
     */
    public WordStats stats(final String word) {
        Objects.requireNonNull(word, "word");
        return data.stats().getOrDefault(Occurrences.keyOf(word), WordStats.NONE);
    }

    /**
     * Lists where the book writes a word.
     *
     * @param word the word in any letter case and either apostrophe; never null
     * @return its occurrences in reading order; never null, empty for a word the book never writes
     */
    public List<Posting> postings(final String word) {
        Objects.requireNonNull(word, "word");
        return data.postings().getOrDefault(Occurrences.keyOf(word), List.of());
    }

    /**
     * Finds names in the book.
     *
     * @param names the names, each of one to three words; a longer name has no mention; never null
     * @param mode whether the letter case must match the name
     * @return every mention in reading order; never null
     */
    public List<Mention> mentions(final Collection<String> names, final Case mode) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(mode, "mode");
        log.debug("Mentions of {} names, mode {}", names.size(), mode);
        return MentionFinder.find(data, names, mode);
    }

    /**
     * Cuts the text around some of the mentions of one name.
     *
     * @param mentions the mentions in reading order; never null
     * @param plan how many windows, how many neighbouring segments and how many tokens each; never null
     * @return the windows in reading order, at most {@code plan.count()}; never null
     */
    public List<Window> windows(final List<Mention> mentions, final WindowPlan plan) {
        Objects.requireNonNull(mentions, "mentions");
        Objects.requireNonNull(plan, "plan");
        log.debug("Windows over {} mentions, plan {}", mentions.size(), plan);
        final WindowCutter cutter = new WindowCutter(data);
        return WindowPicker.choose(mentions, plan.count()).stream()
                .map(mention -> cutter.cut(mention, plan))
                .toList();
    }

    /**
     * The earlier segments that hold the most of some terms, for relevant earlier pairs.
     *
     * @param terms the terms, matched whatever their letter case and only as whole word runs; never null
     * @param beforeOrdinal only segments with a smaller ordinal count
     * @param limit how many ordinals to return at most
     * @return the ordinals, most terms first and the nearer segment first among equals; never null
     */
    public List<Integer> sharing(final Collection<String> terms, final int beforeOrdinal, final int limit) {
        Objects.requireNonNull(terms, "terms");
        log.debug("Sharing of {} terms before segment {}, limit {}", terms.size(), beforeOrdinal, limit);
        if (limit <= 0 || beforeOrdinal <= 0) {
            return List.of();
        }
        final Set<String> distinct = new LinkedHashSet<>();
        terms.forEach(term -> distinct.add(Occurrences.keyOf(term)));
        final Map<Integer, Integer> scores = new TreeMap<>();
        for (final String term : distinct) {
            MentionFinder.find(data, List.of(term), Case.INSENSITIVE).stream()
                    .map(Mention::ordinal)
                    .filter(ordinal -> ordinal < beforeOrdinal)
                    .distinct()
                    .forEach(ordinal -> scores.merge(ordinal, 1, Integer::sum));
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(
                                Map.Entry.<Integer, Integer>comparingByKey().reversed()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Lists the segments the name scans read.
     *
     * @return the ordinals of the non-auxiliary body segments, in reading order
     */
    public List<Integer> story() {
        return data.story();
    }
}
