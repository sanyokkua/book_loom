package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import ua.bookloom.pipeline.eval.EvalRow.Check;

/**
 * The rates a prompt-eval run is held to, and the table it prints.
 *
 * @param model the model the run measured
 * @param rows every case's outcome, in case order
 * @param defectRows the corpus reviewer outcomes
 * @param rules {@code language} for a run with the language-rules map, {@code generic} for one forced to the generic
 *     rules, so a matrix can set the two side by side
 */
record EvalReport(String model, List<EvalRow> rows, List<DefectRow> defectRows, String rules) {

    static final double PARSE_FLOOR = 0.95;
    static final double GATE_FLOOR = 0.90;
    static final double REVIEW_FLOOR = 0.80;

    private static final int DETAIL_WIDTH = 90;
    private static final String ROW = "%-22s %-5s %-5s %-5s %-6s %-6s %-6s %-5s %s";

    /** Copies the rows. */
    EvalReport {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(rules, "rules");
        rows = List.copyOf(rows);
        defectRows = List.copyOf(defectRows);
    }

    EvalReport(final String model, final List<EvalRow> rows) {
        this(model, rows, List.of(), "language");
    }

    double parseRate() {
        return rate(EvalRow::parsed);
    }

    double gateRate() {
        return rate(EvalRow::gate);
    }

    double scriptRate() {
        return rate(EvalRow::script);
    }

    double markerRate() {
        return rate(EvalRow::marker);
    }

    double injectionRate() {
        return rate(EvalRow::injection);
    }

    double reviewRate() {
        return rate(EvalRow::reviewed);
    }

    /** Whether the parse, gate and review-separation rates reach their floors. */
    boolean meetsThresholds() {
        return parseRate() >= PARSE_FLOOR
                && gateRate() >= GATE_FLOOR
                && reviewRate() >= REVIEW_FLOOR
                && meetsDefectThresholds();
    }

    /** Whether the corpus reviewer rates reach the model class's thresholds and no applied edit broke a token; true when no
     * corpus was run. */
    boolean meetsDefectThresholds() {
        if (defectRows.isEmpty()) {
            return true;
        }
        final EvalThresholds limits = EvalThresholds.forModel(model);
        return EvalMetrics.falseNegativeRate(defectRows) <= limits.falseNegativeMax()
                && EvalMetrics.falsePositiveRate(defectRows) <= limits.falsePositiveMax()
                && EvalMetrics.stability(defectRows) >= limits.stabilityMin()
                && EvalMetrics.tokenBreaks(defectRows) == 0;
    }

    /** One line of the corpus rates, empty when no corpus was run. */
    String defectSummary() {
        if (defectRows.isEmpty()) {
            return "";
        }
        return String.format(
                Locale.ROOT,
                "corpus n=%d runs=%d  reviewParse %.0f%%  catch %.0f%%  falseAlarmEdits %.0f%%  stability %.0f%%"
                        + "  tokenBreaks %d",
                defectRows.size(),
                defectRows.get(0).runs(),
                percent(EvalMetrics.parseRate(defectRows)),
                percent(EvalMetrics.catchRate(defectRows)),
                percent(EvalMetrics.falsePositiveRate(defectRows)),
                percent(EvalMetrics.stability(defectRows)),
                EvalMetrics.tokenBreaks(defectRows));
    }

    /** One line of the share of cases right per defect family, empty when no corpus was run. */
    String kindSummary() {
        if (defectRows.isEmpty()) {
            return "";
        }
        return "corpus by kind  "
                + EvalMetrics.rightByKind(defectRows).entrySet().stream()
                        .map(entry ->
                                String.format(Locale.ROOT, "%s %.0f%%", entry.getKey(), percent(entry.getValue())))
                        .collect(Collectors.joining("  "));
    }

    private String kindJson() {
        return EvalMetrics.rightByKind(defectRows).entrySet().stream()
                .map(entry -> String.format(Locale.ROOT, "\"%s\":%.3f", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** The report as one JSON object: the rates a matrix compares. */
    String json() {
        return String.format(
                Locale.ROOT,
                "{\"model\":\"%s\",\"rules\":\"%s\",\"class\":\"%s\",\"parse\":%.3f,\"gate\":%.3f,\"script\":%.3f,"
                        + "\"marker\":%.3f,\"injection\":%.3f,\"reviewSeparation\":%.3f,"
                        + "\"reviewParse\":%.3f,\"falseNegative\":%.3f,\"falsePositive\":%.3f,"
                        + "\"stability\":%.3f,\"tokenBreaks\":%d,\"byKind\":%s,\"meetsThresholds\":%b}",
                model,
                rules,
                EvalThresholds.classOf(model),
                parseRate(),
                gateRate(),
                scriptRate(),
                markerRate(),
                injectionRate(),
                reviewRate(),
                EvalMetrics.parseRate(defectRows),
                EvalMetrics.falseNegativeRate(defectRows),
                EvalMetrics.falsePositiveRate(defectRows),
                EvalMetrics.stability(defectRows),
                EvalMetrics.tokenBreaks(defectRows),
                kindJson(),
                meetsThresholds());
    }

    /** The table: one line per case, then the rates. */
    String table() {
        final List<String> lines = new ArrayList<>();
        lines.add("promptEval model=" + model + " rules=" + rules);
        lines.add(String.format(
                Locale.ROOT, ROW, "case", "kind", "parse", "gate", "script", "marker", "inject", "review", "reply"));
        rows.forEach(row -> lines.add(line(row)));
        lines.add(String.format(
                Locale.ROOT,
                "parse %.0f%% (>= %.0f%%)  gate %.0f%% (>= %.0f%%)  review %.0f%% (>= %.0f%%)  script %.0f%%  marker"
                        + " %.0f%%  injection %.0f%%",
                percent(parseRate()),
                percent(PARSE_FLOOR),
                percent(gateRate()),
                percent(GATE_FLOOR),
                percent(reviewRate()),
                percent(REVIEW_FLOOR),
                percent(scriptRate()),
                percent(markerRate()),
                percent(injectionRate())));
        defectRows.forEach(row -> lines.add(defectLine(row)));
        lines.add(defectSummary());
        lines.add(kindSummary());
        return String.join("\n", lines);
    }

    private static String defectLine(final DefectRow row) {
        return String.format(
                Locale.ROOT,
                "corpus %-22s %-12s defective=%-5b flagged=%-5b byReviewer=%-5b readable=%-5b stable=%b",
                row.id(),
                row.kind(),
                row.defective(),
                row.flagged(),
                row.reviewerFlagged(),
                row.readable(),
                row.stable());
    }

    private static String line(final EvalRow row) {
        final String detail = row.detail().replace('\n', ' ');
        return String.format(
                Locale.ROOT,
                ROW,
                row.name(),
                row.kind(),
                row.parsed().label(),
                row.gate().label(),
                row.script().label(),
                row.marker().label(),
                row.injection().label(),
                row.reviewed().label(),
                detail.length() > DETAIL_WIDTH ? detail.substring(0, DETAIL_WIDTH) + "…" : detail);
    }

    private double rate(final Function<EvalRow, Check> check) {
        final long applicable =
                rows.stream().map(check).filter(value -> value != Check.NA).count();
        final long passed =
                rows.stream().map(check).filter(value -> value == Check.PASS).count();
        return applicable == 0 ? 1.0 : (double) passed / applicable;
    }

    private static double percent(final double rate) {
        return rate * 100;
    }
}
