package ua.bookloom.pipeline.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** What the project service reads off a stored project and its segments: pending records, change and decided counts. */
@Slf4j
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoredBooks {

    // The content hash is the document's own (a zipped FictionBook hashes its unzipped member, not the archive), so the
    // file is compared by the hash the reader would store for it now.
    static boolean isUnchanged(final Project stored, final Path source, final DocumentPort documents) {
        final Result<Document> opened = documents.open(source);
        final Document document = opened.data();
        if (document == null) {
            log.debug("project={} source unreadable: treated as changed", stored.id());
            return false;
        }
        try {
            return document.contentHash().equals(stored.contentHash());
        } finally {
            documents.close(document);
        }
    }

    static int countDecided(final List<SegmentRecord> records) {
        return (int) records.stream()
                .filter(record -> record.status() != SegmentStatus.PENDING)
                .count();
    }

    static Project newProject(
            final String id,
            final Path source,
            final Document document,
            final BookBrief brief,
            final String title,
            final Instant now) {
        final String name = ProjectNames.automatic(title, brief.sourceLanguage(), brief.targetLanguage());
        return new Project(
                id, source, document.format(), document.contentHash(), brief, name, NameOrigin.AUTO, title, now, now);
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
