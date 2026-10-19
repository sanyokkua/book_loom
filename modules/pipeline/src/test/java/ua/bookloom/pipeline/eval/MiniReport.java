package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.document.SegmentStatus;

/**
 * What the mini suite measured for one model, in the files the other suites write to {@code build/reports/promptEval}.
 * {@code suite} tells the matrix which table to print.
 *
 * @param model the model measured
 * @param dial the quality dial of the run
 * @param run what the run decided
 * @param results every applicable expectation scored
 */
record MiniReport(String model, String dial, MiniRun run, List<MiniResult> results) {

    static final String SUITE = "mini";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double PERCENT = 100.0;
    private static final double SECONDS = 1000.0;

    /** Copies the results. */
    MiniReport {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(run, "run");
        results = List.copyOf(results);
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
        final ArrayNode errors = root.putArray("errors");
        run.errors().forEach(errors::add);
        putDefects(root);
        putResults(root);
        return root.toString();
    }

    private void putDefects(final ObjectNode root) {
        final ObjectNode defects = root.putObject("byDefect");
        byDefect()
                .forEach((defect, counts) ->
                        defects.putObject(defect).put("passed", counts[0]).put("total", counts[1]));
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
        results.stream()
                .filter(r -> !r.passed())
                .forEach(r -> out.append(
                        String.format(Locale.ROOT, "  MISS %-22s %-26s %s%n", r.id(), r.defect(), r.detail())));
        return out.toString();
    }
}
