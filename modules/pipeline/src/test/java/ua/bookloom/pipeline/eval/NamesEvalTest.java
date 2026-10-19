package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The names suite (15i.E1): Names &amp; style end to end against a real local model, scored against gold. Local-only:
 * {@code scripts/eval-matrix.sh --suite names}, or {@code BOOKLOOM_EVAL_SUITE=names ./gradlew :pipeline:promptEval}
 * with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER} and {@code BOOKLOOM_EVAL_MODEL}.
 * {@code BOOKLOOM_EVAL_BOOK} (a committed book id or {@code all}, the default) picks the invented failure books; when
 * {@code BOOKLOOM_CORPUS_DIR} is set every {@code *.gold.json} under it runs too. It measures and asserts no floor; the
 * report lands in {@code build/reports/promptEval/<model>-names.json} and {@code .txt}. See
 * {@code docs/DEVELOPMENT.md#names-suite}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "names")
class NamesEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void names_realModel_reportsEveryBookAgainstItsGold() throws IOException {
        final Map<String, String> env = System.getenv();
        final String model = env.getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);

        final NamesReport report = NamesSuites.run(model, PromptEvalTest.chatModel(model), workDir, env);

        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + NamesReport.SUITE;
        EvalOutput.write(EvalProvenance.capture(NamesReport.SUITE, model, ""), name, report.table(), report.json());
        log.info("Names eval report {}\n{}", name, report.table());
        assertThat(report.rows()).as(report.table()).isNotEmpty();
    }
}
