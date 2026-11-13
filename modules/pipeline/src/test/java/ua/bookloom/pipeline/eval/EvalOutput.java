package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes a finished eval report twice: to {@code build/reports/promptEval} where a matrix reads it (overwritten by the
 * next run) and to {@code .temporary_context/eval-history/<run>/} in the repository where runs are kept to be compared. The JSON
 * always carries the run's {@link EvalProvenance}. No book text goes here beyond what the report already holds.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalOutput {

    static final String HISTORY_ENV = "BOOKLOOM_EVAL_HISTORY_DIR";
    static final String OFF = "off";
    static final String CASES_FILE = "cases.jsonl";
    private static final String HISTORY_DEFAULT = ".temporary_context/eval-history";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        write(provenance, name, table, reportJson, reportsDir, history, List.of());
    }

    /**
     * As above, and with the history on, the run's cases beside the report: {@code cases.jsonl} and the calls files.
     *
     * @param cases the non-null cases of every repeat; empty for a suite that keeps none
     */
    static void write(
            final EvalProvenance provenance,
            final String name,
            final String table,
            final String reportJson,
            final Path reportsDir,
            final Optional<Path> history,
            final List<CaseRecord.Entry> cases)
            throws IOException {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(cases, "cases");
        final String json = provenance.within(reportJson) + "\n";
        final String text = table + "\n";
        put(reportsDir, name, text, json);
        log.debug("Eval report files name={} dir={} cases={}", name, reportsDir, cases.size());
        if (history.isPresent()) {
            final Path run = freshDir(history.get(), provenance.runName());
            put(run, "report", text, json);
            for (final CaseRecord.Entry entry : cases) {
                writeCase(run, entry.record(), entry.calls());
            }
            log.info(
                    "Eval report written run={} dataset={} k={} folder={}",
                    provenance.runName(),
                    provenance.dataset() == null ? null : provenance.dataset().id(),
                    provenance.repeats().k(),
                    run.toAbsolutePath());
        } else {
            log.info(
                    "Eval report written run={} dataset={} k={} folder={}",
                    provenance.runName(),
                    provenance.dataset() == null ? null : provenance.dataset().id(),
                    provenance.repeats().k(),
                    reportsDir.toAbsolutePath());
        }
    }

    /**
     * Appends one case's record to the run folder's {@code cases.jsonl} and writes its calls to the file the record's
     * {@code replyRef} names beside it.
     *
     * @param runDir the run folder, created when missing
     * @param record the non-null case of one repeat
     * @param calls the case's calls in the order they were sent; never null, empty when the case made none
     */
    static void writeCase(final Path runDir, final CaseRecord record, final List<CaseRecord.Exchange> calls)
            throws IOException {
        Objects.requireNonNull(runDir, "runDir");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(calls, "calls");
        log.debug("Eval case {} repeat={} calls={} into {}", record.caseId(), record.repeat(), calls.size(), runDir);
        final Path callsFile = runDir.resolve(record.replyRef());
        Files.createDirectories(callsFile.getParent());
        Files.writeString(
                runDir.resolve(CASES_FILE),
                record.json() + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        Files.writeString(callsFile, MAPPER.writeValueAsString(calls), StandardCharsets.UTF_8);
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
