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
        int verifiedSegments) {

    /** Rejects a report without its destination, a negative count, or defensively copies {@code sideFiles}. */
    public ExportReport {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sideFiles, "sideFiles");
        if (written < 0
                || pending < 0
                || sourceKept < 0
                || flaggedWritten < 0
                || autoAccepted < 0
                || reviewed < 0
                || verifiedSegments < 0) {
            throw new IllegalArgumentException("no count may be negative: " + written + ", " + pending + ", "
                    + sourceKept + ", " + flaggedWritten + ", " + autoAccepted + ", " + reviewed + ", "
                    + verifiedSegments);
        }
        sideFiles = List.copyOf(sideFiles);
    }
}
