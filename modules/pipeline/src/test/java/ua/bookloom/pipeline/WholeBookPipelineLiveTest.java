package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.WholeBookRun.dataOf;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.llm.LlmModule;

/**
 * The whole-book scenario of {@link WholeBookPipelineEndToEndTest} against a REAL local model in each dialect: it shows
 * a small model can follow every template a Balanced run uses. A real model's wording varies, so every assertion is
 * structural. Each case runs only when its server's address is set, and never in CI or {@code check}.
 */
@Tag("liveLocal")
class WholeBookPipelineLiveTest {

    private static final String OLLAMA_URL = "BOOKLOOM_LIVE_OLLAMA_URL";
    private static final String LMSTUDIO_URL = "BOOKLOOM_LIVE_LMSTUDIO_URL";
    // The finding source a draft reply that could not be read, even after its structural repair, is flagged with.
    private static final String REPLY_FINDING = "reply";

    @TempDir
    private Path tempDir;

    // Ollama's native dialect drafts, judges and repairs the book to the end with every reply read.
    @Test
    @EnabledIfEnvironmentVariable(named = OLLAMA_URL, matches = ".+")
    void run_realOllama_completesWithEveryReplyRead() {
        assertCompletedWithEveryReplyRead(model(
                ProviderKind.OLLAMA, URI.create(env(OLLAMA_URL)), "BOOKLOOM_LIVE_OLLAMA_MODEL", "gemma4:e4b-mlx"));
    }

    // LM Studio's OpenAI-compatible dialect drafts, judges and repairs the book to the end with every reply read.
    @Test
    @EnabledIfEnvironmentVariable(named = LMSTUDIO_URL, matches = ".+")
    void run_realLmStudio_completesWithEveryReplyRead() {
        assertCompletedWithEveryReplyRead(model(
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(env(LMSTUDIO_URL)).resolve("/v1"),
                "BOOKLOOM_LIVE_LMSTUDIO_MODEL",
                "google/gemma-4-e4b"));
    }

    private void assertCompletedWithEveryReplyRead(final ChatModel model) {
        final WholeBookRun.Project project = WholeBookRun.project(WholeBookRun.book(tempDir), QualityDial.BALANCED);

        final JobState end =
                report(project.job(ReviewMode.UNATTENDED, model).run()).end();
        final List<SegmentView> segments = dataOf(project.desk().queue(project.id(), ReviewFilter.ALL_SEGMENTS));

        assertThat(end).isEqualTo(JobState.COMPLETED);
        assertThat(segments).hasSize(5).noneMatch(segment -> segment.status() == SegmentStatus.PENDING);
        assertThat(segments)
                .flatExtracting(SegmentView::findings)
                .noneMatch(finding -> REPLY_FINDING.equals(finding.raisedBy()));
        assertThat(segments).anyMatch(segment -> segment.judgeScore() != null);
    }

    /** A real chat model built through {@link LlmModule}, as the app builds one. */
    private static ChatModel model(
            final ProviderKind kind, final URI baseUrl, final String modelEnv, final String defaultModel) {
        final Injector injector = Guice.createInjector(new LlmModule());
        final String providerId = "live";
        dataOf(injector.getInstance(ProviderConfigs.class)
                .register(new ProviderConfig(
                        providerId,
                        kind,
                        baseUrl,
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT)));
        final String modelId = System.getenv().getOrDefault(modelEnv, defaultModel);
        return dataOf(injector.getInstance(ChatModelFactory.class).create(new ModelSelection(providerId, modelId)));
    }

    private static String env(final String name) {
        return Objects.requireNonNull(System.getenv(name), name);
    }
}
