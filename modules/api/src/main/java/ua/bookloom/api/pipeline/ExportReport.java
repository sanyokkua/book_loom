package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What one export wrote and verified, feeding the Export screen's completion summary.
 *
 * @param destination the file the translated book was written to
 * @param written the segments carrying a target that were written
 * @param pending PENDING segments of translated kinds written as source
 * @param sourceKept segments kept as source by choice
 * @param flaggedWritten FLAGGED segments written with their machine target
 * @param autoAccepted segments accepted without repair
 * @param reviewed segments a person acted on
 * @param sideFiles the side files written beside the book
 * @param verifiedSegments the body segments the consistency check verified
 * @param consistency what the final consistency pass did; {@link ConsistencySummary#NOT_RUN} when it was off
 * @param keptVerbatim segments written as they are because nothing in them needed translating (numbers, symbols,
 *     Roman numerals, a single character); counted in {@code written}, never in {@code sourceKept}
 * @param sourceFallbacks segments written in their source although they had a translation, because it broke their
 *     formatting, in book order; counted in {@code pending}, never in {@code written}
 * @param suspicious accepted segments the final audit doubts as the book was written, in book order, each with the
 *     checks that fired
 * @param unresolvedBlocking where a person finds each segment whose written text still holds a blocking finding
 *     (a mixed-script word, a source-language paragraph, reply residue, a lost name, unbalanced quotes), in book order
 */
public record ExportReport(
        Path destination,
        int written,
        int pending,
        int sourceKept,
        int flaggedWritten,
        int autoAccepted,
        int reviewed,
        List<Path> sideFiles,
        int verifiedSegments,
        ConsistencySummary consistency,
        int keptVerbatim,
        List<SourceFallback> sourceFallbacks,
        List<SuspiciousSegment> suspicious,
        List<String> unresolvedBlocking) {

    /** Rejects a report without its destination, a negative count, or defensively copies {@code sideFiles}. */
    public ExportReport {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sideFiles, "sideFiles");
        Objects.requireNonNull(consistency, "consistency");
        Objects.requireNonNull(sourceFallbacks, "sourceFallbacks");
        Objects.requireNonNull(suspicious, "suspicious");
        Objects.requireNonNull(unresolvedBlocking, "unresolvedBlocking");
        if (written < 0
                || pending < 0
                || sourceKept < 0
                || flaggedWritten < 0
                || autoAccepted < 0
                || reviewed < 0
                || verifiedSegments < 0
                || keptVerbatim < 0) {
            throw new IllegalArgumentException("no count may be negative: " + written + ", " + pending + ", "
                    + sourceKept + ", " + flaggedWritten + ", " + autoAccepted + ", " + reviewed + ", "
                    + verifiedSegments + ", " + keptVerbatim);
        }
        sideFiles = List.copyOf(sideFiles);
        sourceFallbacks = List.copyOf(sourceFallbacks);
        suspicious = List.copyOf(suspicious);
        unresolvedBlocking = List.copyOf(unresolvedBlocking);
    }

    /** A report with no segment holding a blocking finding. */
    public ExportReport(
            final Path destination,
            final int written,
            final int pending,
            final int sourceKept,
            final int flaggedWritten,
            final int autoAccepted,
            final int reviewed,
            final List<Path> sideFiles,
            final int verifiedSegments,
            final ConsistencySummary consistency,
            final int keptVerbatim,
            final List<SourceFallback> sourceFallbacks,
            final List<SuspiciousSegment> suspicious) {
        this(
                destination,
                written,
                pending,
                sourceKept,
                flaggedWritten,
                autoAccepted,
                reviewed,
                sideFiles,
                verifiedSegments,
                consistency,
                keptVerbatim,
                sourceFallbacks,
                suspicious,
                List.of());
    }

    /** A report whose audit found nothing doubtful. */
    public ExportReport(
            final Path destination,
            final int written,
            final int pending,
            final int sourceKept,
            final int flaggedWritten,
            final int autoAccepted,
            final int reviewed,
            final List<Path> sideFiles,
            final int verifiedSegments,
            final ConsistencySummary consistency,
            final int keptVerbatim,
            final List<SourceFallback> sourceFallbacks) {
        this(
                destination,
                written,
                pending,
                sourceKept,
                flaggedWritten,
                autoAccepted,
                reviewed,
                sideFiles,
                verifiedSegments,
                consistency,
                keptVerbatim,
                sourceFallbacks,
                List.of());
    }

    /** A report with no segment written in its source for a broken translation. */
    public ExportReport(
            final Path destination,
            final int written,
            final int pending,
            final int sourceKept,
            final int flaggedWritten,
            final int autoAccepted,
            final int reviewed,
            final List<Path> sideFiles,
            final int verifiedSegments,
            final ConsistencySummary consistency,
            final int keptVerbatim) {
        this(
                destination,
                written,
                pending,
                sourceKept,
                flaggedWritten,
                autoAccepted,
                reviewed,
                sideFiles,
                verifiedSegments,
                consistency,
                keptVerbatim,
                List.of());
    }
}
