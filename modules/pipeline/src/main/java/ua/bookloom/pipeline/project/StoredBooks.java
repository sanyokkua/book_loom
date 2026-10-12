package ua.bookloom.pipeline.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.util.hash.HashUtil;

/** What the project service reads off a stored project and its segments: pending records, change and decided counts. */
@Slf4j
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoredBooks {

    // Every format's content hash is the SHA-256 of the whole file, so the file is compared without opening it.
    static boolean isUnchanged(final Project stored, final Path source) {
        try {
            return HashUtil.sha256Hex(Files.readAllBytes(source)).equals(stored.contentHash());
        } catch (IOException unreadable) {
            log.debug("project={} source unreadable: treated as changed", stored.id());
            return false;
        }
    }

    static int countDecided(final List<SegmentRecord> records) {
        return (int) records.stream()
                .filter(record -> record.status() != SegmentStatus.PENDING)
                .count();
    }

    static AppError nothingToContinue() {
        return AppError.of(
                ErrorCode.validation,
                "There is no earlier translation to continue",
                "This file has no stored project, or its content changed since it was imported; import it again.");
    }

    static List<SegmentRecord> pendingRecords(final String projectId, final Document document) {
        final List<SegmentRecord> records = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                records.add(new SegmentRecord(
                        projectId,
                        segment.id(),
                        unit.id(),
                        segment.order(),
                        segment.kind(),
                        SegmentStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        List.of(),
                        SegmentPath.DRAFT,
                        0,
                        false,
                        null));
            }
        }
        return records;
    }
}
