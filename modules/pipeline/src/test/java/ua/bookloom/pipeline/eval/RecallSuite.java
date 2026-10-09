package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.DocumentPort;

/**
 * The detector-recall suite ({@code BOOKLOOM_EVAL_SUITE=recall}): every book directory under the root — the root
 * itself when it holds a {@code book.json}, else each child directory that does — is read against its source, and the
 * detectors that fired are scored against the book's {@code gold.jsonl}. Each book directory also gets a
 * {@code segments.jsonl} with every aligned segment's id, hash, texts and fired detectors, which is what a reading
 * labels; it holds book text, so the root must live outside the repository.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecallSuite {

    /** The variable naming the root directory. */
    static final String DIR_ENV = "BOOKLOOM_RECALL_DIR";

    /** The file written into each book directory for a reading to label. */
    static final String SEGMENTS = "segments.jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static RecallReport run(final Path root, final DocumentPort documents) {
        Objects.requireNonNull(root, "root");
        final List<Path> dirs = bookDirs(root);
        log.info("Recall suite root={} books={}", root, dirs.size());
        if (dirs.isEmpty()) {
            throw new IllegalArgumentException("no " + RecallBook.FILE + " in " + root + " or its child directories");
        }
        final List<RecallBookRun> runs = dirs.stream()
                .map(RecallBook::load)
                .map(book -> measure(book, documents))
                .toList();
        return new RecallReport(runs);
    }

    static List<Path> bookDirs(final Path root) {
        if (Files.isRegularFile(root.resolve(RecallBook.FILE))) {
            return List.of(root);
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(child -> Files.isRegularFile(child.resolve(RecallBook.FILE)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RecallBookRun measure(final RecallBook book, final DocumentPort documents) {
        final RecallBookRun run = RecallDetectors.run(book, documents);
        writeSegments(book.dir(), run);
        return run;
    }

    private static void writeSegments(final Path dir, final RecallBookRun run) {
        final StringBuilder lines = new StringBuilder();
        run.segments().forEach(segment -> lines.append(line(segment)).append('\n'));
        try {
            Files.writeString(dir.resolve(SEGMENTS), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Recall segments of {} written to {}", run.name(), dir.resolve(SEGMENTS));
    }

    private static String line(final RecallSegment segment) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", segment.id());
        fields.put("hash", segment.hash());
        fields.put("source", segment.source());
        fields.put("target", segment.target());
        fields.put("fired", segment.fired());
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
