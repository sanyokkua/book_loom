package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;

/**
 * The mini suite (15j.J15): the invented 28-block book through the names pass, the translation job and the export's
 * consistency pass against a real model, each expectation of {@code eval/mini/expectations.json} scored. Local-only:
 * {@code scripts/eval-matrix.sh --suite mini}, or {@code BOOKLOOM_EVAL_SUITE=mini ./gradlew :pipeline:promptEval} with
 * {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_PROVIDER} and {@code BOOKLOOM_EVAL_MODEL}. {@code BOOKLOOM_EVAL_DIAL}
 * (default {@code BALANCED}) and {@code BOOKLOOM_EVAL_WINDOW} pass through; {@code BOOKLOOM_EVAL_NAMES}
 * ({@code TRANSLITERATE}, the default, {@code TRANSLATE} or {@code KEEP_ORIGINAL}) is the brief's name policy, which
 * decides what the brand expectations demand. It asserts no floor; the report lands in
 * {@code build/reports/promptEval/<model>-mini-<policy>.json} and {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "mini")
class MiniEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void mini_realModel_scoresEveryExpectation() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final QualityDial dial = QualityDial.valueOf(
                System.getenv().getOrDefault("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(Locale.ROOT));
        final NamePolicy policy = NamePolicy.valueOf(System.getenv()
                .getOrDefault("BOOKLOOM_EVAL_NAMES", "TRANSLITERATE")
                .toUpperCase(Locale.ROOT));

        final MiniRun run = new MiniRunner(MiniBook.load(), dial, policy, EvalWindow.window())
                .run(PromptEvalTest.chatModel(model), workDir);
        final MiniReport report =
                new MiniReport(model, dial.name(), run, MiniScorer.score(MiniExpectations.load(), run));

        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + MiniReport.SUITE + "-" + policy.name();
        EvalOutput.write(
                EvalProvenance.capture(MiniReport.SUITE, model, policy.name()), name, report.table(), report.json());
        log.info("Mini eval report {}\n{}", name, report.table());
        assertThat(run.outcomes()).as(report.table()).isNotEmpty();
    }
}
