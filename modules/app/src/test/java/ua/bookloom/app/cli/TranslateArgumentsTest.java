package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;

class TranslateArgumentsTest {

    @TempDir
    private Path tempDir;

    // A command without provider flags remains the deterministic offline pseudo invocation.
    @Test
    void parse_pseudoDefaults_returnsPseudoSelectionWithoutOverrides() throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(List.of(book().toString()));

        final TranslateArguments arguments = dataOf(result);
        assertThat(arguments.providerId()).isEqualTo("pseudo");
        assertThat(arguments.modelId()).isNull();
        assertThat(arguments.baseUrl()).isNull();
        assertThat(arguments.requestTimeout()).isNull();
    }

    // A preset provider records only the explicitly supplied real-model selection.
    @Test
    void parse_ollamaModel_returnsPresetSelectionWithoutOverrides() throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(
                List.of(book().toString(), "--provider", "ollama", "--model", "gemma4:e4b-mlx"));

        final TranslateArguments arguments = dataOf(result);
        assertThat(arguments.providerId()).isEqualTo("ollama");
        assertThat(arguments.modelId()).isEqualTo("gemma4:e4b-mlx");
        assertThat(arguments.baseUrl()).isNull();
        assertThat(arguments.requestTimeout()).isNull();
    }

    // Explicit endpoint and request-timeout flags remain typed values for run-local registration.
    @Test
    void parse_ollamaOverrides_returnsUrlAndDuration() throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(List.of(
                book().toString(),
                "--provider",
                "ollama",
                "--model",
                "gemma4:e4b-mlx",
                "--base-url",
                "http://10.0.0.5:11434",
                "--timeout",
                "30"));

        final TranslateArguments arguments = dataOf(result);
        assertThat(arguments.baseUrl()).isEqualTo(URI.create("http://10.0.0.5:11434"));
        assertThat(arguments.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    // A custom OpenAI-compatible provider requires and preserves its endpoint and model id.
    @Test
    void parse_openAiCompatible_returnsCustomProviderSelection() throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(List.of(
                book().toString(),
                "--provider",
                "openai-compatible",
                "--base-url",
                "http://localhost:8080/v1",
                "--model",
                "qwen3"));

        final TranslateArguments arguments = dataOf(result);
        assertThat(arguments.providerId()).isEqualTo("openai-compatible");
        assertThat(arguments.modelId()).isEqualTo("qwen3");
        assertThat(arguments.baseUrl()).isEqualTo(URI.create("http://localhost:8080/v1"));
    }

    // Without run options the command keeps the brief's dial and names, waits through a twelve-hour outage, writes
    // what a stopped run translated and writes no report.
    @Test
    void parse_noRunOptions_returnsTheDefaults() throws IOException {
        final TranslateArguments arguments = dataOf(TranslateArguments.parse(List.of(book().toString())));

        assertThat(arguments.options()).isEqualTo(RunOptions.DEFAULTS);
        assertThat(arguments.options().maxOutage()).isEqualTo(Duration.ofHours(12));
    }

    // The headless proof run: every run option at once.
    @Test
    void parse_everyRunOption_returnsThemTyped() throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(List.of(
                book().toString(),
                "--quality",
                "balanced",
                "--names",
                "transliterate",
                "--review-names",
                "--max-outage",
                "1h30m",
                "--report",
                "build/e2e/run.json",
                "--stop-after",
                "300",
                "--no-partial"));

        assertThat(dataOf(result).options())
                .isEqualTo(new RunOptions(
                        QualityDial.BALANCED,
                        NamePolicy.TRANSLITERATE,
                        true,
                        Duration.ofMinutes(90),
                        false,
                        Path.of("build/e2e/run.json"),
                        300));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "-3", "many"})
    void parse_stopAfterNotAPositiveNumber_isRejected(String value) throws IOException {
        assertThat(TranslateArguments.parse(List.of(book().toString(), "--stop-after", value))
                        .isErr())
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"fast, FAST", "MAX, MAX", "balanced, BALANCED"})
    void parse_qualityValue_isRead(String value, QualityDial expected) throws IOException {
        assertThat(dataOf(TranslateArguments.parse(List.of(book().toString(), "--quality", value)))
                        .options()
                        .quality())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"keep, KEEP_ORIGINAL", "translate, TRANSLATE", "Transliterate, TRANSLITERATE"})
    void parse_namesValue_isRead(String value, NamePolicy expected) throws IOException {
        assertThat(dataOf(TranslateArguments.parse(List.of(book().toString(), "--names", value)))
                        .options()
                        .names())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"45s, PT45S", "12h, PT12H", "1h30m, PT1H30M", "PT2H, PT2H"})
    void parse_maxOutageValue_isRead(String value, Duration expected) throws IOException {
        assertThat(dataOf(TranslateArguments.parse(List.of(book().toString(), "--max-outage", value)))
                        .options()
                        .maxOutage())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            bad quality | --quality,best | --quality must be fast, balanced or max
            bad names | --names,romanize | --names must be translate, transliterate or keep
            zero outage | --max-outage,0m | --max-outage must be a positive duration such as 12h, 90m or 1h30m
            word outage | --max-outage,forever | --max-outage must be a positive duration such as 12h, 90m or 1h30m
            missing report | --report | --report needs a value
            repeated flag | --review-names,--review-names | --review-names was repeated
            """)
    void parse_invalidRunOption_returnsValidationError(String name, String options, String reason) throws IOException {
        final List<String> args = Stream.concat(Stream.of(book().toString()), Stream.of(options.split(",")))
                .toList();

        assertThat(errorOf(TranslateArguments.parse(args)).message()).isEqualTo(reason);
    }

    // Provider-specific invalid inputs are rejected before any provider configuration or network call is possible.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "unknown-provider",
                "missing-ollama-model",
                "missing-custom-url",
                "zero-timeout",
                "nonnumeric-timeout",
                "repeated-provider",
                "unsupported-num-ctx"
            })
    void parse_invalidProviderArguments_returnsValidationError(String invalidCase) throws IOException {
        final Result<TranslateArguments> result = TranslateArguments.parse(argumentsFor(invalidCase));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).message()).isEqualTo(invalidReason(invalidCase));
    }

    private List<String> argumentsFor(String invalidCase) throws IOException {
        final String path = book().toString();
        return switch (invalidCase) {
            case "unknown-provider" -> List.of(path, "--provider", "gemini", "--model", "gemini-2.5-flash");
            case "missing-ollama-model" -> List.of(path, "--provider", "ollama");
            case "missing-custom-url" -> List.of(path, "--provider", "openai-compatible", "--model", "qwen3");
            case "zero-timeout" -> List.of(path, "--provider", "ollama", "--model", "gemma4:e4b-mlx", "--timeout", "0");
            case "nonnumeric-timeout" ->
                List.of(path, "--provider", "ollama", "--model", "gemma4:e4b-mlx", "--timeout", "x");
            case "repeated-provider" ->
                List.of(path, "--provider", "ollama", "--provider", "lmstudio", "--model", "gemma4:e4b-mlx");
            case "unsupported-num-ctx" ->
                List.of(path, "--provider", "ollama", "--model", "gemma4:e4b-mlx", "--num-ctx", "8192");
            default -> throw new IllegalArgumentException("unknown invalid case: " + invalidCase);
        };
    }

    private static String invalidReason(String invalidCase) {
        return switch (invalidCase) {
            case "unknown-provider" -> "--provider must be pseudo, ollama, lmstudio or openai-compatible";
            case "missing-ollama-model" -> "--model is required for provider ollama";
            case "missing-custom-url" -> "--base-url is required for provider openai-compatible";
            case "zero-timeout", "nonnumeric-timeout" -> "--timeout must be a positive whole number";
            case "repeated-provider" -> "--provider was repeated";
            case "unsupported-num-ctx" -> "unknown option";
            default -> throw new IllegalArgumentException("unknown invalid case: " + invalidCase);
        };
    }

    private Path book() throws IOException {
        return Files.writeString(tempDir.resolve("Book.md"), "Hello world.\n", StandardCharsets.UTF_8);
    }

    private static TranslateArguments dataOf(Result<TranslateArguments> result) {
        return Objects.requireNonNull(result.data(), "arguments");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
