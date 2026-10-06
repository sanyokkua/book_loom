package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                        pct(m.dominantShareMean())));
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

    private static String f2(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
