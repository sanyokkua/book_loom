package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
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
 *
 * <p>{@code BOOKLOOM_EVAL_LANGS} (a comma list of tags, or {@code all}) runs the per-language mini-corpora under
 * {@code eval/languages/} instead; {@code BOOKLOOM_EVAL_RULES=generic} forces every prompt to the generic language
 * rules, and the reports then end in {@code -generic}, so the two can be compared.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
class PromptEvalTest {

    private static final String DEFAULT_MODEL = "gemma4:e4b-mlx";
    private static final List<String> LANGUAGES =
            List.of("en", "ru", "uk", "fr", "hr", "pl", "cs", "sl", "sk", "es", "pt", "de");

    @Test
    void promptEval_realModel_meetsTheParseGateAndJudgeFloors() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", DEFAULT_MODEL);
        final ModelCalls calls = calls(model);
        final int repeats = Integer.parseInt(System.getenv().getOrDefault("BOOKLOOM_EVAL_STABILITY", "1"));
        final String languages = System.getenv().getOrDefault("BOOKLOOM_EVAL_LANGS", "");
        final List<EvalReport> reports = languages.isBlank()
                ? List.of(englishToUkrainian(calls, model, repeats))
                : Arrays.stream(languages.split(","))
                        .map(String::strip)
                        .flatMap(tag -> tag.equals("all") ? LANGUAGES.stream() : Stream.of(tag))
                        .distinct()
                        .map(tag -> languageReport(calls, model, tag, repeats))
                        .toList();

        for (final EvalReport report : reports) {
            write(report);
        }
        assertThat(reports)
                .allSatisfy(report ->
                        assertThat(report.meetsThresholds()).as(report.table()).isTrue());
    }

    private static EvalReport englishToUkrainian(final ModelCalls calls, final String model, final int repeats) {
        final PromptEvalRunner runner = new PromptEvalRunner(calls);
        final String only = System.getenv().getOrDefault("BOOKLOOM_EVAL_ONLY", "");
        final boolean skipJudge = "1".equals(System.getenv("BOOKLOOM_EVAL_SKIP_JUDGE"));
        final boolean corpusOnly = "corpus".equals(only);
        return new EvalReport(
                model,
                corpusOnly
                        ? List.of()
                        : runner.runAll(PromptEvalCases.ALL.stream()
                                .filter(evalCase -> evalCase.name().startsWith(only))
                                .filter(evalCase -> !skipJudge || !(evalCase instanceof EvalCase.Judge))
                                .toList()),
                skipJudge ? List.of() : runner.runDefects(EvalCorpus.defects(), repeats),
                rulesLabel());
    }

    private static EvalReport languageReport(
            final ModelCalls calls, final String model, final String tag, final int repeats) {
        final LanguageCorpus corpus = EvalCorpus.language(tag);
        final PromptEvalRunner.LanguageRun run = new PromptEvalRunner(
                        calls, corpus.sourceLanguage(), corpus.targetLanguage())
                .runLanguage(corpus, repeats);
        return new EvalReport(model + " [" + tag + "]", run.rows(), run.defectRows(), rulesLabel());
    }

    /** {@code generic} when the run is forced to the generic rules, as {@code LanguageRules} reads the switch. */
    private static String rulesLabel() {
        final String asked = System.getProperty("bookloom.eval.rules", System.getenv("BOOKLOOM_EVAL_RULES"));
        return "generic".equalsIgnoreCase(asked) ? "generic" : "language";
    }

    private static void write(final EvalReport report) throws IOException {
        final String name = safeName(report.model()) + ("generic".equals(report.rules()) ? "-generic" : "");
        final Path file = Path.of("build", "reports", "promptEval", name + ".txt");
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, report.table() + "\n", StandardCharsets.UTF_8);
        Files.writeString(file.resolveSibling(name + ".json"), report.json() + "\n", StandardCharsets.UTF_8);
        log.info("Prompt eval report {}\n{}", file.toAbsolutePath(), report.table());
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
