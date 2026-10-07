package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.glossary.StopWords;
import ua.bookloom.pipeline.lexicon.CooccurrenceLearner;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Teaches the lexicon what the run has already decided: the translated pairs of a chunk are counted by the
 * {@link CooccurrenceLearner} once the chunk is stored, and a term whose rendering the counts establish gets it as its
 * learned rendering. A pair is counted only after its record is committed, so a stop never leaves a count for a
 * decision that was not kept; a run that continues over stored decisions replays them first, which gives the same
 * counts. A term the glossary holds is never learned — the person's lock decides it — and neither is a word the
 * glossary holds as a name or a function word of the target language ({@code коли}). The lexicon is a hint, so a store that cannot be read or written is logged and skipped.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class TermLearning {

    private static final int MIN_FAMILY_LETTERS = 4;
    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{M}'’ʼ-]+");

    /** A decided pair, tokens removed. */
    record Pair(String source, String target) {}

    private final CooccurrenceLearner learner;
    private final @Nullable String sourceLanguage;
    private final SegmentRepository segments;
    private final LexiconRepository lexicon;
    private final GlossaryRepository glossary;
    private final String projectId;
    private final Set<String> functionWords;
    private final List<Pair> held = new ArrayList<>();

    TermLearning(final RunStores stores, final String projectId) {
        this(stores, projectId, null, null);
    }

    /**
     * Learns with the languages' data: the target's oblique endings choose the dictionary form, and the source's
     * title list tells a title, which a book may write with a capital only, from a common word.
     */
    TermLearning(
            final RunStores stores,
            final String projectId,
            final @Nullable String sourceLanguage,
            final @Nullable String targetLanguage) {
        Objects.requireNonNull(stores, "stores");
        this.sourceLanguage = sourceLanguage;
        this.learner = new CooccurrenceLearner(
                targetLanguage == null ? List.of() : LanguageRules.bundled().obliqueEndings(targetLanguage));
        this.segments = stores.segments();
        this.lexicon = stores.lexicon();
        this.glossary = stores.glossary();
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.functionWords = StopWords.bundled(targetLanguage).orElse(Set.of());
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
        tracked.forEach(this::track);
        final List<GlossaryEntry> unnamed =
                names.data().stream().filter(TermLearning::lacksSpelling).toList();
        unnamed.forEach(entry -> learner.trackName(entry.term()));
        pairs.forEach(pair -> learner.observe(pair.source(), pair.target()));
        final Set<String> excluded = new HashSet<>(wordsOf(names.data()));
        excluded.addAll(functionWords);
        final List<LexiconEntry> affected = tracked.stream()
                .filter(entry -> everyTerm || named(entry.term(), pairs))
                .toList();
        final Map<String, LexiconEntry.Learned> decided = unclaimed(terms.data(), affected, excluded);
        final long changed = affected.stream()
                .filter(entry -> publish(entry, decided.get(entry.term())))
                .count();
        log.debug("Term learning done project={} tracked={} changed={}", projectId, tracked.size(), changed);
        learnSpellings(unnamed, withRenderings(excluded, terms.data(), decided), everyTerm ? null : pairs);
    }

    private void track(final LexiconEntry entry) {
        if (KeyTermScan.isTitle(entry.term(), sourceLanguage)) {
            learner.trackTitle(entry.term());
        } else {
            learner.track(entry.term(), true);
        }
    }

    // A word that renders a recurring term (a title written before every surname: Міс) is never a name's spelling.
    private static Set<String> withRenderings(
            final Set<String> excluded,
            final List<LexiconEntry> terms,
            final Map<String, LexiconEntry.Learned> learned) {
        final Set<String> all = new HashSet<>(excluded);
        terms.forEach(entry -> entry.established().ifPresent(all::add));
        learned.values().forEach(found -> all.add(found.text()));
        return all;
    }

    // A glossary name with no target gets, once the book has used one spelling for it, that spelling as a suggestion
    // the person can change; the first spelling wins, and a target already there — the person's or the model's — is
    // never replaced.
    private void learnSpellings(
            final List<GlossaryEntry> unnamed, final Set<String> excluded, final @Nullable List<Pair> pairs) {
        for (final GlossaryEntry entry : unnamed) {
            if (pairs == null || named(entry.term(), pairs)) {
                learner.established(entry.term(), excluded).ifPresent(found -> suggest(entry, found));
            }
        }
    }

    private void suggest(final GlossaryEntry entry, final CooccurrenceLearner.Learned found) {
        final Optional<GlossaryEntry> current =
                glossary.findByTerm(projectId, entry.term()).data();
        if (current == null || current.isEmpty() || !lacksSpelling(current.get())) {
            return;
        }
        if (glossary.update(current.get().withSuggestedTarget(found.rendering()))
                .isErr()) {
            log.warn("A learned spelling could not be stored project={}", projectId);
            return;
        }
        log.debug("Learned spelling of a name set project={} support={}", projectId, found.support());
        log.trace("Learned spelling of {} is {}", entry.term(), found.rendering());
    }

    private static boolean lacksSpelling(final GlossaryEntry entry) {
        final boolean oneWord = entry.term().strip().indexOf(' ') < 0;
        final boolean aName = entry.type() == TermType.CHARACTER || entry.type() == TermType.PLACE;
        return aName
                && oneWord
                && !entry.locked()
                && (entry.target() == null || entry.target().isBlank());
    }

    // A rendering stem belongs to one term: the person's choices and the renderings already learned or most used for
    // the other terms come first, then the new findings by strength; a finding on a stem another term holds is
    // dropped (mr and mrs are two titles, so they never share пані), unless the two are one word family.
    private Map<String, LexiconEntry.Learned> unclaimed(
            final List<LexiconEntry> all, final List<LexiconEntry> affected, final Set<String> excluded) {
        final Set<String> affectedTerms =
                affected.stream().map(LexiconEntry::term).collect(Collectors.toSet());
        final Map<String, String> claims = new HashMap<>();
        all.stream().filter(entry -> !affectedTerms.contains(entry.term())).forEach(entry -> claim(claims, entry));
        final Map<String, CooccurrenceLearner.Learned> found = new LinkedHashMap<>();
        affected.forEach(entry -> learner.established(entry.term(), excluded)
                .or(() -> retained(entry, excluded))
                .ifPresent(learned -> found.put(entry.term(), learned)));
        final Map<String, LexiconEntry.Learned> kept = new HashMap<>();
        found.entrySet().stream()
                .sorted(Map.Entry.<String, CooccurrenceLearner.Learned>comparingByValue(
                                Comparator.comparingDouble(CooccurrenceLearner.Learned::dice)
                                        .reversed())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> keepIfUnclaimed(entry.getKey(), entry.getValue(), claims, kept));
        return kept;
    }

    // A rendering that was established stays while the pairs still support it: the first decision wins, so a book
    // whose model keeps using a second word is not left with a hint that comes and goes.
    private Optional<CooccurrenceLearner.Learned> retained(final LexiconEntry entry, final Set<String> excluded) {
        return Optional.ofNullable(entry.learned())
                .flatMap(held -> learner.retained(entry.term(), held.text(), excluded));
    }

    private void keepIfUnclaimed(
            final String term,
            final CooccurrenceLearner.Learned learned,
            final Map<String, String> claims,
            final Map<String, LexiconEntry.Learned> kept) {
        final String stem = CooccurrenceLearner.stemOf(learned.rendering());
        final String owner = claims.get(stem);
        if (owner != null && !isSameFamily(owner, term)) {
            log.debug("Learned rendering dropped: its stem is held by another term project={}", projectId);
            log.trace("Term {} lost the stem {} to {}", term, stem, owner);
            return;
        }
        claims.putIfAbsent(stem, term);
        kept.put(term, new LexiconEntry.Learned(learned.rendering(), learned.support(), learned.occurrences()));
    }

    private static void claim(final Map<String, String> claims, final LexiconEntry entry) {
        entry.established().map(CooccurrenceLearner::stemOf).ifPresent(stem -> claims.putIfAbsent(stem, entry.term()));
    }

    // Terms that differ only by an English plural or possessive are one word; an abbreviation (mr, mrs) is too short
    // to carry an inflection, so it is a word of its own.
    private static boolean isSameFamily(final String one, final String other) {
        return base(one).equals(base(other));
    }

    private static String base(final String term) {
        final String key = LexiconEntry.keyOf(term);
        final String stripped = key.replaceFirst("(?:'s|es|s)$", "");
        return stripped.codePointCount(0, stripped.length()) >= MIN_FAMILY_LETTERS ? stripped : key;
    }

    private static boolean named(final String term, final List<Pair> pairs) {
        return pairs.stream().anyMatch(pair -> TermMatch.occursIn(term, pair.source()));
    }

    private boolean publish(final LexiconEntry entry, final LexiconEntry.@Nullable Learned found) {
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
