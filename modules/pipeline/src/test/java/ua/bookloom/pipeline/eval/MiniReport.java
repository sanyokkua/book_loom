package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.TermType;

/**
 * What the mini suite measured for one model, in the files the other suites write to {@code build/reports/promptEval}.
 * {@code suite} tells the matrix which table to print.
 *
 * @param model the model measured
 * @param dial the quality dial of the run
 * @param run what the run decided
 * @param results every applicable expectation scored
 * @param labels the dataset's case labels, which the table summarises
 */
record MiniReport(String model, String dial, MiniRun run, List<MiniResult> results, MiniLabels labels) {

    static final String SUITE = "mini";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double PERCENT = 100.0;
    private static final double SECONDS = 1000.0;

    /** Copies the results. */
    MiniReport {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(labels, "labels");
        results = List.copyOf(results);
    }

    /** A report with no case labels. */
    MiniReport(final String model, final String dial, final MiniRun run, final List<MiniResult> results) {
        this(model, dial, run, results, MiniLabels.NONE);
    }

    int passed() {
        return (int) results.stream().filter(MiniResult::passed).count();
    }

    double passRate() {
        return results.isEmpty() ? 0 : (double) passed() / results.size();
    }

    int flagged() {
        return (int) run.outcomes().values().stream()
                .filter(o -> o.status() == SegmentStatus.FLAGGED)
                .count();
    }

    /** Per defect, how many expectations passed and how many there were. */
    Map<String, int[]> byDefect() {
        final Map<String, int[]> byDefect = new LinkedHashMap<>();
        for (final MiniResult result : results) {
            final int[] counts = byDefect.computeIfAbsent(result.defect(), key -> new int[2]);
            counts[0] += result.passed() ? 1 : 0;
            counts[1]++;
        }
        return byDefect;
    }

    /** The per-case records of repeat {@code repeat}, whose requests carried {@code seed}. */
    List<CaseRecord.Entry> cases(final int repeat, final int seed) {
        return MiniCases.of(run, results, repeat, seed);
    }

    /** The report as one JSON object. */
    String json() {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("suite", SUITE);
        root.put("model", model);
        root.put("dial", dial);
        root.put("names", run.policy().name());
        root.put("end", run.end().name());
        root.put("expectations", results.size());
        root.put("passed", passed());
        root.put("passRate", passRate());
        root.put("segments", run.outcomes().size());
        root.put("flagged", flagged());
        root.put("calls", run.calls().total());
        root.put("namesCalls", run.calls().names());
        root.put("translationCalls", run.calls().translation());
        root.put("consistencyCalls", run.calls().consistency());
        root.put("seconds", run.elapsed().toMillis() / SECONDS);
        root.set("summaryGate", MAPPER.valueToTree(run.summaryGate()));
        final ArrayNode errors = root.putArray("errors");
        run.errors().forEach(errors::add);
        putDefects(root);
        putResults(root);
        putCases(root);
        putOutputs(root);
        return root.toString();
    }

    private void putDefects(final ObjectNode root) {
        final ObjectNode defects = root.putObject("byDefect");
        byDefect()
                .forEach((defect, counts) ->
                        defects.putObject(defect).put("passed", counts[0]).put("total", counts[1]));
    }

    private void putCases(final ObjectNode root) {
        final ArrayNode rows = root.putArray("cases");
        cases(0, EvalRepeats.BASE_SEED).forEach(entry -> {
            try {
                rows.add(MAPPER.readTree(entry.record().json()));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * The first report's JSON with every repeat's figures (pass rate, flagged, calls, seconds, what the chapter-summary
     * gate kept and dropped), stored targets, proofread verdicts and characters, so a K-run report keeps what each repeat decided and with
     * which gender origins (arm 4b reads both).
     *
     * @param reports one report per repeat, in repeat order; not empty
     * @param seeds each repeat's seed, in the same order
     * @return the JSON object as text
     */
    static String json(final List<MiniReport> reports, final List<Integer> seeds) {
        try {
            final ObjectNode root =
                    (ObjectNode) MAPPER.readTree(reports.getFirst().json());
            final ArrayNode repeats = root.putArray("repeats");
            for (int repeat = 0; repeat < reports.size(); repeat++) {
                final ObjectNode row = repeats.addObject().put("seed", seeds.get(repeat));
                final MiniRun each = reports.get(repeat).run();
                reports.get(repeat).putFigures(row);
                final ObjectNode outputs = row.putObject("outputs");
                each.outcomes().forEach((id, outcome) -> outputs.put(id, outcome.target()));
                putProofread(row, each);
                final ArrayNode characters = row.putArray("characters");
                each.glossary().stream()
                        .filter(entry -> entry.type() == TermType.CHARACTER)
                        .forEach(entry -> characters
                                .addObject()
                                .put("term", entry.term())
                                .put("gender", entry.gender().name())
                                .put(
                                        "origin",
                                        entry.genderOrigin() == null
                                                ? null
                                                : entry.genderOrigin().name()));
            }
            return root.toString();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    // What the repeat's Proofread stage decided per segment it read, with the text before and after, so two arms'
    // changed paragraphs can be judged (arm 7c).
    private static void putProofread(final ObjectNode row, final MiniRun run) {
        final ArrayNode rows = row.putArray("proofread");
        run.proofread()
                .forEach(read -> rows.addObject()
                        .put("id", read.id())
                        .put("outcome", read.outcome().name())
                        .put("reason", read.reason())
                        .put("round", read.round())
                        .put("before", read.before().target())
                        .put("after", read.after().target()));
    }

    // One repeat's own figures beside its outputs, so a K-run report can be compared repeat by repeat (arm 6).
    private void putFigures(final ObjectNode row) {
        row.put("passRate", passRate())
                .put("flagged", flagged())
                .put("calls", run.calls().total())
                .put("translationCalls", run.calls().translation())
                .put("seconds", run.elapsed().toMillis() / SECONDS);
        row.set("summaryGate", MAPPER.valueToTree(run.summaryGate()));
    }

    /** Every block's stored target of this report's run, so a baseline keeps the text of invented books. */
    private void putOutputs(final ObjectNode root) {
        final ObjectNode outputs = root.putObject("outputs");
        run.outcomes().forEach((id, outcome) -> outputs.put(id, outcome.target()));
    }

    private void putResults(final ObjectNode root) {
        final ArrayNode rows = root.putArray("results");
        results.forEach(r -> rows.addObject()
                .put("id", r.id())
                .put("defect", r.defect())
                .put("passed", r.passed())
                .put("detail", r.detail()));
    }

    /** The summary line, then every expectation that was not met with what was found. */
    String table() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "%s names=%s dial=%s: %d/%d expectations (%.0f%%), %d segments, %d flagged, %d calls, %.0f s, job %s%n",
                model,
                run.policy(),
                dial,
                passed(),
                results.size(),
                PERCENT * passRate(),
                run.outcomes().size(),
                flagged(),
                run.calls().total(),
                run.elapsed().toMillis() / SECONDS,
                run.end()));
        run.errors().forEach(error -> out.append("  ERROR ").append(error).append('\n'));
        out.append(gateLine(run.summaryGate()));
        results.stream()
                .filter(r -> !r.passed())
                .forEach(r -> out.append(
                        String.format(Locale.ROOT, "  MISS %-22s %-26s %s%n", r.id(), r.defect(), r.detail())));
        out.append(labels.describe(usableByBlock()));
        return out.toString();
    }

    // The chapter-summary gate's figures; none when the run kept no summary and the gate read nothing.
    private static String gateLine(final SummaryGateTally gate) {
        if (gate.sentences() == 0 && gate.notKept().isEmpty()) {
            return "";
        }
        return String.format(
                Locale.ROOT,
                "  chapter summaries %d kept, not kept %s; gate dropped %d of %d sentences (%.1f%%) %s%n",
                gate.chaptersKept(),
                gate.notKept(),
                gate.dropped(),
                gate.sentences(),
                PERCENT * Math.max(0, gate.dropShare()),
                gate.decisions());
    }

    private Map<String, Boolean> usableByBlock() {
        final Map<String, Boolean> usable = new LinkedHashMap<>();
        run.outcomes()
                .forEach((id, o) -> usable.put(
                        id,
                        o.target() != null
                                && o.status() != SegmentStatus.FLAGGED
                                && o.status() != SegmentStatus.PENDING));
        return usable;
    }
}
