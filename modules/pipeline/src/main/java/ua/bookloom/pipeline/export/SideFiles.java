package ua.bookloom.pipeline.export;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.glossary.GlossaryCsv;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/**
 * The side files an export writes beside the book, each named through {@link SideFile#pathBeside}. Their contents are
 * built in memory before the book is moved into place, so a failed build writes nothing; each then lands through a
 * temporary file and a move, replacing an existing file only with permission.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SideFiles {

    /**
     * Everything a side file is built from.
     *
     * @param destination the book's destination, which the side files are named after
     * @param targets the book as it is written, with each written target's masked form
     * @param records the project's stored records
     * @param keptKinds the auxiliary kinds the brief keeps as source
     * @param counts the counts the export reports
     * @param pass what the consistency pass changed, or null when it was not run
     * @param glossary the project's glossary entries
     */
    record Sources(
            Path destination,
            EffectiveTargets targets,
            List<SegmentRecord> records,
            Set<SegmentKind> keptKinds,
            ExportCounts counts,
            @Nullable ConsistencyReport pass,
            List<GlossaryEntry> glossary) {

        /** Rejects missing parts and copies the collections. */
        Sources {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(targets, "targets");
            Objects.requireNonNull(counts, "counts");
            records = List.copyOf(records);
            keptKinds = Set.copyOf(keptKinds);
            glossary = List.copyOf(glossary);
        }
    }

    /**
     * One side file's path and text, ready to be written.
     *
     * @param path where the file lands
     * @param text its whole content
     */
    record Content(Path path, String text) {

        /** Rejects a missing path or text. */
        Content {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Builds the chosen side files in memory.
     *
     * @param chosen the non-null side files the person chose
     * @param sources the non-null parts they are built from
     * @return the contents in {@link SideFile} order; empty when none is chosen
     */
    static List<Content> build(final Set<SideFile> chosen, final Sources sources) {
        Objects.requireNonNull(chosen, "chosen");
        Objects.requireNonNull(sources, "sources");
        final String title = Objects.requireNonNull(sources.destination().getFileName(), "file name")
                .toString();
        final List<Content> contents = Arrays.stream(SideFile.values())
                .filter(chosen::contains)
                .map(kind -> new Content(
                        kind.pathBeside(
                                sources.destination(),
                                sources.targets().document().format()),
                        text(kind, title, sources)))
                .toList();
        log.debug(
                "Side files built chosen={} paths={}",
                chosen,
                contents.stream().map(Content::path).toList());
        return contents;
    }

    private static String text(final SideFile kind, final String title, final Sources sources) {
        return switch (kind) {
            case GLOSSARY_CSV -> GlossaryCsv.format(sources.glossary());
            case BILINGUAL_HTML -> BilingualHtml.of(title, sources.targets());
            case QUALITY_REPORT ->
                QualityReport.of(
                        title,
                        sources.counts(),
                        sources.targets().document(),
                        sources.records(),
                        sources.keptKinds(),
                        sources.pass());
        };
    }

    /**
     * Writes built side files beside the book, each through a temporary file and a move.
     *
     * @param contents the non-null side files to write, in order
     * @param overwrite whether an existing file may be replaced
     * @param moves the non-null move seam
     * @param cancellationRequested read before each file; once true, no further file is written
     * @return the paths written; {@code cancelled} once a cancel is seen; {@code validation} when a file's temporary
     *     path is a symbolic link; {@code internal} naming the first file that could not be put in place, whose
     *     temporary file is removed
     */
    static Result<List<Path>> write(
            final List<Content> contents,
            final boolean overwrite,
            final ExportMoveOperation moves,
            final BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(moves, "moves");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        final List<Path> written = new ArrayList<>(contents.size());
        for (final Content content : contents) {
            if (cancellationRequested.getAsBoolean()) {
                log.debug("Side files stopped by a cancel before {} written={}", content.path(), written);
                return Result.err(BookExporter.cancelledError());
            }
            final Result<Path> one = writeOne(content, overwrite, moves);
            if (one.isErr()) {
                return Result.err(Objects.requireNonNull(one.error(), "error"));
            }
            written.add(content.path());
        }
        log.debug("Side files written paths={}", written);
        return Result.ok(List.copyOf(written));
    }

    private static Result<Path> writeOne(
            final Content content, final boolean overwrite, final ExportMoveOperation moves) {
        final Path fileName = Objects.requireNonNull(content.path().getFileName(), "file name");
        final Path temporary = content.path().resolveSibling("." + fileName);
        log.debug("Writing side file {} through {} overwrite={}", content.path(), temporary, overwrite);
        final boolean linked = Files.isSymbolicLink(temporary);
        log.debug("Side file temporary {} is a symbolic link: {}", temporary, linked);
        if (linked) {
            log.warn("Refused side file because temporary path {} is a symbolic link", temporary);
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "This export path is unsafe",
                    "The temporary path for " + fileName + " is a link to another file, so it was not written."));
        }
        try {
            Files.writeString(temporary, content.text(), StandardCharsets.UTF_8);
            Publication.move(moves, temporary, content.path(), overwrite);
            return Result.ok(content.path());
        } catch (Throwable cause) {
            deleteTemporary(temporary);
            log.error("Failed to write side file {}", content.path(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "A side file could not be written",
                    "The book was written, but " + fileName + " could not be saved beside it.",
                    null,
                    cause));
        }
    }

    private static void deleteTemporary(final Path temporary) {
        try {
            log.debug("Side file temporary {} deleted: {}", temporary, Files.deleteIfExists(temporary));
        } catch (Throwable cause) {
            log.warn("Could not delete side file temporary {}", temporary, cause);
        }
    }
}
