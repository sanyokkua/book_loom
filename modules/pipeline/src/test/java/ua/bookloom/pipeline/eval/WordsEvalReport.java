package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The garbled-word check's measurement of one model: recall over the defective cases and the false-positive rate over
 * the clean ones, with the clean words the check flagged named so the owner can read them.
 *
 * @param model the model measured
 * @param rows one row per case
 */
record WordsEvalReport(String model, List<WordsEvalRow> rows) {

    /** The recall the owner asks of ADR-0042's chosen option. */
    static final double RECALL_TARGET = 0.70;

    /** Copies the rows. */
    WordsEvalReport {
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
    }

    /** Defective cases whose every garbled word was reported, over all defective cases; zero when there are none. */
    double recall() {
        return share(WordsEvalRow::defective, WordsEvalRow::caught);
    }

    /** Clean cases with any word reported, over all clean cases; zero when there are none. */
    double falsePositiveRate() {
        return share(row -> !row.defective(), WordsEvalRow::falseAlarm);
    }

    /** Whether the report meets the task's bar: recall at least 70% and no clean word flagged. */
    boolean meetsTarget() {
        return recall() >= RECALL_TARGET && falsePositiveRate() == 0.0;
    }

    private double share(final Predicate<WordsEvalRow> population, final Predicate<WordsEvalRow> hit) {
        final List<WordsEvalRow> in = rows.stream().filter(population).toList();
        return in.isEmpty() ? 0.0 : (double) in.stream().filter(hit).count() / in.size();
    }

    /** The report as one JSON object; {@code suite} tells the matrix table it is not a case-set report. */
    String json() {
        return String.format(
                Locale.ROOT,
                "{\"suite\":\"words\",\"model\":\"%s\",\"cases\":%d,\"recall\":%.3f,\"falsePositive\":%.3f,"
                        + "\"meetsTarget\":%s}",
                model,
                rows.size(),
                recall(),
                falsePositiveRate(),
                meetsTarget());
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "promptEval words model=%s recall=%.0f%% falsePositive=%.0f%% meetsTarget=%s",
                model,
                100 * recall(),
                100 * falsePositiveRate(),
                meetsTarget()));
        for (final WordsEvalRow row : rows) {
            out.append(String.format(
                    Locale.ROOT,
                    "%n%-26s %-9s expected=%s flagged=%s",
                    row.id(),
                    row.defective() ? (row.caught() ? "caught" : "MISSED") : (row.falseAlarm() ? "FALSE" : "clean"),
                    row.expected(),
                    row.flagged()));
        }
        return out.toString();
    }
}
