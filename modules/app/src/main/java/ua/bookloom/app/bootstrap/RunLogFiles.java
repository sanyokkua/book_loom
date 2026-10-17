package ua.bookloom.app.bootstrap;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.util.paths.AppPaths;

/**
 * Names this launch's run log and removes the oldest ones. Plain {@code java.nio}, no logger: it runs on the bootstrap
 * path before logging is configured, so failures are returned for {@link LoggingBootstrap} to report.
 *
 * <p>The stamp is the launch time, so file names sort oldest first and pruning needs no file clock.
 */
// Checkstyle parses source text before Lombok creates the private constructor (false positive).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunLogFiles {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String SUFFIX = ".log";

    /**
     * Deletes the oldest run logs so that, with the one about to be created, no more than
     * {@link AppPaths#RUN_LOGS_KEPT} remain, and returns the new file's path.
     *
     * @param logDir the resolved, existing log directory
     * @param clock the launch time source
     * @return this launch's run log path (its appender creates the file) and the names that could not be removed,
     *     for the caller to report once logging is up
     */
    static PreparedRunLog prepareNext(Path logDir, Clock clock) {
        final Path next = logDir.resolve(AppPaths.RUN_LOG_PREFIX + STAMP.format(LocalDateTime.now(clock)) + SUFFIX);
        final List<String> failed = new ArrayList<>();
        final List<Path> existing = existingRunLogs(logDir, failed);
        existing.remove(next);
        final int surplus = existing.size() - (AppPaths.RUN_LOGS_KEPT - 1);
        existing.stream().limit(Math.max(0, surplus)).forEach(file -> delete(file, failed));
        return new PreparedRunLog(next, List.copyOf(failed));
    }

    private static List<Path> existingRunLogs(Path logDir, List<String> failed) {
        final List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(logDir, AppPaths.RUN_LOG_PREFIX + "*" + SUFFIX)) {
            files.forEach(found::add);
        } catch (IOException e) {
            // Housekeeping: a directory that cannot be listed costs old files staying, not the launch.
            failed.add(logDir.getFileName().toString());
            return found;
        }
        Collections.sort(found);
        return found;
    }

    private static void delete(Path file, List<String> failed) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            failed.add(file.getFileName().toString());
        }
    }
}
