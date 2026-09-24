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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;

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
