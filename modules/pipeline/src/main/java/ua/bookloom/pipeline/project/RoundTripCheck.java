package ua.bookloom.pipeline.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.RoundTripReport;

/**
 * Writes a freshly opened copy of the source with no edits and compares it with the source, so the writer is proven
 * on the person's actual book before hours of translation depend on it. It always opens the source again, because a
 * written book mutates the tree the registry holds for the opened one (design.md D2).
 */
@Slf4j
@RequiredArgsConstructor
final class RoundTripCheck {

    private static final String UNDETERMINED_LANGUAGE = "und";

    private final BookInspector inspector;
    private final DocumentPort documents;

    /**
     * Runs the check.
     *
     * @param projectId the project the check is for, used only in log lines
     * @param source the book file to open afresh; never modified
     * @return the report, or the failed result of the first open, profile or write that did not succeed
     */
    Result<RoundTripReport> run(final String projectId, final Path source) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        log.debug("round-trip check project={} source={}", projectId, source);
        final Path directory = createDirectory();
        Document original = null;
        Document copy = null;
        try {
            final Result<Document> opened = documents.open(source);
            original = opened.data();
            if (original == null) {
                return Result.err(Objects.requireNonNull(opened.error(), "error"));
            }
            final Path written = directory.resolve(source.getFileName().toString());
            final Result<Path> writeResult = write(original, written);
            if (writeResult.isErr()) {
                return Result.err(Objects.requireNonNull(writeResult.error(), "error"));
            }
            final Result<Document> reopened = documents.open(written);
            copy = reopened.data();
            if (copy == null) {
                return Result.err(Objects.requireNonNull(reopened.error(), "error"));
            }
            return compare(projectId, original, copy);
        } finally {
            close(original);
            close(copy);
            delete(directory);
        }
    }

    private Result<Path> write(final Document original, final Path written) {
        final String declared = original.declaredLang();
        final String target = declared == null ? UNDETERMINED_LANGUAGE : declared;
        log.debug("round-trip writes to={} sourceLanguage={} targetLanguage={}", written, declared, target);
        return documents.write(original, written, declared, target);
    }

    private Result<RoundTripReport> compare(final String projectId, final Document original, final Document copy) {
        final Result<BookProfile> originalProfile = inspector.profile(original);
        if (originalProfile.isErr()) {
            return Result.err(Objects.requireNonNull(originalProfile.error(), "error"));
        }
        final Result<BookProfile> copyProfile = inspector.profile(copy);
        if (copyProfile.isErr()) {
            return Result.err(Objects.requireNonNull(copyProfile.error(), "error"));
        }
        final Set<String> copyIds =
                Objects.requireNonNull(copyProfile.data(), "profile").resourceIds();
        final List<String> missing = Objects.requireNonNull(originalProfile.data(), "profile").resourceIds().stream()
                .filter(id -> !copyIds.contains(id))
                .sorted()
                .toList();
        final int sourceBody = bodyCount(original);
        final int copyBody = bodyCount(copy);
        final RoundTripReport report =
                new RoundTripReport(sameStructure(original, copy), missing.isEmpty(), missing, sourceBody, copyBody);
        log.debug("round-trip segments source={} copy={}", sourceBody, copyBody);
        log.info(
                "round-trip check project={} structurePreserved={} idsPreserved={} missingIds={}",
                projectId,
                report.structurePreserved(),
                report.idsPreserved(),
                missing.size());
        return Result.ok(report);
    }

    private static boolean sameStructure(final Document original, final Document copy) {
        final List<Segment> before = segmentsOf(original);
        final List<Segment> after = segmentsOf(copy);
        if (before.size() != after.size()) {
            return false;
        }
        for (int index = 0; index < before.size(); index++) {
            final Segment left = before.get(index);
            final Segment right = after.get(index);
            if (!left.id().equals(right.id())
                    || !left.masked().equals(right.masked())
                    || !left.placeholders().equals(right.placeholders())) {
                log.debug("round-trip segment differs at index={} id={}", index, left.id());
                return false;
            }
        }
        return true;
    }

    private static List<Segment> segmentsOf(final Document document) {
        final List<Segment> all = new ArrayList<>();
        document.units().forEach(unit -> all.addAll(unit.segments()));
        return all;
    }

    private static int bodyCount(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static Path createDirectory() {
        try {
            return Files.createTempDirectory("bookloom-roundtrip-");
        } catch (IOException cause) {
            throw new IllegalStateException("cannot create the round-trip directory", cause);
        }
    }

    private void close(final @Nullable Document document) {
        if (document != null) {
            documents.close(document);
        }
    }

    private static void delete(final Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(RoundTripCheck::deleteQuietly);
        } catch (IOException cause) {
            log.warn("round-trip directory {} could not be removed", directory, cause);
        }
    }

    private static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("round-trip file {} could not be removed", path, cause);
        }
    }
}
