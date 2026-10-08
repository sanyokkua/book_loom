package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The stage suites against a real local model: the model-call stages the other suites never reach, each run through its
 * production class (see {@link StageSuites}). Local-only: {@code BOOKLOOM_EVAL_SUITE=prescan|terms|setup|consistency|retry
 * ./gradlew :pipeline:promptEval} with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER} and
 * {@code BOOKLOOM_EVAL_MODEL}, or {@code scripts/eval-matrix.sh --suite <name>}. It measures and asserts no floor; the
 * report lands in {@code build/reports/promptEval/<model>-<suite>.json} and {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "prescan|terms|setup|consistency|retry")
class StageEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void stageEval_realModel_reportsEveryCase() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final String suite = Objects.requireNonNull(System.getenv("BOOKLOOM_EVAL_SUITE"));

        final StageReport report = StageSuites.run(suite, model, PromptEvalTest.chatModel(model), workDir);

        write(report);
        assertThat(report.rows()).as(report.table()).isNotEmpty();
    }

    private static void write(final StageReport report) throws IOException {
        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + report.suite();
        final Path file = Path.of("build", "reports", "promptEval", name + ".txt");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, report.table() + "\n", StandardCharsets.UTF_8);
        Files.writeString(file.resolveSibling(name + ".json"), report.json() + "\n", StandardCharsets.UTF_8);
        log.info("Stage eval report {}\n{}", file.toAbsolutePath(), report.table());
    }
}
