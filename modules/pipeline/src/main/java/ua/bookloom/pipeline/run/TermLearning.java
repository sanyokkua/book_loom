package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.lexicon.CooccurrenceLearner;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Teaches the lexicon what the run has already decided: the translated pairs of a chunk are counted by the
 * {@link CooccurrenceLearner} once the chunk is stored, and a term whose rendering the counts establish gets it as its
 * learned rendering. A pair is counted only after its record is committed, so a stop never leaves a count for a
 * decision that was not kept; a run that continues over stored decisions replays them first, which gives the same
 * counts. A term the glossary holds is never learned — the person's lock decides it — and neither is a word the
 * glossary holds as a name. The lexicon is a hint, so a store that cannot be read or written is logged and skipped.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class TermLearning {

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{M}'’ʼ-]+");

    /** A decided pair, tokens removed. */
    record Pair(String source, String target) {}

    private final CooccurrenceLearner learner = new CooccurrenceLearner();
    private final SegmentRepository segments;
    private final LexiconRepository lexicon;
    private final GlossaryRepository glossary;
    private final String projectId;
    private final List<Pair> held = new ArrayList<>();

    TermLearning(final RunStores stores, final String projectId) {
        Objects.requireNonNull(stores, "stores");
        this.segments = stores.segments();
        this.lexicon = stores.lexicon();
        this.glossary = stores.glossary();
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /**
     * The pair a decided record teaches: an accepted machine draft, repaired or not. A segment kept verbatim, a
     * flagged one, a memory reuse and the person's own edit teach nothing — a reuse would count one passage twice.
     */
    static Optional<Pair> pairOf(final Segment segment, final SegmentRecord record) {
        final String target = record.machineTarget();
        final boolean drafted = record.path() == SegmentPath.DRAFT || record.path() == SegmentPath.REPAIRED;
        if (record.status() != SegmentStatus.ACCEPTED || !drafted || target == null) {
            return Optional.empty();
        }
        final Pair pair = new Pair(Tokens.replace(segment.masked(), " "), Tokens.replace(target, " "));
        return pair.target().isBlank() ? Optional.empty() : Optional.of(pair);
    }

    /** Holds the pair of a decision until its chunk is stored. */
    void decided(final Segment segment, final SegmentRecord record) {
        pairOf(segment, record).ifPresent(held::add);
    }

    /** Counts the held pairs, now that their records are stored, and publishes what they establish. */
    void committed() {
        final List<Pair> stored = List.copyOf(held);
        held.clear();
        log.debug("Learning from a stored chunk project={} pairs={}", projectId, stored.size());
        learn(stored, false);
    }

    /**
     * Rebuilds the counts from the decisions stored before this run, and publishes for every tracked term, so a run
     * that continues learns what a run that never stopped would have.
     *
     * @param decided the book's segments that were decided before this run, in document order
     */
    void replay(final List<Segment> decided) {
        Objects.requireNonNull(decided, "decided");
        held.clear();
        final List<SegmentRecord> records = segments.all(projectId).data();
        if (records == null) {
            log.warn("Replay skipped project={}: the stored decisions could not be read", projectId);
            return;
        }
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, record -> record, (a, b) -> b));
        final List<Pair> stored = decided.stream()
                .flatMap(segment ->
                        Optional.ofNullable(byId.get(segment.id())).flatMap(record -> pairOf(segment, record)).stream())
                .toList();
        log.debug(
                "Replaying stored decisions project={} decided={} pairs={}", projectId, decided.size(), stored.size());
        learn(stored, true);
    }

    private void learn(final List<Pair> pairs, final boolean everyTerm) {
        final Result<List<LexiconEntry>> terms = lexicon.all(projectId);
        final Result<List<GlossaryEntry>> names = glossary.all(projectId);
        if (terms.data() == null || names.data() == null) {
            log.warn("Term learning skipped project={}: the lexicon or the glossary could not be read", projectId);
            return;
        }
        final Set<String> locked = names.data().stream()
                .map(entry -> LexiconEntry.keyOf(entry.term()))
                .collect(Collectors.toSet());
        final List<LexiconEntry> tracked = terms.data().stream()
                .filter(entry -> !locked.contains(LexiconEntry.keyOf(entry.term())))
                .toList();
        tracked.forEach(entry -> learner.track(entry.term()));
        pairs.forEach(pair -> learner.observe(pair.source(), pair.target()));
        final Set<String> excluded = wordsOf(names.data());
        final long changed = tracked.stream()
                .filter(entry -> everyTerm || named(entry.term(), pairs))
                .filter(entry -> publish(entry, excluded))
                .count();
        log.debug("Term learning done project={} tracked={} changed={}", projectId, tracked.size(), changed);
    }

    private static boolean named(final String term, final List<Pair> pairs) {
        return pairs.stream().anyMatch(pair -> TermMatch.occursIn(term, pair.source()));
    }

    private boolean publish(final LexiconEntry entry, final Set<String> excluded) {
        final LexiconEntry.Learned found = learner.established(entry.term(), excluded)
                .map(learned -> new LexiconEntry.Learned(learned.rendering(), learned.support(), learned.occurrences()))
                .orElse(null);
        if (Objects.equals(entry.learned(), found)) {
            return false;
        }
        final Result<Optional<LexiconEntry>> stored =
                lexicon.update(projectId, entry.term(), current -> current.withLearned(found));
        if (stored.isErr()) {
            log.warn("A learned rendering could not be stored project={}", projectId);
            return false;
        }
        log.debug(
                "Learned rendering {} project={} support={}",
                found == null ? "withdrawn" : "set",
                projectId,
                found == null ? 0 : found.support());
        log.trace("Learned rendering of {} is {}", entry.term(), found);
        return true;
    }

    private static Set<String> wordsOf(final List<GlossaryEntry> entries) {
        return entries.stream()
                .map(GlossaryEntry::target)
                .filter(Objects::nonNull)
                .flatMap(target -> NON_LETTERS.splitAsStream(target.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }
}
