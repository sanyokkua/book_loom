package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The article suite (redesign task 1.11): the English {@code article-gravity} Markdown through today's job into one
 * target and exported, measured for the flagged rate (the run-level aggregate of each repeat) and the share of protected
 * spans (URLs, DOIs, code identifiers) the exported Markdown keeps verbatim. Local-only:
 * {@code BOOKLOOM_EVAL_PAIR_TARGET=uk scripts/eval-matrix.sh --suite article --repeats 1}, or
 * {@code BOOKLOOM_EVAL_SUITE=article ./gradlew :pipeline:promptEval --tests '*ArticleEvalTest'} with
 * {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL}, {@code BOOKLOOM_EVAL_DIAL}
 * (default {@code BALANCED}), {@code BOOKLOOM_EVAL_WINDOW}, {@code BOOKLOOM_EVAL_REPEATS} and
 * {@code BOOKLOOM_EVAL_PAIR_TARGET} ({@code uk}, {@code ru} or {@code fr}; default {@code uk}). It asserts no floor.
 * The report lands in {@code build/reports/promptEval/<model>-article-en-<target>-<dial>.json} and {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = ArticleReport.SUITE)
class ArticleEvalTest {

    static final String TARGET_ENV = "BOOKLOOM_EVAL_PAIR_TARGET";

    @TempDir
    private Path workDir;

    @Test
    void article_realModel_reportsFlaggedRateAndProtectedSpans() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", "gemma4:e4b-mlx");
        final QualityDial dial = QualityDial.valueOf(
                System.getenv().getOrDefault("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(Locale.ROOT));
        final String target =
                System.getenv().getOrDefault(TARGET_ENV, "uk").strip().toLowerCase(Locale.ROOT);
        final ArticleEval eval = new ArticleEval(dial, EvalWindow.window(), target);

        final EvalRepeats.Runs<ArticleRun> runs = EvalRepeats.fromEnvironment(System.getenv())
                .run(PromptEvalTest.chatModel(model), seeded -> eval.run(seeded, repeatDir()), ArticleRun::flaggedRate);
        final ArticleReport report =
                new ArticleReport(model, dial.name(), runs.results().get(0));

        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-article-" + report.label();
        EvalOutput.write(
                EvalProvenance.capture(ArticleReport.SUITE, model, report.label())
                        .withDataset(ArticleGravity.ID)
                        .withAggregates(runs.aggregates()),
                name,
                report.table(),
                report.json());
        log.info("Article eval report {}\n{}", name, report.table());
        assertThat(report.run().segments()).as(report.table()).isPositive();
    }

    private Path repeatDir() {
        try {
            return Files.createTempDirectory(workDir, "repeat");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
