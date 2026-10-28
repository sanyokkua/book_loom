package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;

/**
 * The mini suite (15j.J15): the invented `mini-en` book through the names pass, the translation job and the export's
 * consistency pass against a real model, each expectation of {@code eval/datasets/mini-en/expectations.json} scored. Local-only:
 * {@code scripts/eval-matrix.sh --suite mini}, or {@code BOOKLOOM_EVAL_SUITE=mini ./gradlew :pipeline:promptEval} with
 * {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_PROVIDER} and {@code BOOKLOOM_EVAL_MODEL}. {@code BOOKLOOM_EVAL_DIAL}
 * (default {@code BALANCED}) and {@code BOOKLOOM_EVAL_WINDOW} pass through; {@code BOOKLOOM_EVAL_NAMES}
 * ({@code TRANSLITERATE}, the default, {@code TRANSLATE} or {@code KEEP_ORIGINAL}) is the brief's name policy, which
 * decides what the brand expectations demand. {@code BOOKLOOM_EVAL_PAIR} ({@code en-uk}, the default, reads
 * {@code mini-en}; {@code ru-uk} reads {@code mini-ru}) picks the dataset. It asserts no floor; the report lands in
 * {@code build/reports/promptEval/<model>-mini-<pair>-<dial>-<policy>.json} and {@code .txt}, and the history folder
 * carries the same pair, dial and policy label.
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

        final EvalPair pair = EvalPair.fromEnvironment(System.getenv());
        final EvalRepeats repeats = EvalRepeats.fromEnvironment(System.getenv());
        final EvalRepeats.Runs<MiniReport> runs = repeats.run(
                PromptEvalTest.chatModel(model),
                seeded -> repeat(seeded, model, dial, policy, pair.dataset()),
                MiniReport::passRate);
        final MiniReport report = runs.results().get(0);
        final MiniRun run = report.run();
        write(model, pair.label(dial.name(), policy.name()), pair, runs);
        assertThat(run.outcomes()).as(report.table()).isNotEmpty();
    }

    private static void write(
            final String model, final String label, final EvalPair pair, final EvalRepeats.Runs<MiniReport> runs)
            throws IOException {
        final MiniReport report = runs.results().get(0);
        final List<CaseRecord.Entry> cases = new ArrayList<>();
        for (int repeat = 0; repeat < runs.results().size(); repeat++) {
            cases.addAll(runs.results().get(repeat).cases(repeat, runs.seeds().get(repeat)));
        }
        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + MiniReport.SUITE + "-" + label;
        EvalOutput.write(
                EvalProvenance.capture(MiniReport.SUITE, model, label)
                        .withDataset(pair.dataset())
                        .withAggregates(runs.aggregates()),
                name,
                report.table(),
                report.json(),
                Path.of("build", "reports", "promptEval"),
                EvalOutput.historyRoot(System.getenv(), EvalProvenance.repoRoot()),
                cases);
        log.info("Mini eval report {}\n{}", name, report.table());
    }

    private MiniReport repeat(
            final ChatModel seeded,
            final String model,
            final QualityDial dial,
            final NamePolicy policy,
            final String dataset) {
        try {
            final MiniRun run = new MiniRunner(MiniBook.load(dataset), dial, policy, EvalWindow.window())
                    .run(seeded, Files.createTempDirectory(workDir, "repeat"));
            return new MiniReport(
                    model,
                    dial.name(),
                    run,
                    MiniScorer.score(MiniExpectations.load(dataset), run),
                    MiniLabels.load(dataset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
