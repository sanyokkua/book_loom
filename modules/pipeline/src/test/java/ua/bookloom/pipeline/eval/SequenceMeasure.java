package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.CallKindTotals;
import ua.bookloom.pipeline.ControlCharacters;
import ua.bookloom.pipeline.RenderingConsistency;
import ua.bookloom.pipeline.batch.ProtocolLeak;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.eval.SequenceFixture.Name;
import ua.bookloom.pipeline.eval.SequenceFixture.Rendering;
import ua.bookloom.pipeline.eval.SequenceFixture.Term;
import ua.bookloom.pipeline.eval.SequenceMetrics.NameStat;
import ua.bookloom.pipeline.eval.SequenceMetrics.TermStat;
import ua.bookloom.pipeline.eval.SequenceRun.Decided;

/**
 * Turns one run into the sequence metrics with production checks only: {@link TextChecks} for the language, quote and
 * script findings, {@link GenderChecks} for the narrator, {@link RenderingConsistency} for the lexicon. A term's
 * renderings are read by co-occurrence: a source segment naming the term counts for every known rendering its target
 * carries, so a target that uses a rendering the fixture does not list counts for none.
 */
final class SequenceMeasure {

    private static final String WORD_START = "(?<![\\p{L}\\p{M}])(?:";
    private static final String WORD_END = ")(?![\\p{L}\\p{M}])";
    private static final String GENDER_CHECK = "uk";
    private static final int MIN_ECHO_LETTERS = 3;

    private final SequenceFixture fixture;

    SequenceMeasure(final SequenceFixture fixture) {
        this.fixture = Objects.requireNonNull(fixture, "fixture");
    }

    SequenceMetrics measure(final String model, final String dial, final SequenceRun run) {
        final List<Decided> segments = run.segments();
        final List<TermStat> terms =
                fixture.terms().stream().map(term -> term(term, segments)).toList();
        final List<NameStat> names =
                fixture.names().stream().map(name -> name(name, segments)).toList();
        final SequenceMetrics.SequenceMetricsBuilder builder = SequenceMetrics.builder()
                .suite(SequenceMetrics.SUITE)
                .model(model)
                .narrator(run.narrator().label())
                .dial(dial)
                .terms(terms)
                .names(names);
        quality(builder, segments, terms, names);
        realRun(builder, run, terms);
        lexicon(builder, run.lexicon());
        cost(builder, run);
        return builder.build();
    }

    private void quality(
            final SequenceMetrics.SequenceMetricsBuilder builder,
            final List<Decided> segments,
            final List<TermStat> terms,
            final List<NameStat> names) {
        builder.segments(segments.size())
                .flagged((int) segments.stream()
                        .filter(s -> s.status() == SegmentStatus.FLAGGED)
                        .count())
                .flaggedWithoutTarget((int) segments.stream()
                        .filter(s -> s.status() == SegmentStatus.FLAGGED && s.target() == null)
                        .count())
                .renderingsPerTerm(mean(terms.stream()
                        .filter(t -> t.distinct() > 0)
                        .mapToInt(TermStat::distinct)
                        .toArray()))
                .nameVariants(names.stream()
                        .mapToInt(n -> Math.max(0, n.variants() - 1))
                        .sum())
                .genderSlips(genderSlips(segments))
                .englishLeftovers(
                        (int) segments.stream().filter(this::isEnglishLeftover).count())
                .quoteFailures(findings(segments, FindingKind.UNBALANCED_QUOTES))
                .asciiQuotes((int) segments.stream()
                        .filter(s -> s.target() != null && s.target().indexOf('"') >= 0)
                        .count())
                .mixedScript(findings(segments, FindingKind.MIXED_SCRIPT))
                .controlCharacters((int) segments.stream()
                        .filter(s -> s.target() != null && ControlCharacters.addsControl(s.source(), s.target()))
                        .count())
                .closerResidue(findings(segments, FindingKind.PROTOCOL_LEAK))
                .vocativeMissing(findings(segments, FindingKind.VOCATIVE_MISSING));
    }

    private static void realRun(
            final SequenceMetrics.SequenceMetricsBuilder builder, final SequenceRun run, final List<TermStat> terms) {
        final Map<String, List<String>> claims = claims(run.lexicon());
        builder.hardGateFailuresRound0(run.hardGateFailuresRound0())
                .hardGateKinds(run.hardGateKinds())
                .leakedProtocol((int) run.segments().stream()
                        .filter(s -> s.target() != null && ProtocolLeak.leaks(s.target()))
                        .count())
                .reviewerTruncated(run.reviewerTruncated())
                .termClaimedWrong(claims.values().stream().mapToInt(List::size).sum())
                .claimedRenderings(claims.entrySet().stream()
                        .map(e -> e.getKey() + " <- " + String.join(", ", e.getValue()))
                        .toList())
                .dominantShareMean(mean(terms.stream()
                        .filter(t -> t.distinct() > 0)
                        .mapToDouble(TermStat::dominantShare)
                        .toArray()));
    }

    // A rendering two terms both learned is wrong for at least one of them (Mr and Mrs both becoming "пані").
    private static Map<String, List<String>> claims(final List<LexiconEntry> entries) {
        final Map<String, List<String>> byRendering = new TreeMap<>();
        for (final LexiconEntry entry : entries) {
            if (entry.learned() != null) {
                byRendering
                        .computeIfAbsent(entry.learned().text().toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                        .add(entry.term());
            }
        }
        byRendering.values().removeIf(terms -> terms.size() < 2);
        return byRendering;
    }

    private static void lexicon(
            final SequenceMetrics.SequenceMetricsBuilder builder, final List<LexiconEntry> entries) {
        final RenderingConsistency lexicon = RenderingConsistency.of(entries);
        builder.learned(lexicon.learned())
                .learnedCoverage(lexicon.learnedCoverage())
                .lexiconDistinct(lexicon.distinctPerTerm())
                .learnedRenderings(entries.stream()
                        .filter(entry -> entry.learned() != null)
                        .map(SequenceMeasure::learnedLine)
                        .toList());
    }

    private static void cost(final SequenceMetrics.SequenceMetricsBuilder builder, final SequenceRun run) {
        final int count = run.segments().size();
        final int batched = batchedItems(run.calls());
        final int fallbacks = run.fallbackReasons().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        final double seconds = run.elapsed().toMillis() / 1000.0;
        builder.calls(run.calls().size())
                .callsPerSegment(count == 0 ? 0 : (double) run.calls().size() / count)
                .secondsPerSegment(count == 0 ? 0 : seconds / count)
                .elapsedSeconds(seconds)
                .batchedItems(batched)
                .batchFallbacks(fallbacks)
                .batchFallbackRate(batched == 0 ? 0 : (double) fallbacks / batched)
                .fallbackReasons(run.fallbackReasons())
                .editsApplied(
                        run.segments().stream().mapToInt(Decided::editsApplied).sum())
                .editsRefused(run.editsRefused())
                .callsByKind(callsByKind(run.calls()))
                .wastedCallRate(wastedRate(run.calls()))
                .costByKind(costByKind(run.calls()));
    }

    private static double wastedRate(final List<ModelCallFinished> calls) {
        final long wasted = calls.stream()
                .filter(call -> !call.isAnswered() || call.attempt() > 1)
                .count();
        return calls.isEmpty() ? 0 : (double) wasted / calls.size();
    }

    private static Map<String, SequenceMetrics.KindCost> costByKind(final List<ModelCallFinished> calls) {
        final CallKindTotals totals = new CallKindTotals();
        calls.forEach(totals::record);
        final Map<String, SequenceMetrics.KindCost> byName = new LinkedHashMap<>();
        totals.totals()
                .forEach((kind, t) -> byName.put(
                        kind.name(),
                        new SequenceMetrics.KindCost(
                                t.attempts(),
                                t.failed(),
                                t.promptTokens(),
                                t.completionTokens(),
                                t.elapsed().toMillis() / 1000.0)));
        return byName;
    }

    private static String learnedLine(final LexiconEntry entry) {
        final LexiconEntry.Learned learned = Objects.requireNonNull(entry.learned());
        return entry.term() + " -> " + learned.text() + " (" + learned.support() + "/" + learned.occurrences() + ")";
    }

    private static double mean(final int[] values) {
        return values.length == 0 ? 0 : Arrays.stream(values).sum() / (double) values.length;
    }

    private static double mean(final double[] values) {
        return values.length == 0 ? 0 : Arrays.stream(values).sum() / values.length;
    }

    private static double dominantShare(final Collection<Integer> counts) {
        final int total = counts.stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? 0 : (double) Collections.max(counts) / total;
    }

    private TermStat term(final Term term, final List<Decided> segments) {
        final Pattern source = Pattern.compile(term.pattern());
        final List<Decided> naming =
                segments.stream().filter(s -> source.matcher(s.source()).find()).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Rendering rendering : term.renderings()) {
            final Pattern word = Pattern.compile(WORD_START + rendering.pattern() + WORD_END);
            counts.put(rendering.label(), (int) naming.stream()
                    .filter(s -> s.target() != null
                            && word.matcher(s.target().toLowerCase(Locale.ROOT)).find())
                    .count());
        }
        return new TermStat(
                term.term(),
                naming.size(),
                counts,
                (int) counts.values().stream().filter(n -> n > 0).count(),
                dominantShare(counts.values()));
    }

    private NameStat name(final Name name, final List<Decided> segments) {
        final Pattern source = Pattern.compile("\\b" + Pattern.quote(name.name()) + "\\b");
        final List<Decided> naming =
                segments.stream().filter(s -> source.matcher(s.source()).find()).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String spelling : name.spellings()) {
            final Pattern word = Pattern.compile("(?<![\\p{L}\\p{M}])" + Pattern.quote(spelling));
            counts.put(spelling, (int) naming.stream()
                    .filter(s -> s.target() != null
                            && word.matcher(s.target().toLowerCase(Locale.ROOT)).find())
                    .count());
        }
        return new NameStat(name.name(), naming.size(), counts, (int)
                counts.values().stream().filter(n -> n > 0).count());
    }

    private int genderSlips(final List<Decided> segments) {
        return segments.stream()
                .filter(s -> s.narrator() == NarratorPerson.FIRST && s.target() != null)
                .mapToInt(s -> GenderChecks.named(GENDER_CHECK)
                        .find(s.target(), Gender.MALE)
                        .size())
                .sum();
    }

    private int findings(final List<Decided> segments, final FindingKind kind) {
        return (int) segments.stream()
                .filter(s -> s.target() != null)
                .filter(s -> textFindings(s).stream().anyMatch(f -> f.kind() == kind && f.blocking()))
                .count();
    }

    private List<CheckFinding> textFindings(final Decided segment) {
        return TextChecks.run(
                segment.source(), Objects.requireNonNull(segment.target()), fixture.source(), fixture.target());
    }

    private boolean isEnglishLeftover(final Decided segment) {
        final String target = segment.target();
        if (target == null) {
            return false;
        }
        return textFindings(segment).stream().anyMatch(f -> f.kind() == FindingKind.LEFTOVER_LANGUAGE)
                || isEcho(segment.source(), target);
    }

    private static boolean isEcho(final String source, final String target) {
        final long letters = source.codePoints().filter(Character::isLetter).count();
        return letters >= MIN_ECHO_LETTERS && source.strip().equalsIgnoreCase(target.strip());
    }

    private static Map<String, Integer> callsByKind(final List<ModelCallFinished> calls) {
        final Map<CallKind, Integer> counts = new EnumMap<>(CallKind.class);
        calls.forEach(call -> counts.merge(call.kind(), 1, Integer::sum));
        final Map<String, Integer> byName = new LinkedHashMap<>();
        counts.forEach((kind, n) -> byName.put(kind.name(), n));
        return byName;
    }

    private static int batchedItems(final List<ModelCallFinished> calls) {
        return calls.stream()
                .filter(call ->
                        call.kind() == CallKind.DRAFT && call.segmentIds().size() > 1)
                .mapToInt(call -> call.segmentIds().size())
                .sum();
    }
}
