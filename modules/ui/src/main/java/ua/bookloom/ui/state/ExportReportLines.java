package ua.bookloom.ui.state;

import static ua.bookloom.util.contract.ArgNames.MESSAGES;
import static ua.bookloom.util.contract.ArgNames.REPORT;
import static ua.bookloom.util.contract.ArgNames.SUMMARY;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Words what an export's report says beyond its counts, so the screen's checks and the completion dialog state the
 * side files and the held-back segments in the same words.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExportReportLines {

    private static final int MAX_NAMED_CHARACTERS = 5;

    /**
     * The file names of the side files written beside the book.
     *
     * @param report the finished export's report
     * @return one name per written side file, in the order they were written; empty when none was
     */
    public static List<String> sideFileNames(final ExportReport report) {
        Objects.requireNonNull(report, REPORT);
        return report.sideFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
    }

    /**
     * The warning that segments wait on characters whose gender is unknown, which names them and their segments. A
     * character whose gender is only suggested is not named here: the person has to confirm it, not set it
     * ({@link #suggestedGender}).
     *
     * @param messages the catalogue the line is worded from
     * @param summary the proofread summary from the report
     * @return the line, or empty when no segment waits on a character whose gender is missing
     */
    public static Optional<String> awaitingGender(final Messages messages, final ProofreadSummary summary) {
        Objects.requireNonNull(messages, MESSAGES);
        Objects.requireNonNull(summary, SUMMARY);
        return waits(messages, summary, false, MessageKey.EXPORT_AWAITING_GENDER, summary.unknownGenderSegments());
    }

    /**
     * The warning that segments wait on a gender the glossary only suggests, which the person confirms in Names &amp;
     * style.
     *
     * @param messages the catalogue the line is worded from
     * @param summary the proofread summary from the report
     * @return the line, or empty when no segment waits on a suggested gender
     */
    public static Optional<String> suggestedGender(final Messages messages, final ProofreadSummary summary) {
        Objects.requireNonNull(messages, MESSAGES);
        Objects.requireNonNull(summary, SUMMARY);
        return waits(messages, summary, true, MessageKey.EXPORT_SUGGESTED_GENDER, summary.suggestedGenderSegments());
    }

    private static Optional<String> waits(
            final Messages messages,
            final ProofreadSummary summary,
            final boolean suggested,
            final MessageKey line,
            final int segments) {
        final List<GenderWait> waits = summary.awaitingGender().stream()
                .filter(wait -> wait.suggested() == suggested)
                .toList();
        if (waits.isEmpty()) {
            return Optional.empty();
        }
        final String named = waits.stream()
                .limit(MAX_NAMED_CHARACTERS)
                .map(wait -> wait.character() + " ×" + wait.segments())
                .collect(Collectors.joining(", "));
        final int unnamed = waits.size() - MAX_NAMED_CHARACTERS;
        final String characters =
                unnamed > 0 ? named + " " + messages.get(MessageKey.EXPORT_AWAITING_GENDER_MORE, unnamed) : named;
        return Optional.of(messages.get(line, segments, characters));
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

    /**
     * The note that points at the report file when it lists segments written as they are.
     *
     * @param messages the catalogue the line is worded from
     * @param report the finished export's report
     * @return the line naming the report file, or empty when no report was written or nothing was written as it is
     */
    public static Optional<String> reportFile(final Messages messages, final ExportReport report) {
        Objects.requireNonNull(messages, MESSAGES);
        Objects.requireNonNull(report, REPORT);
        if (report.sourceFallbacks().isEmpty() && report.unresolvedBlocking().isEmpty()) {
            return Optional.empty();
        }
        return sideFileNames(report).stream()
                .filter(name -> name.endsWith(SideFile.QUALITY_REPORT.suffix()))
                .findFirst()
                .map(name -> messages.get(MessageKey.EXPORT_REPORT_FILE, name));
    }

    private static Optional<String> fallbackLine(
            final Messages messages,
            final ExportReport report,
            final SourceFallback.Reason reason,
            final MessageKey key) {
        Objects.requireNonNull(messages, MESSAGES);
        Objects.requireNonNull(report, REPORT);
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
