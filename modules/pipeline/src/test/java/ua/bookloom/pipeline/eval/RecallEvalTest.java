package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.document.DocumentModule;

/**
 * The detector-recall suite over the owner's exported books, with no model: {@code BOOKLOOM_EVAL_SUITE=recall
 * BOOKLOOM_RECALL_DIR=<dir outside the repository> ./gradlew :pipeline:promptEval} (or
 * {@code scripts/eval-matrix.sh --suite recall}). Skipped when the directory is unset; refused when it lies inside the
 * checkout, since every book directory gets a {@code segments.jsonl} of book text. It measures and asserts no floor;
 * the report lands in {@code build/reports/promptEval/recall.json} and {@code .txt}. The layout and the gold format are
 * in {@code docs/DEVELOPMENT.md#recall-suite}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "recall")
class RecallEvalTest {

    @Test
    void recall_ownerBooks_reportsEveryDetectorAndClass() throws IOException {
        final String dir = System.getenv(RecallSuite.DIR_ENV);
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "set " + RecallSuite.DIR_ENV);
        final Path root = Path.of(dir).toAbsolutePath().normalize();
        assertThat(root.startsWith(EvalProvenance.repoRoot().toAbsolutePath().normalize()))
                .as("%s must lie outside the repository: segments.jsonl holds book text", RecallSuite.DIR_ENV)
                .isFalse();
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);

        final RecallReport report = RecallSuite.run(root, documents);

        EvalOutput.write(
                EvalProvenance.capture(RecallReport.SUITE, "none", ""),
                RecallReport.SUITE,
                report.table(),
                report.json());
        log.info("Recall eval report\n{}", report.table());
        assertThat(report.aligned()).as(report.table()).isPositive();
    }
}
