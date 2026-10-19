package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * One whole owner book through everything the window decides for a typical user (15j.J16): the predicted Book Brief
 * accepted, the names pass, the translation job at the dial, and the export written twice, with and without the
 * consistency pass. Local-only, and it must be selected by class, since {@code PromptEvalTest} also runs whenever
 * {@code BOOKLOOM_EVAL_URL} is set:
 * {@code BOOKLOOM_EVAL_URL=… BOOKLOOM_EVAL_BOOK_FILE=<book> BOOKLOOM_EVAL_OUT_DIR=<dir outside the repository>
 * ./gradlew :pipeline:promptEval --tests '*BookRunEvalTest'}. {@code BOOKLOOM_EVAL_DIAL} (default {@code BALANCED}),
 * {@code BOOKLOOM_EVAL_MODEL}, {@code BOOKLOOM_EVAL_PROVIDER}, {@code BOOKLOOM_EVAL_WINDOW},
 * {@code BOOKLOOM_EVAL_SOURCE} (default {@code en}) and {@code BOOKLOOM_EVAL_TARGET} (default {@code uk}) pass
 * through. The output directory holds book text, so a directory inside the checkout is refused.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_BOOK_FILE", matches = ".+")
class BookRunEvalTest {

    @Test
    void bookRun_realModel_writesBothExportsTheCsvAndTheCounts() throws IOException {
        final Path book =
                Path.of(required("BOOKLOOM_EVAL_BOOK_FILE")).toAbsolutePath().normalize();
        final Path outDir =
                Path.of(required("BOOKLOOM_EVAL_OUT_DIR")).toAbsolutePath().normalize();
        assertThat(outDir.startsWith(EvalProvenance.repoRoot().toAbsolutePath().normalize()))
                .as("BOOKLOOM_EVAL_OUT_DIR must lie outside the repository: the exports hold book text")
                .isFalse();
        assertThat(book).as("BOOKLOOM_EVAL_BOOK_FILE").isRegularFile();
        Files.createDirectories(outDir);
        final String model = env("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final QualityDial dial =
                QualityDial.valueOf(env("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(Locale.ROOT));

        final BookRunSummary summary = new BookRunner(new BookRunner.Options(
                        book,
                        outDir,
                        env("BOOKLOOM_EVAL_SOURCE", "en"),
                        env("BOOKLOOM_EVAL_TARGET", "uk"),
                        dial,
                        EvalWindow.window()))
                .run(PromptEvalTest.chatModel(model), model);

        log.info("Book run summary {}", summary);
        assertThat(outDir.resolve(BookRunSummary.FILE)).isRegularFile();
        assertThat(summary.noConsistencyPass()).as("export without the pass").isNotNull();
        assertThat(summary.withConsistencyPass()).as("export with the pass").isNotNull();
    }

    private static String required(final String name) {
        final String value = System.getenv(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }

    private static String env(final String name, final String fallback) {
        final String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
