package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.CsvProblem;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Reads a glossary back after an import and words what a glossary import did: the rows taken, the lines it could not
 * read with each cause named once (and the values a file may use), and the refused lines. Runs of lines read as spans
 * and at most {@value #SPANS_SHOWN} spans are named, so a file of 5,000 bad rows reads as one sentence, not a wall.
 */
@Slf4j
final class ImportSummary {

    private static final int SPANS_SHOWN = 10;
    private static final String SPAN_DASH = "–";

    private final Messages messages;

    ImportSummary(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** What an import did and the glossary as it stands afterwards. */
    record Imported(GlossaryImportReport report, List<GlossaryEntry> all) {}

    static Result<Imported> readAfterImport(final GlossaryService glossary, final String project, final Path source) {
        return glossary.importCsv(project, source)
                .flatMap(report -> glossary.entries(project).map(all -> new Imported(report, all)));
    }

    String of(final GlossaryImportReport report) {
        Objects.requireNonNull(report, "report");
        log.debug(
                "import read {} rows, {} malformed, {} refused",
                report.imported(),
                report.malformedLines().size(),
                report.refusedLines().size());
        final StringBuilder summary =
                new StringBuilder(messages.get(MessageKey.NAMES_STYLE_IMPORT_DONE, report.imported()));
        append(summary, MessageKey.NAMES_STYLE_IMPORT_MALFORMED, report.malformedLines());
        // Each cause once, in a fixed order, whatever the number of rows it struck.
        Arrays.stream(CsvProblem.values())
                .filter(report.problems()::contains)
                .forEach(problem -> summary.append(' ').append(wordsOf(problem)));
        append(summary, MessageKey.NAMES_STYLE_IMPORT_REFUSED, report.refusedLines());
        if (report.imported() == 0
                && report.malformedLines().isEmpty()
                && report.refusedLines().isEmpty()) {
            log.debug("import found no rows in the file");
            summary.append(' ').append(messages.get(MessageKey.NAMES_STYLE_IMPORT_NO_ROWS));
        }
        if (report.duplicates() > 0) {
            log.debug("import kept the last of {} repeated terms", report.duplicates());
            summary.append(' ').append(messages.get(MessageKey.NAMES_STYLE_IMPORT_DUPLICATES, report.duplicates()));
        }
        return summary.toString();
    }

    GlossaryNotice exported(final Path written) {
        return new GlossaryNotice(
                GlossaryNotice.Level.INFO, messages.get(MessageKey.NAMES_STYLE_EXPORT_DONE, written.getFileName()));
    }

    GlossaryNotice exportedTerms(final Path written) {
        return new GlossaryNotice(
                GlossaryNotice.Level.INFO, messages.get(MessageKey.RECURRING_EXPORT_DONE, written.getFileName()));
    }

    private void append(final StringBuilder summary, final MessageKey key, final List<Integer> lines) {
        if (!lines.isEmpty()) {
            summary.append(' ').append(messages.get(key, spansOf(lines), lines.size()));
        }
    }

    private String wordsOf(final CsvProblem problem) {
        return switch (problem) {
            case OPEN_QUOTE -> messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_QUOTE);
            case FIELD_COUNT -> messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_FIELDS);
            case NO_TERM -> messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_TERM);
            case UNKNOWN_TYPE -> messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_TYPE, valuesOf(TermType.values()));
            case UNKNOWN_GENDER ->
                messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_GENDER, valuesOf(Gender.values()));
            case UNKNOWN_LOCKED -> messages.get(MessageKey.NAMES_STYLE_IMPORT_PROBLEM_LOCKED);
        };
    }

    // The values as the file writes them: the glossary export spells each constant in lower case.
    private static String valuesOf(final Enum<?>[] constants) {
        return Arrays.stream(constants)
                .map(constant -> constant.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    // Ascending lines as spans ("2–5001", "7"), the first SPANS_SHOWN of them, then how many lines were left out.
    private String spansOf(final List<Integer> lines) {
        final List<String> spans = new ArrayList<>();
        int shownLines = 0;
        int start = 0;
        while (start < lines.size() && spans.size() < SPANS_SHOWN) {
            int end = start;
            while (end + 1 < lines.size() && lines.get(end + 1) == lines.get(end) + 1) {
                end++;
            }
            spans.add(start == end ? String.valueOf(lines.get(start)) : lines.get(start) + SPAN_DASH + lines.get(end));
            shownLines += end - start + 1;
            start = end + 1;
        }
        final String shown = String.join(", ", spans);
        final int left = lines.size() - shownLines;
        return left == 0 ? shown : messages.get(MessageKey.NAMES_STYLE_IMPORT_MORE_LINES, shown, left);
    }
}
