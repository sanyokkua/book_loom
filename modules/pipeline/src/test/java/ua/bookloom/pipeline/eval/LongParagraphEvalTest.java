package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The long-paragraph set of 15i.X3 drafted by a real model: each of the twenty public-domain paragraphs of
 * {@code eval/long-paragraphs.json} is drafted on the real draft path, as sentence groups when
 * {@code BOOKLOOM_LONG_PARAGRAPHS=groups} (the eval-matrix flag {@code --long-paragraphs groups}) and in one call
 * otherwise, and every source and target is written to {@code build/reports/promptEval/<model>-longpara.json} and
 * {@code .txt} for grading. Local-only: {@code scripts/eval-matrix.sh --suite longpara}, or
 * {@code BOOKLOOM_EVAL_SUITE=longpara ./gradlew :pipeline:promptEval} with {@code BOOKLOOM_EVAL_URL} and optionally
 * {@code BOOKLOOM_EVAL_PROVIDER} and {@code BOOKLOOM_EVAL_MODEL}. It measures and asserts no floor.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "longpara")
class LongParagraphEvalTest {

    @Test
    void longPara_realModel_writesEverySourceAndTarget() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final LongParagraphEvalRunner runner = new LongParagraphEvalRunner(PromptEvalTest.calls(model));

        final LongParagraphReport report = runner.run(LongParagraph.load());

        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + LongParagraphReport.SUITE;
        EvalOutput.write(
                EvalProvenance.capture(LongParagraphReport.SUITE, model, ""),
                name,
                report.table(model),
                report.json(model));
        log.info("Long-paragraph eval report {}\n{}", name, report.table(model));
        assertThat(report.rows()).hasSize(20);
    }
}
