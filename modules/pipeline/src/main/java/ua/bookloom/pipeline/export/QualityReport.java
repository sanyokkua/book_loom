package ua.bookloom.pipeline.export;

import java.time.Duration;
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
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.RunRecord;
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

    private static final String NOT_RECORDED = "not recorded";
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long MINUTES_PER_HOUR = 60;
    private static final long SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

    /**
     * Builds the report of one export.
     *
     * @param title the non-null written book's file name
     * @param counts the non-null counts the export reports
     * @param book the non-null book as written, whose document order and locators the flagged list follows
     * @param records the non-null stored records
     * @param keptKinds the non-null auxiliary kinds the brief keeps as source, whose records are never listed flagged
     * @param pass what the consistency pass changed, or null when it was not run
     * @param suspicious the non-null accepted segments the final audit doubts, listed with the checks that fired
     * @param policy the non-null way each segment without an accepted target was written
     * @param lastRun the project's last translation run, or null when it never ran
     * @return the report's Markdown text
     */
    static String of(
            final String title,
            final ExportCounts counts,
            final Document book,
            final List<SegmentRecord> records,
            final Set<SegmentKind> keptKinds,
            @Nullable final ConsistencyReport pass,
            final List<SuspiciousSegment> suspicious,
            final ExportPolicy policy,
            @Nullable final RunRecord lastRun) {
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(policy, "policy");
        final String text = "# Export report: " + title + "\n\n"
                + counts(counts)
                + "\n"
                + lastRun(lastRun)
                + "\n"
                + policy.text()
                + "\n## Flagged segments\n\n"
                + flagged(book, records, keptKinds)
                + "\n## Suspicious accepted segments\n\n"
                + suspicious(suspicious)
                + "\n## Consistency notes\n\n"
                + ConsistencySection.of(pass);
        log.debug("Quality report built title={} length={} consistencyPass={}", title, text.length(), pass != null);
        return text;
    }

    // The run's length is its start to its end, so a pause counts: the report says "last run" and does not claim more.
    private static String lastRun(@Nullable final RunRecord run) {
        final String time = run == null || run.endedAt() == null
                ? NOT_RECORDED
                : clock(Duration.between(run.startedAt(), run.endedAt()));
        final String model = run == null || run.model() == null ? NOT_RECORDED : run.model();
        return "## Last run\n\n- Model: " + model + "\n- Run time: " + time + "\n";
    }

    private static String clock(final Duration length) {
        final long seconds = length.toSeconds();
        return String.format(
                Locale.ROOT,
                "%d:%02d:%02d",
                seconds / SECONDS_PER_HOUR,
                seconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR,
                seconds % SECONDS_PER_MINUTE);
    }

    private static String counts(final ExportCounts counts) {
        return "## Counts\n\n"
                + "- Written with a translation: " + counts.written() + "\n"
                + "- Pending, written in the source language: " + counts.pending() + "\n"
                + "- Kept as source by choice: " + counts.sourceKept() + "\n"
                + "- Kept as is, nothing to translate (numbers, symbols): " + counts.keptVerbatim() + "\n"
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

    private static String suspicious(final List<SuspiciousSegment> suspicious) {
        return suspicious.isEmpty()
                ? "None.\n"
                : suspicious.stream()
                        .map(segment -> "- " + segment.locator() + ": " + String.join(", ", segment.checks()) + "\n")
                        .collect(Collectors.joining());
    }
}
