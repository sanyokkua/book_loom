package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The sequence suite (15e.3): a generated synthetic book of eight chapters and about 320 paragraphs through the real job
 * against a real model, measured for the defects that only a whole run shows. Local-only:
 * {@code scripts/eval-matrix.sh --suite sequence}, or {@code BOOKLOOM_EVAL_SUITE=sequence ./gradlew :pipeline:promptEval}
 * with {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL}, {@code BOOKLOOM_EVAL_DIAL}
 * (default {@code BALANCED}), {@code BOOKLOOM_EVAL_WINDOW} and {@code BOOKLOOM_EVAL_NARRATOR} ({@code unset}, the
 * default, is the real run's brief with no narrator; {@code set} is a first-person male narrator). It measures and
 * asserts no floor; 15e.4 records the thresholds. The report lands in
 * {@code build/reports/promptEval/<model>-sequence-<narrator>.json} and {@code .txt}.
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
        final String asked = System.getenv("BOOKLOOM_EVAL_WINDOW");
        final Integer window = asked == null || asked.isBlank() ? null : Integer.valueOf(asked.strip());
        final SequenceNarratorMode narrator = SequenceNarratorMode.parse(System.getenv("BOOKLOOM_EVAL_NARRATOR"));
        final SequenceFixture fixture = SequenceFixture.load();

        final SequenceRun run =
                new SequenceEval(fixture, dial, window, narrator).run(PromptEvalTest.chatModel(model), workDir);
        final SequenceReport report = new SequenceReport(new SequenceMeasure(fixture).measure(model, dial.name(), run));

        write(model, narrator, report);
        assertThat(run.segments()).as(report.table()).isNotEmpty();
    }

    private static void write(final String model, final SequenceNarratorMode narrator, final SequenceReport report)
            throws IOException {
        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-sequence-" + narrator.label();
        final Path file = Path.of("build", "reports", "promptEval", name + ".txt");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, report.table() + "\n", StandardCharsets.UTF_8);
        Files.writeString(file.resolveSibling(name + ".json"), report.json() + "\n", StandardCharsets.UTF_8);
        log.info("Sequence eval report {}\n{}", file.toAbsolutePath(), report.table());
    }
}
