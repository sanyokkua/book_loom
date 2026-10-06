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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Learns which target word keeps company with a key term, from decided segment pairs and with no model call. A word's
 * stem is its first {@value #STEM_LENGTH} letters, which merges its inflections without any language rule. For a term
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
 * <p>Only vocabulary counts are kept, so memory grows with the book's vocabulary, never with its length. Not
 * thread-safe: used from the job thread only, and deterministic for a given sequence of calls.
 */
@Slf4j
public final class CooccurrenceLearner {

    /** How many leading letters of a lower-cased word are its stem. */
    static final int STEM_LENGTH = 4;

    /** A common word must be written in lower case in at least this share of the segments that name it. */
    static final double MIN_LOWER_SHARE = 0.2;

    private static final int MIN_LETTERS = 3;
    private static final int MIN_SUPPORT = 3;
    private static final double MIN_DICE = 0.6;
    private static final double RIVAL_MARGIN = 2.0;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");
    private static final Pattern GAP_END = Pattern.compile("[\\s\"“”«»'‘’—–-]+$");
    private static final String SENTENCE_ENDS = ".!?…:";

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
        private final Pattern lowerCaseForm;
        private int occurrences;
        private int lowerCaseSegments;
        private final Map<String, Integer> withStem = new HashMap<>();

        Tracked(final String term, final boolean commonWord) {
            this.term = term;
            this.commonWord = commonWord;
            this.lowerCaseForm = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term.toLowerCase(Locale.ROOT))
                    + "(?:['’ʼ]?s|es)?(?![\\p{L}\\p{N}])");
        }
    }

    /** A stem's support under a term and its score. */
    private record Scored(String stem, int support, double dice) {}

    /** A target word in a segment: its lower-cased surface form and the stem it counts under. */
    private record Word(String surface, String stem) {}

    private final Map<String, Integer> segmentsWithStem = new HashMap<>();
    private final Map<String, Map<String, Integer>> surfaceForms = new HashMap<>();
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
        return wordsOf(Objects.requireNonNull(rendering, "rendering")).stream()
                .findFirst()
                .map(Word::stem)
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
        final String key = LexiconEntry.keyOf(term);
        if (tracked.putIfAbsent(key, new Tracked(term, commonWord)) == null) {
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
        final List<Word> words = wordsOf(target);
        final Set<String> stems = new LinkedHashSet<>();
        for (final Word word : words) {
            surfaceForms.computeIfAbsent(word.stem(), key -> new HashMap<>()).merge(word.surface(), 1, Integer::sum);
            stems.add(word.stem());
        }
        stems.forEach(stem -> segmentsWithStem.merge(stem, 1, Integer::sum));
        tracked.values().forEach(counts -> {
            if (namesTerm(counts, source)) {
                counts.occurrences++;
                if (counts.lowerCaseForm.matcher(source).find()) {
                    counts.lowerCaseSegments++;
                }
                stems.forEach(stem -> counts.withStem.merge(stem, 1, Integer::sum));
            }
        });
        log.trace("Learner observed {} stems, {} terms tracked", stems.size(), tracked.size());
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
        final Set<String> excluded = new HashSet<>();
        excludedWords.forEach(word -> wordsOf(word).forEach(found -> excluded.add(found.stem())));
        final List<Scored> ranked = rank(counts, excluded);
        final Optional<Learned> found = gate(counts, ranked);
        log.debug(
                "Learner term check: occurrences={} candidates={} established={}",
                counts.occurrences,
                ranked.size(),
                found.isPresent());
        return found;
    }

    private List<Scored> rank(final Tracked counts, final Set<String> excluded) {
        final List<Scored> ranked = new ArrayList<>();
        counts.withStem.forEach((stem, support) -> {
            if (support >= MIN_SUPPORT && !excluded.contains(stem)) {
                final double dice = 2.0 * support / (counts.occurrences + segmentsWithStem.getOrDefault(stem, support));
                ranked.add(new Scored(stem, support, dice));
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
        return Optional.of(new Learned(baseForm(winner.stem()), winner.support(), counts.occurrences, winner.dice()));
    }

    private static boolean related(final String one, final String other) {
        return one.startsWith(other) || other.startsWith(one);
    }

    // With language data, a form that ends like an oblique case form (землі) loses to one that does not (земля).
    // The base form is the surface form the most uses of the stem extend ({@code господар} for {@code господаря},
    // {@code господарю}), so a book that mostly meets the term in an oblique case still gets its dictionary form; where
    // no form is a prefix of the others it is the most used, and the shortest on a tie.
    private String baseForm(final String stem) {
        final Map<String, Integer> forms = surfaceForms.getOrDefault(stem, Map.of());
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

    // A word is a candidate unless it is capitalised away from a sentence start, which is how a name looks.
    private static List<Word> wordsOf(final String text) {
        final List<Word> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text);
        int previousEnd = 0;
        boolean first = true;
        while (matcher.find()) {
            final String gap = GAP_END.matcher(text.substring(previousEnd, matcher.start()))
                    .replaceFirst("");
            final boolean sentenceStart =
                    gap.isEmpty() ? first : SENTENCE_ENDS.indexOf(gap.charAt(gap.length() - 1)) >= 0;
            previousEnd = matcher.end();
            first = false;
            final String word = matcher.group();
            final boolean name = !sentenceStart && Character.isUpperCase(word.codePointAt(0));
            final String surface = word.toLowerCase(Locale.ROOT);
            if (!name && surface.length() >= MIN_LETTERS) {
                words.add(new Word(surface, surface.substring(0, Math.min(STEM_LENGTH, surface.length()))));
            }
        }
        return words;
    }
}
