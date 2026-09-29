package ua.bookloom.pipeline.export;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/**
 * The report side file: the counts the export reports, every flagged segment named by its locator with its findings,
 * and the consistency pass's notes — what a person reads to decide where to look before sharing the book.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QualityReport {

    /**
     * Builds the report of one export.
     *
     * @param title the non-null written book's file name
     * @param counts the non-null counts the export reports
     * @param book the non-null book as written, whose document order and locators the flagged list follows
     * @param records the non-null stored records
     * @param keptKinds the non-null auxiliary kinds the brief keeps as source, whose records are never listed flagged
     * @param pass what the consistency pass changed, or null when it was not run
     * @return the report's Markdown text
     */
    static String of(
            final String title,
            final ExportCounts counts,
            final Document book,
            final List<SegmentRecord> records,
            final Set<SegmentKind> keptKinds,
            @Nullable final ConsistencyReport pass) {
        Objects.requireNonNull(counts, "counts");
        final String text = "# Export report: " + title + "\n\n"
                + counts(counts)
                + "\n## Flagged segments\n\n"
                + flagged(book, records, keptKinds)
                + "\n## Consistency notes\n\n"
                + notes(pass);
        log.debug("Quality report built title={} length={} consistencyPass={}", title, text.length(), pass != null);
        return text;
    }

    private static String counts(final ExportCounts counts) {
        return "## Counts\n\n"
                + "- Written with a translation: " + counts.written() + "\n"
                + "- Pending, written in the source language: " + counts.pending() + "\n"
                + "- Kept as source by choice: " + counts.sourceKept() + "\n"
                + "- Flagged, written with the machine translation: " + counts.flaggedWritten() + "\n"
                + "- Accepted without review: " + counts.autoAccepted() + "\n"
                + "- Reviewed by you: " + counts.reviewed() + "\n"
                + "- Body segments re-opened and verified: " + counts.bodySegments() + "\n";
    }

    private static String flagged(
            final Document book, final List<SegmentRecord> records, final Set<SegmentKind> keptKinds) {
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (a, b) -> a));
        final String lines = SegmentLocators.of(book).entrySet().stream()
                .map(entry -> flaggedLine(byId.get(entry.getKey()), entry.getValue(), keptKinds))
                .flatMap(Optional::stream)
                .collect(Collectors.joining());
        return lines.isEmpty() ? "None.\n" : lines;
    }

    private static Optional<String> flaggedLine(
            @Nullable final SegmentRecord record, final SegmentLocator locator, final Set<SegmentKind> keptKinds) {
        if (record == null || record.status() != SegmentStatus.FLAGGED || record.isKeptAsSource(keptKinds)) {
            return Optional.empty();
        }
        final String findings = record.findings().isEmpty()
                ? "no finding recorded"
                : record.findings().stream().map(QualityReport::finding).collect(Collectors.joining("; "));
        return Optional.of("- " + locator.text() + ": " + findings + "\n");
    }

    private static String finding(final QaFinding finding) {
        return finding.kind() + " (" + finding.severity().name().toLowerCase(Locale.ROOT) + ") — " + finding.note();
    }

    private static String notes(@Nullable final ConsistencyReport pass) {
        if (pass == null) {
            return "The consistency pass was not run.\n";
        }
        final List<String> notes = pass.notes();
        return notes.isEmpty()
                ? "The consistency pass changed nothing.\n"
                : notes.stream().map(note -> "- " + note + "\n").collect(Collectors.joining());
    }
}
