package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real-run suite (15e.2): the cases built from the defect classes of the 6 h 17 min run — quotes, mixed script,
 * Russian letters, a first-person narrator, short lines, a batch that asks for {@code terms}, a multi-pair reviewer
 * call and a long one, placeholder and structural repair — sent through the run's own request factory to a real model.
 * Local-only: {@code scripts/eval-matrix.sh --suite realrun}, or
 * {@code BOOKLOOM_EVAL_SUITE=realrun ./gradlew :pipeline:promptEval} with {@code BOOKLOOM_EVAL_URL},
 * {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL}, {@code BOOKLOOM_EVAL_STABILITY} (reviewer repeats) and
 * {@code BOOKLOOM_EVAL_ONLY} (a case-id prefix). It measures and asserts no floor: the thresholds per model class are
 * recorded by 15e.4 from these numbers. The report lands in {@code build/reports/promptEval/<model>-realrun.json}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "realrun")
class RealRunTest {

    @TempDir
    private Path workDir;

    @Test
    void realRun_realModel_reportsEveryKind() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", "gemma4:e4b-mlx");
        final int repeats = Integer.parseInt(System.getenv().getOrDefault("BOOKLOOM_EVAL_STABILITY", "1"));
        final String only = System.getenv().getOrDefault("BOOKLOOM_EVAL_ONLY", "");

        final RealRunReport report =
                new RealRunReport(model, new RealRunRunner(PromptEvalTest.calls(model), repeats, workDir).runAll(only));

        write(report);
        assertThat(report.rows()).as(report.table()).isNotEmpty();
    }

    private static void write(final RealRunReport report) throws IOException {
        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-realrun";
        EvalOutput.write(EvalProvenance.capture("realrun", report.model(), ""), name, report.table(), report.json());
        log.info("Real-run eval report {}\n{}", name, report.table());
    }
}
