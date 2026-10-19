package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The stage suites against a real local model: the model-call stages the other suites never reach, each run through its
 * production class (see {@link StageSuites}). Local-only: {@code BOOKLOOM_EVAL_SUITE=prescan|terms|setup|consistency|retry|replay
 * ./gradlew :pipeline:promptEval} with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER} and
 * {@code BOOKLOOM_EVAL_MODEL}, or {@code scripts/eval-matrix.sh --suite <name>}. It measures and asserts no floor; the
 * report lands in {@code build/reports/promptEval/<model>-<suite>.json} and {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "prescan|terms|setup|consistency|retry|replay")
class StageEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void stageEval_realModel_reportsEveryCase() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final String suite = Objects.requireNonNull(System.getenv("BOOKLOOM_EVAL_SUITE"));
        final Optional<ReplayConfig> replay = ReplayConfig.fromEnv(System.getenv());
        Assumptions.assumeTrue(!"replay".equals(suite) || replay.isPresent(), "replay needs " + ReplayConfig.LOG_ENV);

        final StageReport report = StageSuites.run(suite, model, PromptEvalTest.chatModel(model), workDir);

        write(report, replay.map(config -> config.mode().word()).orElse(""));
        assertThat(report.rows()).as(report.table()).isNotEmpty();
    }

    private static void write(final StageReport report, final String label) throws IOException {
        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + report.suite()
                + (label.isEmpty() ? "" : "-" + label);
        EvalOutput.write(
                EvalProvenance.capture(report.suite(), report.model(), label), name, report.table(), report.json());
        log.info("Stage eval report {}\n{}", name, report.table());
    }
}
