package ua.bookloom.api.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a project's last export wrote, kept so the Export screen's tiles can read "checked when written" after a
 * restart. A record exists only for an export that was written and checked.
 *
 * @param exportedAt when the file was written
 * @param destination the file the translated book was written to
 * @param written the segments carrying a target that were written
 * @param pending pending segments of translated kinds written as source
 * @param sourceKept segments kept as source by choice
 * @param flaggedWritten flagged segments written with their machine target
 * @param autoAccepted segments accepted without repair
 * @param reviewed segments a person acted on
 * @param verifiedSegments the body segments the consistency check verified
 * @param sideFiles the side files written beside the book
 */
public record ExportRecord(
        Instant exportedAt,
        Path destination,
        int written,
        int pending,
        int sourceKept,
        int flaggedWritten,
        int autoAccepted,
        int reviewed,
        int verifiedSegments,
        List<Path> sideFiles) {

    /** Rejects a missing part or a negative count, and copies {@code sideFiles}. */
    public ExportRecord {
        Objects.requireNonNull(exportedAt, "exportedAt");
        Objects.requireNonNull(destination, "destination");
        sideFiles = List.copyOf(Objects.requireNonNull(sideFiles, "sideFiles"));
        if (written < 0
                || pending < 0
                || sourceKept < 0
                || flaggedWritten < 0
                || autoAccepted < 0
                || reviewed < 0
                || verifiedSegments < 0) {
            throw new IllegalArgumentException("export counts must not be negative");
        }
    }
}
