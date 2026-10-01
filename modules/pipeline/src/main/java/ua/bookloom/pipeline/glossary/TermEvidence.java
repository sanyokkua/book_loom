package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;

/**
 * How a book uses each of a set of terms, for the model's review: every occurrence in any case, and the first
 * sentences that hold it. Counting lower-case uses too is the point, since a word the book mostly writes in lower case
 * is the clearest sign it is not a name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermEvidence {

    /** How many example sentences a term carries into the prompt. */
    static final int MAX_EXAMPLES = 2;

    /** An example longer than this is cut, so one long paragraph cannot crowd out the batch. */
    static final int MAX_EXAMPLE_CHARS = 200;

    private static final int MAX_TERM_WORDS = 3;

    /**
     * How the book uses one term.
     *
     * @param count how many times it occurs, in any case
     * @param examples up to {@link #MAX_EXAMPLES} sentences that hold it, in book order
     */
    record Evidence(int count, List<String> examples) {

        static final Evidence NONE = new Evidence(0, List.of());
    }

    /**
     * Gathers the evidence for the terms.
     *
     * @param segments the book's segments; never null
     * @param terms the terms as held; never null
     * @return the evidence by term as given; a term the book never uses maps to {@link Evidence#NONE}
     */
    static Map<String, Evidence> of(final List<Segment> segments, final Collection<String> terms) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(terms, "terms");
        final Map<String, String> termByKey = new HashMap<>();
        terms.forEach(term -> termByKey.put(keyOf(Occurrences.read(term).words()), term));
        final Map<String, Tally> tallies = new HashMap<>();
        for (final Segment segment : segments) {
            final Occurrences.Read read = Occurrences.read(segment.masked());
            final List<Occurrences.Word> words = read.words();
            for (int start = 0; start < words.size(); start++) {
                countWindows(read, start, termByKey, tallies);
            }
        }
        final Map<String, Evidence> evidence = new LinkedHashMap<>();
        terms.forEach(term ->
                evidence.put(term, tallies.getOrDefault(term, new Tally()).evidence()));
        log.debug(
                "Term evidence over {} segments for {} terms, {} seen", segments.size(), terms.size(), tallies.size());
        return evidence;
    }

    private static void countWindows(
            final Occurrences.Read read,
            final int start,
            final Map<String, String> termByKey,
            final Map<String, Tally> tallies) {
        final List<Occurrences.Word> words = read.words();
        for (int end = start + 1; end <= Math.min(words.size(), start + MAX_TERM_WORDS); end++) {
            final String term = termByKey.get(keyOf(words.subList(start, end)));
            if (term != null) {
                tallies.computeIfAbsent(term, _ -> new Tally())
                        .add(read.sentences().get(words.get(start).sentence()));
            }
        }
    }

    private static String keyOf(final List<Occurrences.Word> words) {
        return String.join(" ", words.stream().map(Occurrences.Word::key).toList());
    }

    /** One term's running count and examples. */
    private static final class Tally {

        private int count;
        private final List<String> examples = new ArrayList<>();

        void add(final String sentence) {
            count++;
            final String example =
                    sentence.length() > MAX_EXAMPLE_CHARS ? sentence.substring(0, MAX_EXAMPLE_CHARS) + "…" : sentence;
            if (examples.size() < MAX_EXAMPLES && !examples.contains(example)) {
                examples.add(example);
            }
        }

        Evidence evidence() {
            return new Evidence(count, List.copyOf(examples));
        }
    }
}
