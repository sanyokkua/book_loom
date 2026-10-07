package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
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
     * @return the lines to show; empty when the pass was off, otherwise the count of segments it adjusted, the note
     *     that the gender step was skipped without a model or the count of segments that await a character's gender,
     *     or, when none of these applies, that it ran and changed nothing
     */
    public static List<String> consistency(final Messages messages, final ConsistencySummary summary) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(summary, "summary");
        final List<String> lines = new ArrayList<>();
        if (summary.status() == ConsistencySummary.Status.NOT_RUN) {
            return lines;
        }
        if (summary.adjusted() > 0) {
            lines.add(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_ADJUSTED, summary.adjusted()));
        }
        if (summary.neighbourFixes() > 0) {
            lines.add(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NEIGHBOURS, summary.neighbourFixes()));
        }
        if (summary.status() == ConsistencySummary.Status.RAN_WITHOUT_MODEL) {
            lines.add(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NO_MODEL));
        } else if (summary.openGenderDeferrals() > 0) {
            lines.add(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_AWAITING_GENDER, summary.openGenderDeferrals()));
        }
        if (lines.isEmpty()) {
            lines.add(messages.get(MessageKey.EXPORT_CHECK_CONSISTENCY_NOTHING));
        }
        return lines;
    }

    /**
     * The segments the export wrote in their source because their translation broke the formatting.
     *
     * @param messages the catalogue the line is worded from
     * @param report the finished export's report
     * @return the line naming their count and locators, or empty when there were none
     */
    public static Optional<String> sourceFallbacks(final Messages messages, final ExportReport report) {
        return fallbackLine(
                messages, report, SourceFallback.Reason.BROKEN_FORMATTING, MessageKey.EXPORT_SOURCE_FALLBACKS);
    }

    /**
     * The flagged segments the export wrote in their source because no draft ever passed the gates.
     *
     * @param messages the catalogue the line is worded from
     * @param report the finished export's report
     * @return the line naming their count and locators, or empty when there were none
     */
    public static Optional<String> noTargetFallbacks(final Messages messages, final ExportReport report) {
        return fallbackLine(messages, report, SourceFallback.Reason.NO_TARGET, MessageKey.EXPORT_NO_TARGET_FALLBACKS);
    }

    private static Optional<String> fallbackLine(
            final Messages messages,
            final ExportReport report,
            final SourceFallback.Reason reason,
            final MessageKey key) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(report, "report");
        final List<SourceFallback> named = report.sourceFallbacks().stream()
                .filter(fallback -> fallback.reason() == reason)
                .toList();
        if (named.isEmpty()) {
            return Optional.empty();
        }
        final String locators = named.stream().map(SourceFallback::locator).collect(Collectors.joining(", "));
        return Optional.of(messages.get(key, named.size(), locators));
    }
}
