package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import ua.bookloom.pipeline.eval.SequenceMetrics.NameStat;
import ua.bookloom.pipeline.eval.SequenceMetrics.TermStat;

/** The sequence metrics of one model as the table printed to the log and the JSON the matrix reads. */
record SequenceReport(SequenceMetrics metrics) {

    /** Rejects missing metrics. */
    SequenceReport {
        Objects.requireNonNull(metrics, "metrics");
    }

    String json() {
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The metrics with the target of every segment by id, so a baseline keeps the text of the invented book. */
    String json(final SequenceRun run) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectNode root = (ObjectNode) mapper.readTree(json());
            final ObjectNode outputs = root.putObject("outputs");
            run.segments().forEach(segment -> outputs.put(segment.id(), segment.target()));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The first repeat's report with every repeat's own figures beside it, so a K-repeat arm decides on all of them:
     * {@code ltcr1PerRepeat} and a {@code perRepeat} entry per repeat (its seed, LTCR-1, first-round hard-gate failures,
     * flagged, batch fallbacks, calls, seconds per segment, prompt and cached tokens, and the key-term asks stopping).
     *
     * @param run the first repeat's run, whose targets the report keeps
     * @param seeds the repeats' seeds, in repeat order
     * @param repeats every repeat's metrics, in repeat order, the first being this report's
     */
    String json(final SequenceRun run, final List<Integer> seeds, final List<SequenceMetrics> repeats) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectNode root = (ObjectNode) mapper.readTree(json(run));
            repeats.forEach(repeat -> root.withArray("ltcr1PerRepeat").add(repeat.ltcr1()));
            for (int k = 0; k < repeats.size(); k++) {
                root.withArray("perRepeat").add(repeatNode(mapper, k, seeds.get(k), repeats.get(k)));
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode repeatNode(
            final ObjectMapper mapper, final int repeat, final int seed, final SequenceMetrics m) {
        final ObjectNode node = mapper.createObjectNode()
                .put("repeat", repeat)
                .put("seed", seed)
                .put("ltcr1", m.ltcr1())
                .put("hardGateFailuresRound0", m.hardGateFailuresRound0())
                .put("flagged", m.flagged())
                .put("segments", m.segments())
                .put("batchFallbacks", m.batchFallbacks())
                .put("batchedItems", m.batchedItems())
                .put("calls", m.calls())
                .put("callsPerSegment", m.callsPerSegment())
                .put("secondsPerSegment", m.secondsPerSegment())
                .put("promptTokens", m.promptTokens())
                .put("cachedPromptTokens", m.cachedPromptTokens());
        node.set("hardGateKinds", mapper.valueToTree(m.hardGateKinds()));
        return node;
    }

    /** One line per repeat with the figures a K-repeat arm compares. */
    static String repeatLines(final List<SequenceMetrics> repeats) {
        final List<String> lines = new ArrayList<>();
        for (int k = 0; k < repeats.size(); k++) {
            final SequenceMetrics m = repeats.get(k);
            lines.add(row(
                    "repeat",
                    k,
                    "LTCR-1",
                    ltcr(m.ltcr1()),
                    "hardGateRound0",
                    m.hardGateFailuresRound0(),
                    "flagged",
                    m.flagged(),
                    "fallbacks",
                    m.batchFallbacks(),
                    "calls",
                    m.calls(),
                    "sec/seg",
                    f2(m.secondsPerSegment())));
        }
        return String.join("\n", lines);
    }

    String table() {
        final List<String> lines = new ArrayList<>();
        lines.add("promptEval sequence model=" + metrics.model() + " dial=" + metrics.dial() + " narrator="
                + metrics.narrator());
        lines.addAll(counts(metrics));
        lines.addAll(defects(metrics));
        lines.addAll(realRun(metrics));
        lines.addAll(cost(metrics));
        lines.add("terms (segments naming it; renderings seen):");
        metrics.terms().forEach(t -> lines.add(term(t)));
        lines.add("names (segments naming it; spellings seen):");
        metrics.names().forEach(n -> lines.add(name(n)));
        lines.add("learned: " + metrics.learnedRenderings());
        return String.join("\n", lines);
    }

    private static List<String> counts(final SequenceMetrics m) {
        return List.of(
                row(
                        "segments",
                        m.segments(),
                        "flagged",
                        pct(m.flaggedRate()),
                        "flaggedNoTarget",
                        m.flaggedWithoutTarget()),
                row(
                        "renderings/term",
                        f2(m.renderingsPerTerm()),
                        "nameVariants",
                        m.nameVariants(),
                        "lexicon/term",
                        f2(m.lexiconDistinct())));
    }

    private static List<String> defects(final SequenceMetrics m) {
        return List.of(
                row(
                        "genderSlips",
                        m.genderSlips(),
                        "english",
                        m.englishLeftovers(),
                        "quote",
                        m.quoteFailures(),
                        "asciiQuote",
                        m.asciiQuotes()),
                row(
                        "mixedScript",
                        m.mixedScript(),
                        "learned",
                        m.learned(),
                        "learnedCoverage",
                        pct(m.learnedCoverage())));
    }

    private static List<String> realRun(final SequenceMetrics m) {
        return List.of(
                row(
                        "hardGateRound0",
                        m.hardGateFailuresRound0(),
                        "kinds",
                        m.hardGateKinds(),
                        "leakedProtocol",
                        m.leakedProtocol(),
                        "reviewerTruncated",
                        m.reviewerTruncated()),
                row(
                        "termClaimedWrong",
                        m.termClaimedWrong(),
                        "claimed",
                        m.claimedRenderings(),
                        "dominantShare",
                        pct(m.dominantShareMean()),
                        "LTCR-1",
                        ltcr(m.ltcr1())));
    }

    private static List<String> cost(final SequenceMetrics m) {
        return List.of(
                row(
                        "calls/seg",
                        f2(m.callsPerSegment()),
                        "sec/seg",
                        f2(m.secondsPerSegment()),
                        "elapsedSec",
                        f2(m.elapsedSeconds())),
                row(
                        "promptRead sec/seg",
                        f2(m.promptReadSecondsPerSegment()),
                        "promptTokens",
                        m.promptTokens(),
                        "cached",
                        m.cachedPromptTokens()),
                row(
                        "batchFallbacks",
                        m.batchFallbacks() + "/" + m.batchedItems(),
                        "rate",
                        pct(m.batchFallbackRate()),
                        "reasons",
                        m.fallbackReasons()),
                row("editsApplied", m.editsApplied(), "editsRefused", m.editsRefused(), "calls", m.callsByKind()));
    }

    private static String term(final TermStat t) {
        return String.format(
                Locale.ROOT,
                "  %-10s n=%-3d distinct=%d dominant=%s %s",
                t.term(),
                t.segments(),
                t.distinct(),
                pct(t.dominantShare()),
                t.renderings());
    }

    private static String name(final NameStat n) {
        return String.format(
                Locale.ROOT, "  %-10s n=%-3d variants=%d %s", n.name(), n.segments(), n.variants(), n.spellings());
    }

    private static String row(final Object... cells) {
        final StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i += 2) {
            line.append(i == 0 ? "" : "  ").append(cells[i]).append(' ').append(cells[i + 1]);
        }
        return line.toString();
    }

    private static String pct(final double share) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * share);
    }

    private static String ltcr(final double ratio) {
        return ratio < 0 ? "n/a" : pct(ratio);
    }

    private static String f2(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
