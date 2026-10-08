package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
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
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The prompt eval: the fixed case set through the production prompt builders against a real local Ollama model,
 * measured before any repair a run would make. Local-only — {@code ./gradlew :pipeline:promptEval} with
 * {@code BOOKLOOM_EVAL_URL} (for example {@code http://localhost:11434}; with {@code BOOKLOOM_EVAL_PROVIDER=lmstudio} the OpenAI-compatible {@code http://localhost:1234/v1}), {@code BOOKLOOM_EVAL_STABILITY} (review each corpus case this many times) and optionally
 * {@code BOOKLOOM_EVAL_MODEL} (default {@code gemma4:e4b-mlx}) and {@code BOOKLOOM_EVAL_SKIP_REVIEW=1} (draft, fix and suggest cases only, for a model whose reviewer call stalls) and {@code BOOKLOOM_EVAL_ONLY} (a case-name prefix, such
 * as {@code suggest}, to run only those cases); skipped when the URL is unset. The table is written to
 * {@code build/reports/promptEval/<model>.txt}.
 *
 * <p>{@code BOOKLOOM_EVAL_LANGS} (a comma list of tags, or {@code all}) runs the per-language mini-corpora under
 * {@code eval/languages/} instead; {@code BOOKLOOM_EVAL_RULES=generic} forces every prompt to the generic language
 * rules, and the reports then end in {@code -generic}, so the two can be compared. With
 * {@code BOOKLOOM_EVAL_SUITE=batch} this test is skipped and {@link BatchEvalTest} runs instead, with
 * {@code BOOKLOOM_EVAL_SUITE=words} {@link WordsEvalTest} does, and with {@code BOOKLOOM_EVAL_SUITE=realrun}
 * {@link RealRunTest} does, and with {@code BOOKLOOM_EVAL_SUITE=sequence} {@link SequenceEvalTest} does, and with {@code prescan}, {@code terms}, {@code setup}, {@code consistency} or {@code retry} {@link StageEvalTest} does.
 *
 * <p>The requests are the app's: {@link PromptEvalRunner} and {@link BatchEvalRunner} build them with the run's own
 * request factory over an {@link EvalProject}, and every call goes through {@link JobModelCalls}, which sizes it to the
 * window. {@code BOOKLOOM_EVAL_WINDOW} sets that window (default: the context length the provider reports for the model,
 * limited as a run limits it, else the app's default), so a model can be measured at the window a run will give it.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@DisabledIfEnvironmentVariable(
        named = "BOOKLOOM_EVAL_SUITE",
        matches = "batch|words|realrun|sequence|prescan|terms|setup|consistency|retry")
class PromptEvalTest {

    static final String DEFAULT_MODEL = "gemma4:e4b-mlx";
    static final String PROVIDER_ID = "eval";
    private static final List<String> LANGUAGES =
            List.of("en", "ru", "uk", "fr", "hr", "pl", "cs", "sl", "sk", "es", "pt", "de");

    @Test
    void promptEval_realModel_meetsTheParseGateAndReviewerFloors() throws IOException {
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
        final boolean skipReview = "1".equals(System.getenv("BOOKLOOM_EVAL_SKIP_REVIEW"));
        final boolean corpusOnly = "corpus".equals(only);
        return new EvalReport(
                model,
                corpusOnly
                        ? List.of()
                        : runner.runAll(PromptEvalCases.ALL.stream()
                                .filter(evalCase -> evalCase.name().startsWith(only))
                                .filter(evalCase -> !skipReview || !(evalCase instanceof EvalCase.Review))
                                .toList()),
                skipReview ? List.of() : runner.runDefects(EvalCorpus.defects(), repeats),
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
        log.info("Prompt eval report {} window={}\n{}", file.toAbsolutePath(), EvalProject.window(), report.table());
    }

    private static String safeName(final String model) {
        return model.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static ModelCalls calls(final String modelId) {
        final ChatModel chatModel = chatModel(modelId);
        // The run's own seam, so a request is sized to the window and capped exactly as the app sends it; the guard
        // lets everything through and nothing is announced.
        return new JobModelCalls(
                onSent -> chatModel,
                event -> {},
                Clock.systemUTC(),
                PromptEvalCases.TARGET_LANGUAGE,
                EvalProject.window());
    }

    /** An injector whose provider registry holds the eval's one provider, built from the environment. */
    static Injector registeredProvider() {
        final Injector injector = Guice.createInjector(new LlmModule());
        final boolean lmStudio = "lmstudio".equalsIgnoreCase(System.getenv("BOOKLOOM_EVAL_PROVIDER"));
        final ProviderConfig config = new ProviderConfig(
                PROVIDER_ID,
                lmStudio ? ProviderKind.OPENAI_COMPATIBLE : ProviderKind.OLLAMA,
                URI.create(Objects.requireNonNull(System.getenv("BOOKLOOM_EVAL_URL"))),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        final Result<ProviderConfig> registered =
                injector.getInstance(ProviderConfigs.class).register(config);
        assertThat(registered.isOk())
                .as("provider registration: " + registered.error())
                .isTrue();
        return injector;
    }

    /** The real chat model of the eval's provider, built by the production factory. */
    static ChatModel chatModel(final String modelId) {
        final Result<ChatModel> model = registeredProvider()
                .getInstance(ChatModelFactory.class)
                .create(new ModelSelection(PROVIDER_ID, modelId));
        assertThat(model.isOk()).as("chat model creation: " + model.error()).isTrue();
        return Objects.requireNonNull(model.data());
    }
}
