package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The model's judgement of a book against a real local model (15h.E3), never in {@code check}:
 * {@code BOOKLOOM_EVAL_SUITE=stability} (a committed fixture book, three seeds, agreement per field) or
 * {@code BOOKLOOM_EVAL_SUITE=gold} (the owner's {@code *.gold.json} under {@code BOOKLOOM_CORPUS_DIR}, accuracy per
 * field; skipped when the directory is unset), with {@code BOOKLOOM_EVAL_URL} and optionally
 * {@code BOOKLOOM_EVAL_PROVIDER} and {@code BOOKLOOM_EVAL_MODEL}, or {@code scripts/eval-matrix.sh --suite stability}
 * over every model in {@code scripts/eval-models.txt}. It measures and asserts no floor; the report lands in
 * {@code build/reports/promptEval/<model>-<suite>.json} and {@code .txt}. See
 * {@code docs/DEVELOPMENT.md#judgement-evals}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "stability|gold")
class JudgementEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void judgement_realModel_reportsAgreementAndAccuracyPerField() throws IOException {
        final Map<String, String> env = System.getenv();
        final String model = env.getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final String suite = Objects.requireNonNull(env.get("BOOKLOOM_EVAL_SUITE"));
        final String corpus = env.getOrDefault(JudgementSuites.CORPUS_ENV, "");
        Assumptions.assumeTrue(
                !JudgementSuites.GOLD.equals(suite) || !corpus.isBlank(), "gold needs " + JudgementSuites.CORPUS_ENV);

        final JudgementReport report = JudgementSuites.run(suite, model, PromptEvalTest.chatModel(model), workDir, env);

        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + report.suite();
        EvalOutput.write(
                EvalProvenance.capture(report.suite(), report.model(), ""), name, report.table(), report.json());
        log.info("Judgement eval report {}\n{}", name, report.table());
        assertThat(report.books()).as(report.table()).isNotEmpty();
    }
}
