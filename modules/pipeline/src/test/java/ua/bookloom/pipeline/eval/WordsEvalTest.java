package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.checks.ModelWordValidator;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.checks.WordValidators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The garbled-word suite: the model-based pass over {@code eval/words.json} against a real local model,
 * reporting recall over the garbled cases and the false-positive rate over the clean ones in
 * {@code build/reports/promptEval/<model>-words.json} and {@code .txt}. Local-only:
 * {@code scripts/eval-matrix.sh --suite words}, or {@code BOOKLOOM_EVAL_SUITE=words ./gradlew :pipeline:promptEval}
 * with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_MODEL} and
 * {@code BOOKLOOM_EVAL_WORD_BATCH} (texts per call, default 5). The validator is chosen by the switch
 * {@code bookloom.eval.wordvalidator} (system property) or {@code BOOKLOOM_EVAL_WORDVALIDATOR}, default {@code model};
 * anything else measures the no-op baseline. It measures; the owner reads the table against the 70% bar.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "words")
class WordsEvalTest {

    private static final int DEFAULT_BATCH = 5;
    private static final CallFrame FRAME = new CallFrame(
            PromptEvalCases.SOURCE_LANGUAGE,
            PromptEvalCases.TARGET_LANGUAGE,
            StyleSheet.from(BookBrief.defaults(PromptEvalCases.SOURCE_LANGUAGE)),
            ForeignPassagePolicy.KEEP);

    static List<WordCase> cases() {
        try (InputStream in = WordsEvalTest.class.getResourceAsStream("/eval/words.json")) {
            Objects.requireNonNull(in, "words.json");
            return new ObjectMapper().readValue(in, new TypeReference<List<WordCase>>() {});
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void wordsEval_realModel_reportsRecallAndFalsePositives() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", "gemma4:e4b-mlx");
        final ModelCalls calls = PromptEvalTest.calls(model);
        final String mode = System.getProperty(
                WordValidators.SWITCH, System.getenv().getOrDefault("BOOKLOOM_EVAL_WORDVALIDATOR", "model"));
        final WordValidator chosen =
                WordValidators.forMode(mode, new PromptTemplates(), new ObjectMapper(), calls, FRAME);
        final int batch =
                Integer.parseInt(System.getenv().getOrDefault("BOOKLOOM_EVAL_WORD_BATCH", "" + DEFAULT_BATCH));

        final WordsEvalReport report = new WordsEvalReport(
                model,
                chosen instanceof ModelWordValidator model2
                        ? new WordsEvalRunner(model2, batch).run(cases())
                        : noOp(cases()));

        write(report);
        assertThat(report.rows()).as(report.table()).isNotEmpty();
    }

    private static List<WordsEvalRow> noOp(final List<WordCase> cases) {
        return cases.stream()
                .map(wordCase -> new WordsEvalRow(wordCase.id(), wordCase.defective(), wordCase.words(), List.of()))
                .toList();
    }

    private static void write(final WordsEvalReport report) throws IOException {
        final String name = report.model().replaceAll("[^A-Za-z0-9._-]", "_") + "-words";
        final Path file = Path.of("build", "reports", "promptEval", name + ".txt");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, report.table() + "\n", StandardCharsets.UTF_8);
        Files.writeString(file.resolveSibling(name + ".json"), report.json() + "\n", StandardCharsets.UTF_8);
        log.info("Words eval report {}\n{}", file.toAbsolutePath(), report.table());
    }
}
