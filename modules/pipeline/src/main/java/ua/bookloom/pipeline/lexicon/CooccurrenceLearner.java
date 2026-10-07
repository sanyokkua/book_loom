package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Learns which target word keeps company with a key term, from decided segment pairs and with no model call. A word's
 * stem is its first {@value TargetWords#STEM_LENGTH} letters, which merges its inflections without any language rule. For a term
 * the association of a stem {@code w} is the Dice coefficient {@code 2·c(term,w) / (n(term) + c(w))}: the segments
 * naming the term that carry the stem, over the segments naming the term plus the segments carrying the stem. A word
 * that is everywhere ({@code і}, {@code він}) scores low because {@code c(w)} is large; pointwise mutual information
 * and log-likelihood were measured on a real book and were no better.
 *
 * <p>A rendering is only established when the evidence is plain: enough segments, a high score and a score at least
 * twice that of any rival stem. A title that is followed by changing surnames, a word with two meanings and a term the
 * book renders several ways therefore stay unestablished — saying nothing is the safe answer, because an established
 * rendering is shown to the model on every later call.
 *
 * <p>A rendering that was established stays while its score is at least {@value #KEEP_DICE} and no unrelated word
 * has clearly overtaken it ({@link #retained}): a term the model renders two ways would otherwise lose its rendering
 * the moment the second way is used three times, and the book would be left with a hint that came and went. A glossary
 * name whose target is empty is tracked with {@link #trackName}; its candidates are the capitalised target words, and
 * the spellings that share a stem ({@code Бартімей}, {@code Бартимей}) count as one.
 *
 * <p>Only vocabulary counts are kept, so memory grows with the book's vocabulary, never with its length. Not
 * thread-safe: used from the job thread only, and deterministic for a given sequence of calls.
 */
@Slf4j
public final class CooccurrenceLearner {

    /** A common word must be written in lower case in at least this share of the segments that name it. */
    static final double MIN_LOWER_SHARE = 0.2;

    private static final int MIN_SUPPORT = 3;
    private static final double MIN_DICE = 0.6;

    /** An established rendering is kept while its association with the term stays at least this high... */
    static final double KEEP_DICE = 0.4;

    /**
     * A name's spelling must be written mostly where the name is: a title that goes before many surnames ({@code Міс})
     * keeps company with each of them but is written in segments of their own too.
     */
    private static final double MIN_NAME_PRECISION = 0.75;

    /** ...and at least this share of the best unrelated rival's, so a word that overtook it replaces it. */
    private static final double KEEP_SHARE = 0.75;

    private static final double RIVAL_MARGIN = 2.0;

    /**
     * A rendering the evidence supports.
     *
     * @param rendering the base form: the most used surface form of the winning stem, the shortest on a tie
     * @param support how many decided segments naming the term carry the stem
     * @param occurrences how many decided segments name the term
     * @param dice the association score of the stem with the term
     */
    public record Learned(String rendering, int support, int occurrences, double dice) {}

    /** What is counted for one tracked term. */
    private static final class Tracked {
        private final String term;
        private final boolean commonWord;
        private final boolean name;
        private final boolean capitals;
        private final Pattern lowerCaseForm;
        private int occurrences;
        private int lowerCaseSegments;
        private final Map<String, Integer> withStem = new HashMap<>();

        Tracked(final String term, final boolean commonWord, final boolean name, final boolean title) {
            this.term = term;
            this.commonWord = commonWord;
            this.name = name;
            this.capitals = name || title;
            this.lowerCaseForm = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term.toLowerCase(Locale.ROOT))
                    + "(?:['’ʼ]?s|es)?(?![\\p{L}\\p{N}])");
        }
    }

    /** A stem's support under a term and its score. */
    private record Scored(String stem, int support, double dice) {}

    private final Vocabulary words = new Vocabulary();
    private final Vocabulary names = new Vocabulary();
    private final Map<String, Tracked> tracked = new LinkedHashMap<>();
    private final List<String> obliqueEndings;

    /** A learner with no language data: every surface form of a stem is as good a dictionary form as another. */
    public CooccurrenceLearner() {
        this(List.of());
    }

    /**
     * A learner that prefers, among the forms of a stem, one that does not end like an oblique case form.
     *
     * @param obliqueEndings the non-null endings of the target language's oblique forms; empty for no preference
     */
    public CooccurrenceLearner(final List<String> obliqueEndings) {
        this.obliqueEndings = List.copyOf(Objects.requireNonNull(obliqueEndings, "obliqueEndings"));
    }

    /**
     * The key a rendering is claimed under: the stem of its first word.
     *
     * @param rendering the non-null rendering
     * @return its lower-cased stem; empty when it holds no word
     */
    public static String stemOf(final String rendering) {
        return TargetWords.wordsOf(Objects.requireNonNull(rendering, "rendering")).stream()
                .findFirst()
                .map(TargetWords.Word::stem)
                .orElse("");
    }

    /**
     * Starts counting a term; segments observed earlier are not counted for it, so a term tracked late learns from
     * then on. Tracking a term twice changes nothing.
     *
     * @param term the non-blank source term
     */
    public void track(final String term) {
        track(term, false);
    }

    /**
     * Starts counting a term, optionally holding it to be a common word.
     *
     * @param term the non-blank source term
     * @param commonWord {@code true} for a term that is no title: if the book writes it in lower case in under a fifth
     *     of the segments that name it, it is a part of a title or a name ({@code The Times}) and learns nothing
     */
    public void track(final String term, final boolean commonWord) {
        Objects.requireNonNull(term, "term");
        trackAs(term, new Tracked(term, commonWord, false, false));
    }

    /**
     * Starts counting a title ({@code Mr}, {@code Ms}), whose rendering a model writes with a capital in mid-sentence
     * ({@code Міс}) as often as without, so the capitalised words are candidates for it as they are for a name.
     *
     * @param title the non-blank source title
     */
    public void trackTitle(final String title) {
        Objects.requireNonNull(title, "title");
        trackAs(title, new Tracked(title, false, false, true));
    }

    /**
     * Starts counting a glossary name whose target is empty: the rendering it learns is the capitalised target word
     * that keeps company with it.
     *
     * @param name the non-blank source name
     */
    public void trackName(final String name) {
        Objects.requireNonNull(name, "name");
        trackAs(name, new Tracked(name, false, true, false));
    }

    private void trackAs(final String term, final Tracked counts) {
        if (tracked.putIfAbsent(LexiconEntry.keyOf(term), counts) == null) {
            log.debug("Learner tracks a term; {} terms tracked", tracked.size());
            log.trace("Learner tracks term {}", term);
        }
    }

    /**
     * Counts one decided pair: the stems of the target words, and for each tracked term the source names, the same
     * stems under that term.
     *
     * @param source the non-null source text, placeholder tokens removed
     * @param target the non-null target text, placeholder tokens removed
     */
    public void observe(final String source, final String target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        final Set<String> stems = words.count(TargetWords.wordsOf(target));
        names.count(TargetWords.namesAwayFromSentenceStart(target));
        final Set<String> nameStems = TargetWords.stemsOf(TargetWords.namesOf(target));
        tracked.values().forEach(counts -> {
            if (namesTerm(counts, source)) {
                counts.occurrences++;
                if (counts.lowerCaseForm.matcher(source).find()) {
                    counts.lowerCaseSegments++;
                }
                candidates(counts, stems, nameStems).forEach(stem -> counts.withStem.merge(stem, 1, Integer::sum));
            }
        });
        log.trace("Learner observed {} stems, {} terms tracked", stems.size() + nameStems.size(), tracked.size());
    }

    private static Set<String> candidates(final Tracked counts, final Set<String> stems, final Set<String> nameStems) {
        if (counts.name) {
            return nameStems;
        }
        if (!counts.capitals) {
            return stems;
        }
        final Set<String> both = new LinkedHashSet<>(stems);
        both.addAll(nameStems);
        return both;
    }

    // Mr matches Mrs through the plural ending, but a match another tracked term owns (Mrs) is that term's, so the
    // two titles never count the same segment.
    private boolean namesTerm(final Tracked counts, final String source) {
        final String own = LexiconEntry.keyOf(counts.term);
        return TermMatch.matches(counts.term, source).stream()
                .map(LexiconEntry::keyOf)
                .anyMatch(match -> match.equals(own) || !tracked.containsKey(match));
    }

    /**
     * The rendering the decided pairs establish for a term, or nothing when they do not.
     *
     * @param term the source term; need not be tracked
     * @param excludedWords words that cannot be a rendering, such as the glossary's names; compared by stem
     * @return the learned rendering, or empty when the term is not tracked or the gate is not passed
     */
    public Optional<Learned> established(final String term, final Collection<String> excludedWords) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(excludedWords, "excludedWords");
        final Tracked counts = tracked.get(LexiconEntry.keyOf(term));
        if (counts == null || counts.occurrences < MIN_SUPPORT) {
            return Optional.empty();
        }
        if (counts.commonWord && counts.lowerCaseSegments < MIN_LOWER_SHARE * counts.occurrences) {
            log.debug(
                    "Learner skips a title-case term: lower-case in {} of {} segments",
                    counts.lowerCaseSegments,
                    counts.occurrences);
            return Optional.empty();
        }
        final List<Scored> ranked = rank(counts, excludedStems(excludedWords));
        final Optional<Learned> found = gate(counts, ranked);
        log.debug(
                "Learner term check: occurrences={} candidates={} established={}",
                counts.occurrences,
                ranked.size(),
                found.isPresent());
        return found;
    }

    /**
     * Keeps an established rendering while the decided pairs still support it, so a term the model renders two ways
     * keeps the rendering it was shown first.
     *
     * @param term the source term; need not be tracked
     * @param rendering the rendering established earlier
     * @param excludedWords words that cannot be a rendering, compared by stem
     * @return the rendering with its present support, or empty when the term is not tracked or the evidence fell
     */
    public Optional<Learned> retained(
            final String term, final String rendering, final Collection<String> excludedWords) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(excludedWords, "excludedWords");
        final Tracked counts = tracked.get(LexiconEntry.keyOf(term));
        final String stem = stemOf(rendering);
        if (counts == null || excludedStems(excludedWords).contains(stem)) {
            return Optional.empty();
        }
        final int support = counts.withStem.getOrDefault(stem, 0);
        final double dice = dice(counts, stem, support);
        final double rival = rank(counts, excludedStems(excludedWords)).stream()
                .filter(other -> !related(stem, other.stem()))
                .mapToDouble(Scored::dice)
                .max()
                .orElse(0);
        final boolean kept = support >= MIN_SUPPORT && dice >= KEEP_DICE && dice >= KEEP_SHARE * rival;
        log.debug("Learner retention check: support={} dice={} bestRival={} kept={}", support, dice, rival, kept);
        return kept
                ? Optional.of(new Learned(baseForm(counts, stem), support, counts.occurrences, dice))
                : Optional.empty();
    }

    private Set<String> excludedStems(final Collection<String> excludedWords) {
        final Set<String> excluded = new HashSet<>();
        excludedWords.forEach(word -> {
            TargetWords.wordsOf(word).forEach(found -> excluded.add(found.stem()));
            TargetWords.namesOf(word).forEach(found -> excluded.add(found.stem()));
        });
        return excluded;
    }

    private boolean isPrecise(final Tracked counts, final String stem, final int support) {
        return !counts.name || support >= MIN_NAME_PRECISION * carrying(stem, true, support);
    }

    // A name's competitors are the segments that carry the stem in any case: a sentence-start word is counted with the
    // words, a name away from a sentence start with the names, so a common word at a sentence start (Він) stays common.
    private double dice(final Tracked counts, final String stem, final int support) {
        return 2.0 * support / (counts.occurrences + carrying(stem, counts.capitals, support));
    }

    private int carrying(final String stem, final boolean capitals, final int support) {
        final int found = words.segmentsWith(stem) + (capitals ? names.segmentsWith(stem) : 0);
        return Math.max(support, found);
    }

    private List<Scored> rank(final Tracked counts, final Set<String> excluded) {
        final List<Scored> ranked = new ArrayList<>();
        counts.withStem.forEach((stem, support) -> {
            if (support >= MIN_SUPPORT && !excluded.contains(stem) && isPrecise(counts, stem, support)) {
                ranked.add(new Scored(stem, support, dice(counts, stem, support)));
            }
        });
        ranked.sort(Comparator.comparingDouble(Scored::dice)
                .reversed()
                .thenComparing(Comparator.comparingInt(Scored::support).reversed())
                .thenComparing(Scored::stem));
        return ranked;
    }

    // The rivals are the other stems that are not the winner's own inflection (one a prefix of the other).
    private Optional<Learned> gate(final Tracked counts, final List<Scored> ranked) {
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        final Scored winner = ranked.getFirst();
        final double rival = ranked.stream()
                .skip(1)
                .filter(other -> !related(winner.stem(), other.stem()))
                .mapToDouble(Scored::dice)
                .max()
                .orElse(0);
        if (winner.dice() < MIN_DICE || winner.dice() < RIVAL_MARGIN * rival) {
            log.debug("Learner gate not passed: dice={} bestRival={}", winner.dice(), rival);
            return Optional.empty();
        }
        return Optional.of(
                new Learned(baseForm(counts, winner.stem()), winner.support(), counts.occurrences, winner.dice()));
    }

    private static boolean related(final String one, final String other) {
        return one.startsWith(other) || other.startsWith(one);
    }

    // With language data, a form that ends like an oblique case form (землі) loses to one that does not (земля).
    // The base form is the surface form the most uses of the stem extend ({@code господар} for {@code господаря},
    // {@code господарю}), so a book that mostly meets the term in an oblique case still gets its dictionary form; where
    // no form is a prefix of the others it is the most used, and the shortest on a tie.
    private String baseForm(final Tracked counts, final String stem) {
        final Map<String, Integer> forms = new HashMap<>(words.formsOf(stem));
        if (counts.capitals) {
            names.formsOf(stem).forEach((form, uses) -> forms.merge(form, uses, Integer::sum));
        }
        final String base = baseOf(stem, forms);
        return counts.name ? capitalised(base) : base;
    }

    private static String capitalised(final String word) {
        return word.isEmpty()
                ? word
                : word.substring(0, word.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT)
                        + word.substring(word.offsetByCodePoints(0, 1));
    }

    private String baseOf(final String stem, final Map<String, Integer> forms) {
        return forms.keySet().stream()
                .max(Comparator.comparingInt((String form) -> isOblique(form) ? 0 : 1)
                        .thenComparingInt(form -> extended(form, forms))
                        .thenComparingInt(form -> forms.get(form))
                        .thenComparing(Comparator.comparingInt(String::length).reversed())
                        .thenComparing(Comparator.<String>naturalOrder().reversed()))
                .orElse(stem);
    }

    private boolean isOblique(final String form) {
        return obliqueEndings.stream().anyMatch(form::endsWith);
    }

    private static int extended(final String form, final Map<String, Integer> forms) {
        return forms.entrySet().stream()
                .filter(other -> other.getKey().startsWith(form))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }
}
