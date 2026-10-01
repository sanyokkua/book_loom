package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Words what an export's report says beyond its counts, so the screen's checks and the completion dialog state the
 * side files and the consistency pass in the same words.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExportReportLines {

    /**
     * The file names of the side files written beside the book.
     *
     * @param report the finished export's report
     * @return one name per written side file, in the order they were written; empty when none was
     */
    public static List<String> sideFileNames(final ExportReport report) {
        Objects.requireNonNull(report, "report");
        return report.sideFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
    }

    /**
     * What the consistency pass did, in the words shown to the person.
     *
     * @param messages the catalogue the lines are worded from
     * @param summary the pass's summary from the report
     * @return the lines to show; empty when the pass was off, otherwise the count of segments it adjusted and, without
     *     a model, the note that the gender step was skipped, or that it ran and changed nothing
     */
    public static List<String> consistency(final Messages messages, final ConsistencySummary summary) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(summary, "summary");
        final boolean isAdjusted = summary.adjusted() > 0;
        return switch (summary.status()) {
            case NOT_RUN -> List.of();
            case RAN ->
                List.of(
                        isAdjusted
                                ? messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_ADJUSTED, summary.adjusted())
                                : messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NOTHING));
            case RAN_WITHOUT_MODEL ->
                isAdjusted
                        ? List.of(
                                messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_ADJUSTED, summary.adjusted()),
                                messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NO_MODEL))
                        : List.of(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NO_MODEL));
        };
    }

    /**
     * The segments the export wrote in their source because their translation broke the formatting.
     *
     * @param messages the catalogue the line is worded from
     * @param report the finished export's report
     * @return the line naming their count and locators, or empty when there were none
     */
    public static Optional<String> sourceFallbacks(final Messages messages, final ExportReport report) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(report, "report");
        if (report.sourceFallbacks().isEmpty()) {
            return Optional.empty();
        }
        final String locators =
                report.sourceFallbacks().stream().map(SourceFallback::locator).collect(Collectors.joining(", "));
        return Optional.of(messages.get(
                MessageKey.EXPORT_SOURCE_FALLBACKS, report.sourceFallbacks().size(), locators));
    }
}
