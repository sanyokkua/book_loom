package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import ua.bookloom.pipeline.eval.EvalRow.Check;

/**
 * The rates a prompt-eval run is held to, and the table it prints.
 *
 * @param model the model the run measured
 * @param rows every case's outcome, in case order
 */
record EvalReport(String model, List<EvalRow> rows) {

    static final double PARSE_FLOOR = 0.95;
    static final double GATE_FLOOR = 0.90;
    static final double JUDGE_FLOOR = 0.80;

    private static final int DETAIL_WIDTH = 90;
    private static final String ROW = "%-22s %-5s %-5s %-5s %-6s %-6s %-6s %-5s %s";

    /** Copies the rows. */
    EvalReport {
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
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

    double judgeRate() {
        return rate(EvalRow::judged);
    }

    /** Whether the parse, gate and judge-separation rates reach their floors. */
    boolean meetsThresholds() {
        return parseRate() >= PARSE_FLOOR && gateRate() >= GATE_FLOOR && judgeRate() >= JUDGE_FLOOR;
    }

    /** The table: one line per case, then the rates. */
    String table() {
        final List<String> lines = new ArrayList<>();
        lines.add("promptEval model=" + model);
        lines.add(String.format(
                Locale.ROOT, ROW, "case", "kind", "parse", "gate", "script", "marker", "inject", "judge", "reply"));
        rows.forEach(row -> lines.add(line(row)));
        lines.add(String.format(
                Locale.ROOT,
                "parse %.0f%% (>= %.0f%%)  gate %.0f%% (>= %.0f%%)  judge %.0f%% (>= %.0f%%)  script %.0f%%  marker"
                        + " %.0f%%  injection %.0f%%",
                percent(parseRate()),
                percent(PARSE_FLOOR),
                percent(gateRate()),
                percent(GATE_FLOOR),
                percent(judgeRate()),
                percent(JUDGE_FLOOR),
                percent(scriptRate()),
                percent(markerRate()),
                percent(injectionRate())));
        return String.join("\n", lines);
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
                row.judged().label(),
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
