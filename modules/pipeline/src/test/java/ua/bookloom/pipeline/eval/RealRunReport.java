package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import ua.bookloom.pipeline.eval.RealRunRow.Outcome;

/**
 * The rates of a real-run run, grouped by the defect family and the call: the share of cases that came out as labelled,
 * the known failures reported beside them and outside every rate, the reviewer calls whose reply was cut by the output
 * cap, and the batch items that came back far too short or with the protocol leaked into their text.
 *
 * @param model the model measured
 * @param rows every row, in case order
 */
record RealRunReport(String model, List<RealRunRow> rows) {

    /** Copies the rows. */
    RealRunReport {
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
    }

    /**
     * One group of the table.
     *
     * @param key the family and the call, {@code kind/call}
     * @param passed rows that came out as labelled
     * @param counted rows that count in the rate: every row but the known failures
     * @param known rows of known failures, whatever they came to
     * @param nowPassing known failures that came out right, whose flag is to be dropped
     */
    record Cell(String key, int passed, int counted, int known, int nowPassing) {

        double rate() {
            return counted == 0 ? 1.0 : (double) passed / counted;
        }
    }

    /** The groups in the order the cases ran. */
    List<Cell> cells() {
        final Map<String, List<RealRunRow>> byKey = new LinkedHashMap<>();
        rows.forEach(row -> byKey.computeIfAbsent(row.kind() + "/" + row.call(), key -> new ArrayList<>())
                .add(row));
        return byKey.entrySet().stream()
                .map(entry -> cell(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Cell cell(final String key, final List<RealRunRow> own) {
        return new Cell(
                key,
                count(own, row -> row.outcome() == Outcome.PASS),
                count(own, RealRunRow::isCounted),
                count(own, row -> !row.isCounted()),
                count(own, row -> row.outcome() == Outcome.KNOWN_GREEN));
    }

    private static int count(final List<RealRunRow> own, final Predicate<RealRunRow> test) {
        return (int) own.stream().filter(test).count();
    }

    /** How many reviewer calls were cut by the output cap. */
    int truncatedCalls() {
        return count(rows, RealRunRow::truncated);
    }

    /** The share of batch items whose target is far shorter than its source. */
    double tooShortRate() {
        return share(RealRunRow::tooShort);
    }

    /** The share of batch items whose target carries the protocol around the text. */
    double leakedRate() {
        return share(RealRunRow::leaked);
    }

    private double share(final ToIntFunction<RealRunRow> part) {
        final int items = rows.stream().mapToInt(RealRunRow::items).sum();
        return items == 0 ? 0 : (double) rows.stream().mapToInt(part).sum() / items;
    }

    /** The share of reviewer rows whose repeated runs asked for the same changes. */
    double stability() {
        final List<RealRunRow> reviewer =
                rows.stream().filter(row -> row.call().startsWith("review")).toList();
        return reviewer.isEmpty() ? 1.0 : (double) count(reviewer, RealRunRow::stable) / reviewer.size();
    }

    /** The report as one JSON object; {@code suite} tells the matrix table which kind of report it is. */
    String json() {
        final List<String> groups = new ArrayList<>();
        for (final Cell cell : cells()) {
            groups.add(String.format(
                    Locale.ROOT,
                    "{\"key\":\"%s\",\"rate\":%.3f,\"passed\":%d,\"counted\":%d,\"known\":%d,\"nowPassing\":%d}",
                    cell.key(),
                    cell.rate(),
                    cell.passed(),
                    cell.counted(),
                    cell.known(),
                    cell.nowPassing()));
        }
        return String.format(
                Locale.ROOT,
                "{\"suite\":\"realrun\",\"model\":\"%s\",\"truncated\":%d,\"tooShort\":%.3f,\"leaked\":%.3f,"
                        + "\"stability\":%.3f,\"groups\":[%s]}",
                model,
                truncatedCalls(),
                tooShortRate(),
                leakedRate(),
                stability(),
                String.join(",", groups));
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        final List<String> lines = new ArrayList<>();
        lines.add("promptEval realrun model=" + model);
        lines.add(String.format(Locale.ROOT, "%-30s %-24s %-7s %s", "case", "group", "outcome", "reply"));
        rows.forEach(row -> lines.add(line(row)));
        lines.add(String.format(Locale.ROOT, "%-30s %5s %5s %7s %6s", "group", "ok", "n", "rate", "known"));
        cells().forEach(cell -> lines.add(String.format(
                Locale.ROOT,
                "%-30s %5d %5d %6.0f%% %6d",
                cell.key(),
                cell.passed(),
                cell.counted(),
                100 * cell.rate(),
                cell.known())));
        lines.add(String.format(
                Locale.ROOT,
                "truncated reviewer calls %d  batch tooShort %.1f%%  batch leaked %.1f%%  reviewer stability %.0f%%",
                truncatedCalls(),
                100 * tooShortRate(),
                100 * leakedRate(),
                100 * stability()));
        return String.join("\n", lines);
    }

    private static String line(final RealRunRow row) {
        final String detail = row.detail().replace('\n', ' ');
        return String.format(
                Locale.ROOT,
                "%-30s %-24s %-7s %s",
                row.id(),
                row.kind() + "/" + row.call(),
                row.outcome().label(),
                detail.length() > 80 ? detail.substring(0, 80) + "…" : detail);
    }
}
