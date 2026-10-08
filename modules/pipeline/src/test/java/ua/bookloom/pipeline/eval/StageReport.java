package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * A stage suite's measurement of one model: the share of cases that ended as stated and the wasted work (failed and
 * repeated calls, refused answers) beside it, in the same {@code build/reports/promptEval} files the other suites write.
 *
 * @param suite the suite name, {@code prescan}, {@code terms}, {@code setup}, {@code consistency} or {@code retry}
 * @param model the model measured
 * @param rows one row per case and stage
 */
record StageReport(String suite, String model, List<StageRow> rows) {

    /** Copies the rows. */
    StageReport {
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
    }

    /** Rows that passed over all rows; zero when there are none. */
    double passRate() {
        return rows.isEmpty()
                ? 0.0
                : (double) rows.stream().filter(StageRow::passed).count() / rows.size();
    }

    int calls() {
        return sum(StageRow::calls);
    }

    int failed() {
        return sum(StageRow::failed);
    }

    int repeated() {
        return sum(StageRow::repeated);
    }

    int refused() {
        return sum(StageRow::refused);
    }

    private int sum(final ToIntFunction<StageRow> part) {
        return rows.stream().mapToInt(part).sum();
    }

    /** The report as one JSON object; {@code suite} tells the matrix table which suite it is. */
    String json() {
        return String.format(
                Locale.ROOT,
                "{\"suite\":\"%s\",\"model\":\"%s\",\"cases\":%d,\"passRate\":%.3f,\"calls\":%d,\"failed\":%d,"
                        + "\"repeated\":%d,\"refused\":%d}",
                suite,
                model,
                rows.size(),
                passRate(),
                calls(),
                failed(),
                repeated(),
                refused());
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "promptEval %s model=%s pass=%.0f%% calls=%d failed=%d repeated=%d refused=%d",
                suite,
                model,
                100 * passRate(),
                calls(),
                failed(),
                repeated(),
                refused()));
        for (final StageRow row : rows) {
            out.append(String.format(
                    Locale.ROOT,
                    "%n%-44s %-5s calls=%d failed=%d repeated=%d refused=%d  %s",
                    row.id(),
                    row.passed() ? "ok" : "FAIL",
                    row.calls(),
                    row.failed(),
                    row.repeated(),
                    row.refused(),
                    row.detail()));
        }
        return out.toString();
    }
}
