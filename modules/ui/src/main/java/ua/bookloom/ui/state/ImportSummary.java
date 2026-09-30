package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Reads a glossary back after an import and words what a glossary import did: the rows taken, then the malformed and the refused line numbers, if any. */
@Slf4j
final class ImportSummary {

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
        append(summary, MessageKey.NAMES_STYLE_IMPORT_REFUSED, report.refusedLines());
        return summary.toString();
    }

    GlossaryNotice exported(final Path written) {
        return new GlossaryNotice(
                GlossaryNotice.Level.INFO, messages.get(MessageKey.NAMES_STYLE_EXPORT_DONE, written.getFileName()));
    }

    private void append(final StringBuilder summary, final MessageKey key, final List<Integer> lines) {
        if (!lines.isEmpty()) {
            final String joined =
                    String.join(", ", lines.stream().map(String::valueOf).toList());
            summary.append(' ').append(messages.get(key, joined, lines.size()));
        }
    }
}
