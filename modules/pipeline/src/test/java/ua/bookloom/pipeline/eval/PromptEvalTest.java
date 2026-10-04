package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The prompt eval: the fixed case set through the production prompt builders against a real local Ollama model,
 * measured before any repair a run would make. Local-only — {@code ./gradlew :pipeline:promptEval} with
 * {@code BOOKLOOM_EVAL_URL} (for example {@code http://localhost:11434}; with {@code BOOKLOOM_EVAL_PROVIDER=lmstudio} the OpenAI-compatible {@code http://localhost:1234/v1}), {@code BOOKLOOM_EVAL_STABILITY} (judge each corpus case this many times) and optionally
 * {@code BOOKLOOM_EVAL_MODEL} (default {@code gemma4:e4b-mlx}) and {@code BOOKLOOM_EVAL_SKIP_JUDGE=1} (draft, fix and suggest cases only, for a model whose judge call stalls) and {@code BOOKLOOM_EVAL_ONLY} (a case-name prefix, such
 * as {@code suggest}, to run only those cases); skipped when the URL is unset. The table is written to
 * {@code build/reports/promptEval/<model>.txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
class PromptEvalTest {

    private static final String DEFAULT_MODEL = "gemma4:e4b-mlx";

    @Test
    void promptEval_realModel_meetsTheParseGateAndJudgeFloors() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", DEFAULT_MODEL);
        final PromptEvalRunner runner = new PromptEvalRunner(calls(model));

        final String only = System.getenv().getOrDefault("BOOKLOOM_EVAL_ONLY", "");
        final int repeats = Integer.parseInt(System.getenv().getOrDefault("BOOKLOOM_EVAL_STABILITY", "1"));
        final boolean skipJudge = "1".equals(System.getenv("BOOKLOOM_EVAL_SKIP_JUDGE"));
        final boolean corpusOnly = "corpus".equals(only);
        final EvalReport report = new EvalReport(
                model,
                corpusOnly
                        ? List.of()
                        : runner.runAll(PromptEvalCases.ALL.stream()
                                .filter(evalCase -> evalCase.name().startsWith(only))
                                .filter(evalCase -> !skipJudge || !(evalCase instanceof EvalCase.Judge))
                                .toList()),
                skipJudge ? List.of() : runner.runDefects(EvalCorpus.defects(), repeats));

        final Path file = Path.of("build", "reports", "promptEval", safeName(model) + ".txt");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, report.table() + "\n", StandardCharsets.UTF_8);
        Files.writeString(file.resolveSibling(safeName(model) + ".json"), report.json() + "\n", StandardCharsets.UTF_8);
        log.info("Prompt eval report {}\n{}", file.toAbsolutePath(), report.table());
        assertThat(report.meetsThresholds()).as(report.table()).isTrue();
    }

    private static String safeName(final String model) {
        return model.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static ModelCalls calls(final String modelId) {
        final Injector injector = Guice.createInjector(new LlmModule());
        final boolean lmStudio = "lmstudio".equalsIgnoreCase(System.getenv("BOOKLOOM_EVAL_PROVIDER"));
        final ProviderConfig config = new ProviderConfig(
                "eval",
                lmStudio ? ProviderKind.OPENAI_COMPATIBLE : ProviderKind.OLLAMA,
                URI.create(Objects.requireNonNull(System.getenv("BOOKLOOM_EVAL_URL"))),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        final Result<ProviderConfig> registered =
                injector.getInstance(ProviderConfigs.class).register(config);
        assertThat(registered.isOk())
                .as("provider registration: " + registered.error())
                .isTrue();
        final Result<ChatModel> model =
                injector.getInstance(ChatModelFactory.class).create(new ModelSelection("eval", modelId));
        assertThat(model.isOk()).as("chat model creation: " + model.error()).isTrue();
        final ChatModel chatModel = Objects.requireNonNull(model.data());
        return (callKind, segmentId, request) -> chatModel.chat(request);
    }
}
