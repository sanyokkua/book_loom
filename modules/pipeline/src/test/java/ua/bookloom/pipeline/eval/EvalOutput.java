package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes a finished eval report twice: to {@code build/reports/promptEval} where a matrix reads it (overwritten by the
 * next run) and to {@code eval-history/<run>/} at the repository root where runs are kept to be compared. The JSON
 * always carries the run's {@link EvalProvenance}. No book text goes here beyond what the report already holds.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalOutput {

    static final String HISTORY_ENV = "BOOKLOOM_EVAL_HISTORY_DIR";
    static final String OFF = "off";
    private static final String HISTORY_DEFAULT = "eval-history";

    /** Writes a report to the build reports directory and the history as the environment says. */
    static void write(final EvalProvenance provenance, final String name, final String table, final String reportJson)
            throws IOException {
        write(
                provenance,
                name,
                table,
                reportJson,
                Path.of("build", "reports", "promptEval"),
                historyRoot(System.getenv(), EvalProvenance.repoRoot()));
    }

    static void write(
            final EvalProvenance provenance,
            final String name,
            final String table,
            final String reportJson,
            final Path reportsDir,
            final Optional<Path> history)
            throws IOException {
        Objects.requireNonNull(provenance, "provenance");
        final String json = provenance.within(reportJson) + "\n";
        final String text = table + "\n";
        put(reportsDir, name, text, json);
        if (history.isPresent()) {
            final Path run = freshDir(history.get(), provenance.runName());
            put(run, "report", text, json);
            log.info("Eval history {}", run.toAbsolutePath());
        } else {
            log.debug("Eval history is off");
        }
    }

    /** The history directory the environment asks for; empty when it is {@code off}. */
    static Optional<Path> historyRoot(final Map<String, String> env, final Path repoRoot) {
        final String asked = env.getOrDefault(HISTORY_ENV, "").strip();
        if (OFF.equalsIgnoreCase(asked)) {
            return Optional.empty();
        }
        return Optional.of(asked.isEmpty() ? repoRoot.resolve(HISTORY_DEFAULT) : Path.of(asked));
    }

    private static void put(final Path dir, final String name, final String text, final String json)
            throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".txt"), text, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(name + ".json"), json, StandardCharsets.UTF_8);
    }

    private static Path freshDir(final Path root, final String runName) throws IOException {
        Files.createDirectories(root);
        Path dir = root.resolve(runName);
        for (int n = 2; Files.exists(dir); n++) {
            dir = root.resolve(runName + "-" + n);
        }
        return Files.createDirectories(dir);
    }
}
