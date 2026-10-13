package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;

/**
 * How a book uses each of a set of terms, for the model's review: every occurrence in any case, and the first
 * sentences that hold it. Counting lower-case uses too is the point, since a word the book mostly writes in lower case
 * is the clearest sign it is not a name. The occurrences are the book index's mentions (15i.N4), so a term is found by
 * the one matching rule every name reader shares.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermEvidence {

    /** How many example sentences a term carries into the prompt. */
    static final int MAX_EXAMPLES = 2;

    /** An example longer than this is cut, so one long paragraph cannot crowd out the batch. */
    static final int MAX_EXAMPLE_CHARS = 200;

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
     * Gathers the evidence for the terms from the book's index.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param terms the terms as held; never null
     * @return the evidence by term as given; a term the book never uses maps to {@link Evidence#NONE}
     */
    static Map<String, Evidence> of(
            final BookIndex index, final List<Integer> ordinals, final Collection<String> terms) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(terms, "terms");
        final Set<Integer> slice = new HashSet<>(ordinals);
        final Map<String, Tally> tallies = new HashMap<>();
        for (final Mention mention : index.mentions(new LinkedHashSet<>(terms), Case.INSENSITIVE)) {
            if (slice.contains(mention.ordinal())) {
                tallies.computeIfAbsent(mention.name(), _ -> new Tally()).add(sentenceOf(index, mention));
            }
        }
        final Map<String, Evidence> evidence = new LinkedHashMap<>();
        terms.forEach(term ->
                evidence.put(term, tallies.getOrDefault(term, new Tally()).evidence()));
        log.debug(
                "Term evidence over {} segments for {} terms, {} seen", ordinals.size(), terms.size(), tallies.size());
        return evidence;
    }

    private static String sentenceOf(final BookIndex index, final Mention mention) {
        return index.reads(List.of(mention.ordinal())).getFirst().sentences().get(mention.sentence());
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
