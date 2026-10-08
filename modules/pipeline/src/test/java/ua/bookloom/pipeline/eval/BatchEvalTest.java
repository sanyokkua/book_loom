package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The batch A/B: batches of 4, 8, 12 and 16 consecutive draft cases, English to Ukrainian, through the JSON batch protocol
 * against a real model; it reports per size the id-validity and token-gate rates, the omission and merge
 * rates and the output tokens, in {@code build/reports/promptEval/<model>-batch.json} and {@code .txt}. Local-only:
 * {@code scripts/eval-matrix.sh --suite batch}, or {@code BOOKLOOM_EVAL_SUITE=batch ./gradlew :pipeline:promptEval}
 * with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL} and
 * {@code BOOKLOOM_EVAL_BATCH_SIZES} (a comma list, default {@code 4,8,12,16}). It measures; the batch
 * size is chosen by reading the table, so it asserts no floor.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "batch")
class BatchEvalTest {

    @Test
    void batchEval_realModel_reportsEverySize() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", "gemma4:e4b-mlx");
        final List<Integer> sizes = sizes(System.getenv().getOrDefault("BOOKLOOM_EVAL_BATCH_SIZES", "4,8,12,16"));

        final BatchEvalReport report =
                new BatchEvalReport(model, new BatchEvalRunner(PromptEvalTest.calls(model)).runAll(sizes));

        write(report);
        assertThat(report.cells()).as(report.table()).isNotEmpty();
    }

    private static List<Integer> sizes(final String list) {
        return java.util.Arrays.stream(list.split(","))
                .map(String::strip)
                .map(Integer::parseInt)
                .toList();
    }

    private static void write(final BatchEvalReport report) throws IOException {
        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-batch";
        EvalOutput.write(EvalProvenance.capture("batch", report.model(), ""), name, report.table(), report.json());
        log.info("Batch eval report {}\n{}", name, report.table());
    }
}
