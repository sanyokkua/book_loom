package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;

/**
 * The sequence suite (15e.3): a generated synthetic book of eight chapters and about 320 paragraphs through the real job
 * against a real model, measured for the defects that only a whole run shows. Local-only:
 * {@code scripts/eval-matrix.sh --suite sequence}, or {@code BOOKLOOM_EVAL_SUITE=sequence ./gradlew :pipeline:promptEval}
 * with {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL}, {@code BOOKLOOM_EVAL_DIAL}
 * (default {@code BALANCED}), {@code BOOKLOOM_EVAL_WINDOW}, {@code BOOKLOOM_EVAL_REPEATS} (K repeats, seeds 1000 upwards; the run-level score is one minus the flagged rate) and {@code BOOKLOOM_EVAL_NARRATOR} ({@code unset}, the
 * default, is the real run's brief with no narrator; {@code set} is a first-person male narrator; {@code detect} runs
 * the narrator detector on the fixture and applies {@code BOOKLOOM_EVAL_NARRATOR_GENDER}, {@code male} by default, as
 * the Start question's answer would). It measures and
 * asserts no floor; 15e.4 records the thresholds. The report lands in
 * {@code build/reports/promptEval/<model>-sequence-en-uk-<dial>-<narrator>.json} and {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "sequence")
class SequenceEvalTest {

    @TempDir
    private Path workDir;

    @Test
    void sequence_realModel_reportsEveryMetric() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", "gemma4:e4b-mlx");
        final QualityDial dial = QualityDial.valueOf(
                System.getenv().getOrDefault("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(java.util.Locale.ROOT));
        final int window = EvalWindow.window();
        final SequenceNarratorMode narrator = SequenceNarratorMode.parse(System.getenv("BOOKLOOM_EVAL_NARRATOR"));
        final Gender detected = SequenceNarratorMode.parseGender(System.getenv("BOOKLOOM_EVAL_NARRATOR_GENDER"));
        final SequenceFixture fixture = SequenceFixture.load();

        final EvalRepeats.Runs<SequenceRun> runs = EvalRepeats.fromEnvironment(System.getenv())
                .run(
                        PromptEvalTest.chatModel(model),
                        seeded -> new SequenceEval(fixture, dial, window, narrator, detected).run(seeded, repeatDir()),
                        run -> 1 - measure(fixture, model, dial, run).flaggedRate());
        final SequenceRun run = runs.results().get(0);
        final SequenceReport report = new SequenceReport(measure(fixture, model, dial, run));

        write(model, dial, narrator, report, run, runs.aggregates());
        assertThat(run.segments()).as(report.table()).isNotEmpty();
    }

    private Path repeatDir() {
        try {
            return Files.createTempDirectory(workDir, "repeat");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SequenceMetrics measure(
            final SequenceFixture fixture, final String model, final QualityDial dial, final SequenceRun run) {
        return new SequenceMeasure(fixture).measure(model, dial.name(), run);
    }

    private static void write(
            final String model,
            final QualityDial dial,
            final SequenceNarratorMode narrator,
            final SequenceReport report,
            final SequenceRun run,
            final List<Double> aggregates)
            throws IOException {
        final String label = "en-uk-" + dial.name().toLowerCase(Locale.ROOT) + "-" + narrator.label();
        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-sequence-" + label;
        EvalOutput.write(
                EvalProvenance.capture("sequence", model, label).withAggregates(aggregates),
                name,
                report.table(),
                report.json(run));
        log.info("Sequence eval report {}\n{}", name, report.table());
    }
}
