package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;

/**
 * What the long-paragraph suite (15i.X3) drafted: each paragraph with the translation to grade and the calls it took.
 *
 * @param groups whether the sentence-group arm was on for the run
 * @param rows one row per paragraph, in set order
 */
record LongParagraphReport(boolean groups, List<Row> rows) {

    /** The suite name the reports and {@code scripts/eval-matrix.sh} use. */
    static final String SUITE = "longpara";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One paragraph's draft.
     *
     * @param id the paragraph id
     * @param words the paragraph's word count
     * @param calls the model calls its draft took, repairs included
     * @param source the English paragraph
     * @param target the draft the step restored, or empty when it came to no draft
     */
    record Row(String id, int words, int calls, String source, String target) {}

    /** Every model call of the run. */
    int calls() {
        return rows.stream().mapToInt(Row::calls).sum();
    }

    /**
     * The report as JSON, for {@code scripts/eval-matrix.sh} and the grader.
     *
     * @param model the model that drafted
     * @return the suite, the switch, the call count and every source and target
     */
    String json(final String model) {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("suite", SUITE);
        root.put("model", model);
        root.put("groups", groups);
        root.put("calls", calls());
        final var paragraphs = root.putArray("paragraphs");
        rows.forEach(row -> paragraphs
                .addObject()
                .put("id", row.id())
                .put("words", row.words())
                .put("calls", row.calls())
                .put("source", row.source())
                .put("target", row.target()));
        return root.toString();
    }

    /**
     * The report as a table a person reads.
     *
     * @param model the model that drafted
     * @return a header, one line per paragraph and a total line
     */
    String table(final String model) {
        final StringBuilder out = new StringBuilder(
                String.format(Locale.ROOT, "%-8s %6s %6s %12s%n", "id", "words", "calls", "targetWords"));
        rows.forEach(row -> out.append(String.format(
                Locale.ROOT, "%-8s %6d %6d %12d%n", row.id(), row.words(), row.calls(), wordsOf(row.target()))));
        return out.append(String.format(
                        Locale.ROOT,
                        "%s: %d paragraphs, %d calls, groups %s",
                        model,
                        rows.size(),
                        calls(),
                        groups ? "on" : "off"))
                .toString();
    }

    private static int wordsOf(final String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }
}
