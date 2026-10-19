package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What the names suite measured for one model: one {@link NamesRow} per book, in the files the other suites write to
 * {@code build/reports/promptEval}. {@code suite} tells the matrix which table to print.
 *
 * @param model the model measured
 * @param rows each book's result in run order
 */
record NamesReport(String model, List<NamesRow> rows) {

    static final String SUITE = "names";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double PERCENT = 100.0;

    /** Rejects a missing model and copies the rows. */
    NamesReport {
        Objects.requireNonNull(model, "model");
        rows = List.copyOf(rows);
    }

    /** The report as one JSON object: the suite, the model and each book's error code and score. */
    String json() {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("suite", SUITE);
        root.put("model", model);
        final ArrayNode books = root.putArray("books");
        for (final NamesRow row : rows) {
            final ObjectNode book = books.addObject();
            book.put("book", row.book());
            book.put("error", row.error());
            if (row.score() != null) {
                try {
                    book.set("score", MAPPER.readTree(row.score().json()));
                } catch (JsonProcessingException e) {
                    throw new UncheckedIOException(e);
                }
            }
            if (row.diagnostics() != null) {
                book.set("diagnostics", row.diagnostics().json());
            }
        }
        return root.toString();
    }

    /** One line per book with the measures that matter, a failed book with its error code. */
    String table() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "%-34s %7s %8s %6s %6s %9s %6s %5s %5s %9s %5s %6s %7s%n",
                "book",
                "gender",
                "genderEv",
                "wrongV",
                "type",
                "cluster1G",
                "spell",
                "junk",
                "lost",
                "termsOK",
                "promo",
                "calls",
                "minutes"));
        rows.forEach(row -> out.append(line(row)));
        return out.toString();
    }

    private static String line(final NamesRow row) {
        final NamesScore score = row.score();
        if (score == null) {
            return String.format(Locale.ROOT, "%-34s error %s%n", row.book(), row.error());
        }
        return String.format(
                Locale.ROOT,
                "%-34s %6.0f%% %7.0f%% %6d %5.0f%% %8.0f%% %6d %5d %5d %4d/%-4d %5d %6d %7.1f%n",
                row.book(),
                PERCENT * score.genderAccuracy(),
                PERCENT * score.genderAccuracyOverEvidence(),
                score.wrongButVerified(),
                PERCENT * score.typeAccuracy(),
                PERCENT * score.clusterRate(),
                score.maxSpellings(),
                score.junkKept(),
                score.realNamesLost(),
                score.recurringKept(),
                score.recurringKept() + score.recurringDropped(),
                score.recurringPromoted(),
                score.calls(),
                score.minutes());
    }
}
