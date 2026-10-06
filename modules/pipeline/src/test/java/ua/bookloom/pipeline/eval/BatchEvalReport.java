package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * The batch A/B of one model: per batch size the rates that decide the batch size —
 * the share of items answered under their id exactly once, the share that also kept their tokens (the token gate), the
 * omission and merge rates, the share of items that came back far too short or with the protocol leaked into their text,
 * the output tokens per item and the calls per item.
 *
 * @param model the model measured
 * @param rows every batch call's outcome
 */
record BatchEvalReport(String model, List<BatchEvalRow> rows) {

    /** Copies the rows. */
    BatchEvalReport {
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
    }

    /** The rates at one batch size. */
    record Cell(
            int size,
            int calls,
            int items,
            double idValidity,
            double tokenGate,
            double omission,
            double merge,
            double tooShort,
            double leaked,
            double outputTokensPerItem,
            double callFailures) {}

    /** The cells, size by size, for every size the rows hold. */
    List<Cell> cells() {
        final List<Cell> cells = new ArrayList<>();
        for (final int size : BatchEvalCases.SIZES) {
            final List<BatchEvalRow> own =
                    rows.stream().filter(row -> row.size() == size).toList();
            if (!own.isEmpty()) {
                cells.add(cell(size, own));
            }
        }
        return cells;
    }

    private static Cell cell(final int size, final List<BatchEvalRow> own) {
        final int items = own.stream().mapToInt(BatchEvalRow::size).sum();
        return new Cell(
                size,
                own.size(),
                items,
                share(own, BatchEvalRow::idValid, items),
                share(own, BatchEvalRow::tokenPass, items),
                share(own, BatchEvalRow::missing, items),
                share(own, BatchEvalRow::merged, items),
                share(own, BatchEvalRow::tooShort, items),
                share(own, BatchEvalRow::leaked, items),
                share(own, BatchEvalRow::outputTokens, items),
                (double) own.stream().filter(BatchEvalRow::callFailed).count() / own.size());
    }

    private static double share(final List<BatchEvalRow> own, final ToIntFunction<BatchEvalRow> part, final int items) {
        return items == 0 ? 0 : (double) own.stream().mapToInt(part).sum() / items;
    }

    /** The report as one JSON object; {@code suite} tells the matrix table it is not a case-set report. */
    String json() {
        final List<String> entries = new ArrayList<>();
        for (final Cell cell : cells()) {
            entries.add(String.format(
                    Locale.ROOT,
                    "{\"size\":%d,\"calls\":%d,\"items\":%d,\"idValidity\":%.3f,"
                            + "\"tokenGate\":%.3f,\"omission\":%.3f,\"merge\":%.3f,\"tooShort\":%.3f,"
                            + "\"leaked\":%.3f,\"outputTokensPerItem\":%.1f,"
                            + "\"callFailures\":%.3f}",
                    cell.size(),
                    cell.calls(),
                    cell.items(),
                    cell.idValidity(),
                    cell.tokenGate(),
                    cell.omission(),
                    cell.merge(),
                    cell.tooShort(),
                    cell.leaked(),
                    cell.outputTokensPerItem(),
                    cell.callFailures()));
        }
        return "{\"suite\":\"batch\",\"model\":\"" + model + "\",\"cells\":[" + String.join(",", entries) + "]}";
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        final List<String> lines = new ArrayList<>();
        lines.add("promptEval batch model=" + model);
        lines.add(String.format(
                Locale.ROOT,
                "%4s %5s %5s %8s %8s %8s %8s %8s %8s %8s",
                "size",
                "calls",
                "items",
                "idValid",
                "tokGate",
                "omit",
                "merge",
                "tooShort",
                "leaked",
                "outTok/i"));
        cells().forEach(cell -> lines.add(line(cell)));
        return String.join("\n", lines);
    }

    private static String line(final Cell cell) {
        return String.format(
                Locale.ROOT,
                "%4d %5d %5d %7.0f%% %7.0f%% %7.1f%% %7.1f%% %7.1f%% %7.1f%% %8.1f",
                cell.size(),
                cell.calls(),
                cell.items(),
                100 * cell.idValidity(),
                100 * cell.tokenGate(),
                100 * cell.omission(),
                100 * cell.merge(),
                100 * cell.tooShort(),
                100 * cell.leaked(),
                cell.outputTokensPerItem());
    }
}
