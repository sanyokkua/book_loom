package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.glossary.GlossaryCsv;

/**
 * Reads a book that was already exported back against its source, with no model and no project: the exported book is
 * opened as a book of its own and its segments are paired with the source's by id, which is how an export keeps them.
 * The local-only audit tool and the detector-recall suite share it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExportedBooks {

    private static final String PROJECT = "audit";

    /**
     * Opens one book file.
     *
     * @param documents the non-null document port
     * @param file the non-null book file
     * @return the opened book
     * @throws IllegalStateException when the file cannot be opened, with the error's title
     */
    public static Document open(final DocumentPort documents, final Path file) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(file, "file");
        final Result<Document> opened = documents.open(file);
        final AppError error = opened.error();
        if (error != null) {
            throw new IllegalStateException("cannot open " + file.getFileName() + ": " + error.title(), error.cause());
        }
        return Objects.requireNonNull(opened.data(), "document");
    }

    /**
     * Each source segment the exported book also holds, as an accepted, unreviewed record whose target is the written
     * text's display form. A single-file book's unit id is its file name and seeds every segment id, so an export saved
     * under another name has its unit ids read as the source's, unit by unit in order, before the ids are paired.
     *
     * @param original the non-null source book
     * @param written the non-null exported book
     * @return the records in the source's order; never null, empty when no id is shared
     */
    public static List<SegmentRecord> recordsOf(final Document original, final Document written) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(written, "written");
        final Map<String, String> renames = unitRenames(original, written);
        final Map<String, Segment> writtenById = new HashMap<>();
        written.units()
                .forEach(unit ->
                        unit.segments().forEach(segment -> writtenById.put(renamed(segment.id(), renames), segment)));
        final List<SegmentRecord> records = new ArrayList<>();
        for (final Unit unit : original.units()) {
            for (final Segment segment : unit.segments()) {
                final Segment target = writtenById.get(segment.id());
                if (target != null) {
                    records.add(record(segment, DisplayText.of(target.masked())));
                }
            }
        }
        log.debug(
                "Exported book aligned {} of {} id(s), renamed units {}",
                records.size(),
                sourceCount(original),
                renames.size());
        return records;
    }

    private static Map<String, String> unitRenames(final Document original, final Document written) {
        final Map<String, String> renames = new LinkedHashMap<>();
        final int shared = Math.min(original.units().size(), written.units().size());
        for (int i = 0; i < shared; i++) {
            final String from = written.units().get(i).id();
            final String to = original.units().get(i).id();
            if (!from.equals(to)) {
                renames.put(from, to);
            }
        }
        return renames;
    }

    private static String renamed(final String id, final Map<String, String> renames) {
        String result = id;
        for (final Map.Entry<String, String> rename : renames.entrySet()) {
            result = result.replace(rename.getKey(), rename.getValue());
        }
        return result;
    }

    /**
     * The glossary of an export's CSV side file, every entry unlocked.
     *
     * @param csv the CSV file, or {@code null} for none
     * @return the entries in file order; never null, empty when there is no file
     */
    public static List<GlossaryEntry> glossary(@Nullable final Path csv) {
        if (csv == null) {
            return List.of();
        }
        final List<GlossaryEntry> entries = new ArrayList<>();
        try {
            for (final GlossaryCsv.Row row : GlossaryCsv.parse(Files.readString(csv, StandardCharsets.UTF_8))
                    .rows()) {
                entries.add(new GlossaryEntry(
                        PROJECT + "-" + entries.size(),
                        PROJECT,
                        row.term(),
                        row.target(),
                        row.type(),
                        row.gender(),
                        false));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(entries);
    }

    /**
     * The number of segments a book holds.
     *
     * @param document the non-null book
     * @return the count over every unit
     */
    public static int sourceCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static SegmentRecord record(final Segment segment, final String text) {
        return new SegmentRecord(
                PROJECT,
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                SegmentStatus.ACCEPTED,
                text,
                text,
                null,
                null,
                1.0,
                null,
                List.<QaFinding>of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }
}
