package ua.bookloom.pipeline.lexicon;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.glossary.NameCandidate;

/**
 * Local-only investigation: can a key term's rendering be learned from already translated segment pairs by
 * co-occurrence, with no model? Run {@code BOOKLOOM_LEX_SOURCE=<source> BOOKLOOM_LEX_EXPORT=<exported>
 * [BOOKLOOM_LEX_FROM=en] ./gradlew :pipeline:corpus --tests '*LexiconCooccurrenceTool*' -i} and read the {@code LEX}
 * lines: words (stems) and counts only, never a sentence. Tagged {@code corpus}, so the gate never runs it, and does
 * nothing without the variables.
 */
@Tag("corpus")
class LexiconCooccurrenceTool {

    private static final String SOURCE = "BOOKLOOM_LEX_SOURCE";
    private static final String EXPORT = "BOOKLOOM_LEX_EXPORT";
    private static final List<String> FIXED = List.of(
            "master",
            "imp",
            "spirit",
            "djinni",
            "pentacle",
            "Mr",
            "Mrs",
            "magician",
            "minister",
            "staff",
            "circle",
            "demon",
            "sir",
            "boy");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");
    private static final int MIN_SUPPORT = 3;
    private static final double MIN_SHARE = 0.6;
    private static final int MIN_LETTERS = 3;
    private static final int TOP = 3;
    private static final double RIVAL_FLOOR = 0.5;

    /** The three association measures compared. */
    enum Measure {
        DICE,
        LLR,
        PMI;

        double score(final Scores scores) {
            return switch (this) {
                case DICE -> scores.dice();
                case LLR -> scores.llr();
                case PMI -> scores.pmi();
            };
        }
    }

    /** The 2x2 table of one candidate: term segments, candidate-holding segments, both, all. */
    record Scores(double nTerm, double cBoth, double cAll, double total) {

        double dice() {
            return 2 * cBoth / (nTerm + cAll);
        }

        double pmi() {
            return Math.log((cBoth / nTerm) / (cAll / total)) / Math.log(2);
        }

        double llr() {
            if (cBoth / nTerm <= cAll / total) {
                return 0;
            }
            final double k12 = nTerm - cBoth;
            final double k21 = cAll - cBoth;
            final double k22 = total - nTerm - k21;
            return 2
                    * (term(cBoth, nTerm, cAll)
                            + term(k12, nTerm, total - cAll)
                            + term(k21, total - nTerm, cAll)
                            + term(k22, total - nTerm, total - cAll));
        }

        private double term(final double k, final double row, final double column) {
            return k <= 0 ? 0 : k * Math.log(k * total / (row * column));
        }
    }

    /** One aligned pair in book order, with its candidate set already cut. */
    record Pair(String source, Set<String> candidates) {}

    /** A ranked candidate. */
    record Ranked(String candidate, int support, int all, double score) {}

    @Test
    void investigate_translatedBook_printsTheCooccurrenceEvidence() {
        final String source = System.getenv(SOURCE);
        final String exported = System.getenv(EXPORT);
        assumeTrue(source != null && exported != null, "set BOOKLOOM_LEX_SOURCE and BOOKLOOM_LEX_EXPORT");
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Document original = Objects.requireNonNull(documents
                .open(java.nio.file.Path.of(Objects.requireNonNull(source)))
                .data());
        final Document written = Objects.requireNonNull(documents
                .open(java.nio.file.Path.of(Objects.requireNonNull(exported)))
                .data());
        final List<Pair> pairs = pairs(original, written);
        final List<String> terms = terms(original);
        System.out.println("LEX pairs=" + pairs.size() + " terms=" + terms);
        for (final String term : terms) {
            report(term, pairs);
        }
        for (final Measure measure : Measure.values()) {
            simulate(measure, terms, pairs);
        }
    }

    private static List<String> terms(final Document original) {
        final List<Segment> segments = original.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
        final Set<String> terms = new LinkedHashSet<>();
        KeyTermScan.candidates(segments, System.getenv().getOrDefault("BOOKLOOM_LEX_FROM", "en")).stream()
                .map(NameCandidate::term)
                .forEach(terms::add);
        final Set<String> lower = new HashSet<>(terms);
        FIXED.stream()
                .filter(term -> !lower.contains(term.toLowerCase(Locale.ROOT)))
                .forEach(terms::add);
        return List.copyOf(terms);
    }

    private static List<Pair> pairs(final Document original, final Document written) {
        final Map<String, Segment> writtenById = new HashMap<>();
        written.units().forEach(unit -> unit.segments().forEach(segment -> writtenById.put(segment.id(), segment)));
        final List<Pair> pairs = new ArrayList<>();
        for (final var unit : original.units()) {
            for (final Segment segment : unit.segments()) {
                final Segment target = writtenById.get(segment.id());
                final String text = target == null ? "" : DisplayText.of(target.masked());
                if (!text.isBlank()) {
                    pairs.add(new Pair(DisplayText.of(segment.masked()), candidates(text)));
                }
            }
        }
        return pairs;
    }

    // Stems follow TermMappingVerifier's prefix rule unless BOOKLOOM_LEX_PREFIX caps them; a candidate is a stem or two
    // adjacent stems. With BOOKLOOM_LEX_NONAMES=1 a capitalised word away from a sentence start (a name) is no
    // candidate.
    private static Set<String> candidates(final String text) {
        final List<String> stems = new ArrayList<>();
        final boolean skipNames = "1".equals(System.getenv("BOOKLOOM_LEX_NONAMES"));
        WORD.matcher(text).results().forEach(match -> {
            final boolean name = skipNames && isMidSentenceCapital(text, match.start());
            stems.add(name ? "" : stem(match.group().toLowerCase(Locale.ROOT)));
        });
        final Set<String> candidates = new HashSet<>();
        for (int i = 0; i < stems.size(); i++) {
            if (stems.get(i).length() >= MIN_LETTERS) {
                candidates.add(stems.get(i));
            }
            if (i + 1 < stems.size()
                    && !stems.get(i).isEmpty()
                    && !stems.get(i + 1).isEmpty()) {
                candidates.add(stems.get(i) + " " + stems.get(i + 1));
            }
        }
        return candidates;
    }

    private static boolean isMidSentenceCapital(final String text, final int start) {
        if (!Character.isUpperCase(text.codePointAt(start))) {
            return false;
        }
        final String before = text.substring(0, start).replaceAll("[\\s\"“”«»'‘’—–-]+$", "");
        return !before.isEmpty() && ".!?…:".indexOf(before.charAt(before.length() - 1)) < 0;
    }

    private static String stem(final String word) {
        final int cap = Integer.parseInt(System.getenv().getOrDefault("BOOKLOOM_LEX_PREFIX", "0"));
        final int length = word.length();
        final int cut = length >= 8 ? 3 : length >= 6 ? 2 : length >= 4 ? 1 : 0;
        final int rule = Math.max(Math.min(3, length), length - cut);
        return word.substring(0, cap > 0 ? Math.min(cap, rule) : rule);
    }

    private static void report(final String term, final List<Pair> pairs) {
        final Map<String, Integer> all = new HashMap<>();
        final Map<String, Integer> inTerm = new HashMap<>();
        int occurrences = 0;
        for (final Pair pair : pairs) {
            final boolean has = TermMatch.occursIn(term, pair.source());
            occurrences += has ? 1 : 0;
            for (final String candidate : pair.candidates()) {
                all.merge(candidate, 1, Integer::sum);
                if (has) {
                    inTerm.merge(candidate, 1, Integer::sum);
                }
            }
        }
        System.out.println("LEX TERM " + term + " occurrences=" + occurrences);
        for (final Measure measure : Measure.values()) {
            final List<Ranked> top = rank(measure, inTerm, all, occurrences, pairs.size());
            System.out.println("LEX   " + measure + " "
                    + top.stream().limit(TOP).map(LexiconCooccurrenceTool::show).toList()
                    + share(top, term, pairs));
        }
    }

    private static String show(final Ranked ranked) {
        return String.format(
                Locale.ROOT, "%s(%d/%d %.2f)", ranked.candidate(), ranked.support(), ranked.all(), ranked.score());
    }

    private static List<Ranked> rank(
            final Measure measure,
            final Map<String, Integer> inTerm,
            final Map<String, Integer> all,
            final int occurrences,
            final int total) {
        final List<Ranked> ranked = new ArrayList<>();
        inTerm.forEach((candidate, support) -> {
            if (support >= MIN_SUPPORT) {
                final Scores scores = new Scores(occurrences, support, all.getOrDefault(candidate, support), total);
                ranked.add(new Ranked(candidate, support, all.getOrDefault(candidate, support), measure.score(scores)));
            }
        });
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return ranked;
    }

    // Share = winner's support over the segments holding any of the top candidates; also the first occurrence index at
    // which "support >= 3 and share >= 0.6" held for the final winner.
    private static String share(final List<Ranked> top, final String term, final List<Pair> pairs) {
        if (top.isEmpty()) {
            return " winner=none";
        }
        final List<String> leaders =
                top.stream().limit(TOP).map(Ranked::candidate).toList();
        final String winner = leaders.get(0);
        int seen = 0;
        int won = 0;
        int union = 0;
        int established = -1;
        for (final Pair pair : pairs) {
            if (!TermMatch.occursIn(term, pair.source())) {
                continue;
            }
            seen++;
            won += pair.candidates().contains(winner) ? 1 : 0;
            union += leaders.stream().anyMatch(pair.candidates()::contains) ? 1 : 0;
            if (established < 0 && won >= MIN_SUPPORT && won >= MIN_SHARE * union) {
                established = seen;
            }
        }
        return String.format(
                Locale.ROOT,
                " | winner=%s share(of union)=%.2f share(of occ)=%.2f establishedAtOccurrence=%d",
                winner,
                (double) won / Math.max(union, 1),
                (double) won / Math.max(seen, 1),
                established);
    }

    // Feeds pairs in book order. Before learning from an occurrence, the learner's winner (support >= 3, share >= 0.6
    // among the segments holding a top candidate) is "injected"; drift = the occurrence's own text lacked it.
    private static void simulate(final Measure measure, final List<String> terms, final List<Pair> pairs) {
        final Map<String, Integer> all = new HashMap<>();
        final Map<String, Map<String, Integer>> inTerm = new HashMap<>();
        final Map<String, int[]> tally = new HashMap<>();
        int total = 0;
        for (final Pair pair : pairs) {
            for (final String term : terms) {
                if (!TermMatch.occursIn(term, pair.source())) {
                    continue;
                }
                final Map<String, Integer> held = inTerm.computeIfAbsent(term, key -> new HashMap<>());
                final int[] counts = tally.computeIfAbsent(term, key -> new int[3]);
                counts[0]++;
                final String injected = established(measure, held, all, counts[0] - 1, total);
                if (injected != null) {
                    counts[1]++;
                    counts[2] += pair.candidates().contains(injected) ? 0 : 1;
                }
                pair.candidates().forEach(candidate -> held.merge(candidate, 1, Integer::sum));
            }
            pair.candidates().forEach(candidate -> all.merge(candidate, 1, Integer::sum));
            total++;
        }
        print(measure, tally);
    }

    private static void print(final Measure measure, final Map<String, int[]> tally) {
        int injected = 0;
        int drift = 0;
        for (final int[] counts : tally.values()) {
            injected += counts[1];
            drift += counts[2];
        }

        System.out.println("LEX SIM " + measure + " injected=" + injected + " drift=" + drift);
        tally.forEach((term, counts) -> System.out.println("LEX SIM " + measure + " " + term + " occurrences="
                + counts[0] + " injected=" + counts[1] + " drift=" + counts[2]));
    }

    private static @Nullable String established(
            final Measure measure,
            final Map<String, Integer> held,
            final Map<String, Integer> all,
            final int occurrences,
            final int total) {
        if (occurrences < MIN_SUPPORT) {
            return null;
        }
        final List<Ranked> top = rank(measure, held, all, occurrences, total);
        if (top.isEmpty()) {
            return null;
        }
        final Ranked winner = top.get(0);
        // Rivals are the next candidates that share no word with the winner (a bigram holding it is the same
        // rendering).
        final List<String> winnerWords = List.of(winner.candidate().split(" "));
        final double rivals = winner.support()
                + top.stream()
                        .skip(1)
                        .filter(other -> other.score() >= RIVAL_FLOOR * winner.score())
                        .filter(other ->
                                List.of(other.candidate().split(" ")).stream().noneMatch(winnerWords::contains))
                        .limit(TOP - 1)
                        .mapToInt(Ranked::support)
                        .sum();
        return winner.support() >= MIN_SHARE * rivals ? winner.candidate() : null;
    }
}
