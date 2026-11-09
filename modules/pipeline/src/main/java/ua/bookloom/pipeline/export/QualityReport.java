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
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.SegmentLocators;

/**
 * The report side file: the counts the export reports, every flagged segment named by its locator with its findings,
 * and what the Proofread stage re-read and left waiting — what a person reads to decide where to look before sharing the book.
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
     * @param sources the non-null parts of the export the report reads: its counts, the book as written, the stored
     *     records, the kept auxiliary kinds, what the Proofread stage re-read and left waiting, the
     *     accepted segments the final audit doubts, the policy for segments without an accepted target, and the
     *     project's last run (or null when it never ran)
     * @return the report's Markdown text
     */
    static String of(final String title, final SideFiles.Sources sources) {
        final ExportCounts counts = Objects.requireNonNull(sources.counts(), "counts");
        final ExportPolicy policy = Objects.requireNonNull(sources.policy(), "policy");
        final ProofreadSummary summary = sources.summary();
        final Document book = sources.targets().document();
        final String text = "# Export report: " + title + "\n\n"
                + counts(counts)
                + "\n"
                + lastRun(sources.lastRun())
                + "\n"
                + policy.text()
                + "\n## Flagged segments\n\n"
                + flagged(book, sources.records(), sources.keptKinds())
                + "\n## Suspicious accepted segments\n\n"
                + suspicious(sources.suspicious())
                + "\n## Proofread\n\n"
                + ProofreadSection.of(summary);
        log.debug(
                "Quality report built title={} length={} proofreadReRead={} proofreadWaiting={}",
                title,
                text.length(),
                summary.reRead(),
                summary.waiting());
        return text;
    }

    // The run's length is its start to its end, so a pause counts: the report says "last run" and does not claim more.
    private static String lastRun(@Nullable final RunRecord run) {
        final String time = run == null || run.endedAt() == null
                ? NOT_RECORDED
                : clock(Duration.between(run.startedAt(), run.endedAt()));
        final ModelSelection selection = run == null ? null : run.model();
        final String model = selection == null ? NOT_RECORDED : selection.modelId();
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
