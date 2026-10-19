package ua.bookloom.ui.state;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.util.paths.AppPaths;

/**
 * Writes the diagnostic bundle a person can attach to a report: the detailed log's files and a {@code session.json}
 * naming the build, the machine and the session facts.
 *
 * <p>The bundle holds nothing the detailed log has not already written: its files are copied as they are, and
 * {@code session.json} is built from the same facts as the log's header. When the detailed log is off there are no
 * trace files, so the ordinary {@code bookloom.log} goes in instead, which keeps a bundle from being empty. The file is
 * written where the person chose and nowhere else; nothing is sent anywhere.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class DiagnosticBundle {

    /** The name of the session description inside the bundle. */
    public static final String SESSION_ENTRY = "session.json";

    private static final String ORDINARY_LOG = "bookloom.log";

    /**
     * Writes the bundle.
     *
     * @param target the zip file to write; replaced if it exists
     * @param logDir the log directory whose files go in
     * @param sessionJson the session description, as {@link #sessionJson} builds it
     * @return the number of log files the bundle holds, or an {@code internal} error when the folder could not be read
     *     or the zip could not be written — a partly written zip is removed
     */
    public static Result<Integer> write(final Path target, final Path logDir, final String sessionJson) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(logDir, "logDir");
        Objects.requireNonNull(sessionJson, "sessionJson");
        log.info("diagnostic bundle starting target={} logDir={}", target.getFileName(), logDir);
        try {
            final List<Path> files = logFiles(logDir);
            try (OutputStream out = Files.newOutputStream(target);
                    ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (final Path file : files) {
                    copy(zip, file);
                }
                zip.putNextEntry(new ZipEntry(SESSION_ENTRY));
                zip.write(sessionJson.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            log.info("diagnostic bundle written target={} logFiles={}", target.getFileName(), files.size());
            return Result.ok(files.size());
        } catch (IOException | RuntimeException cause) {
            deleteQuietly(target);
            log.warn("diagnostic bundle could not be written target={}", target.getFileName(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Diagnostic bundle not saved",
                    "The diagnostic bundle could not be written to the chosen file.",
                    null,
                    cause));
        }
    }

    /**
     * Builds {@code session.json}: flat string values, keys in the given order, then the session facts as one object.
     *
     * @param about the build and machine facts (version, OS, JVM, …), in order
     * @param session the session facts recorded so far, in order
     * @return a JSON object as text
     */
    public static String sessionJson(final Map<String, String> about, final Map<String, String> session) {
        Objects.requireNonNull(about, "about");
        Objects.requireNonNull(session, "session");
        final String head = about.entrySet().stream()
                .map(entry -> "  " + quote(entry.getKey()) + ": " + quote(entry.getValue()))
                .collect(Collectors.joining(",\n"));
        final String facts = session.entrySet().stream()
                .map(entry -> "    " + quote(entry.getKey()) + ": " + quote(entry.getValue()))
                .collect(Collectors.joining(",\n"));
        final String sessionBlock = facts.isEmpty() ? "{}" : "{\n" + facts + "\n  }";
        return "{\n" + (head.isEmpty() ? "" : head + ",\n") + "  \"session\": " + sessionBlock + "\n}\n";
    }

    /** The detailed log's files, oldest archive last; the ordinary log when there are none. */
    static List<Path> logFiles(final Path logDir) throws IOException {
        final List<Path> traces = new ArrayList<>();
        try (Stream<Path> listed = Files.list(logDir)) {
            listed.filter(Files::isRegularFile)
                    .filter(file -> nameOf(file).startsWith(AppPaths.TRACE_LOG_PREFIX))
                    .sorted()
                    .forEach(traces::add);
        }
        if (!traces.isEmpty()) {
            log.debug("diagnostic bundle takes {} detailed-log files", traces.size());
            return traces;
        }
        final Path ordinary = logDir.resolve(ORDINARY_LOG);
        log.debug("diagnostic bundle finds no detailed log; the ordinary log exists={}", Files.isRegularFile(ordinary));
        return Files.isRegularFile(ordinary) ? List.of(ordinary) : List.of();
    }

    private static void copy(final ZipOutputStream zip, final Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(nameOf(file)));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static String nameOf(final Path file) {
        final Path name = file.getFileName();
        return name == null ? "" : name.toString();
    }

    private static String quote(final String value) {
        final StringBuilder out = new StringBuilder("\"");
        value.chars().forEach(c -> out.append(escaped((char) c)));
        return out.append('"').toString();
    }

    private static String escaped(final char c) {
        return switch (c) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> c < ' ' ? String.format(Locale.ROOT, "\\u%04x", (int) c) : String.valueOf(c);
        };
    }

    private static void deleteQuietly(final Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanup) {
            log.debug("the partly written bundle could not be removed", cleanup);
        }
    }
}
