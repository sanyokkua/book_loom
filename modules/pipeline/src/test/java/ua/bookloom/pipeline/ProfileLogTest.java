package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.targetReply;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.llm.GatedChatModel;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.persistence.memory.InMemoryProviderConfigs;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A run's start writes the model's resolved profile to the always-on run log once, as choices and counts: the model id
 * is the person's choice and is written, while no name or sentence of the book ever is. The model is the real bound
 * model of {@code :llm}, answered by a scripted transport; the book is invented.
 */
class ProfileLogTest {

    private static final String MODEL = "mara-7b";
    private static final List<String> BOOK_WORDS = List.of("Quillon", "Marbeck", "Ysolde", "salt orchard", "Квіллон");
    private static final String BOOK = """
            Quillon Marbeck walked through the salt orchard at dawn.

            Ysolde waited for Quillon Marbeck by the gate.
            """;

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void attach() {
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_start_writesOneProfileLineWithNoName() throws Exception {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), BOOK);
        final TestProject project = project(book, brief("en", "uk"));
        final RunRequest request =
                new RunRequest(project.id(), ReviewMode.UNATTENDED, null, MODEL, new ModelSelection("office", MODEL));
        final GatedChatModel model = new GatedChatModel(
                new ScriptedTransport(translations()),
                MODEL,
                new InferenceGate(),
                new RetryPolicy(
                        Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC), () -> 0.5, d -> {}));
        final TranslationJob job = dataOf(TranslationEngineImplTest.engine(project, new InMemoryProviderConfigs())
                .newJob(request, model));

        job.run();

        assertThat(infoAndAbove())
                .filteredOn(line -> line.startsWith("profile resolved "))
                .containsExactly("profile resolved model=mara-7b match=DEFAULT quant=unknown reasoning=NONE(none)"
                        + " sampling=profile window=16384 batch=2");
        assertThat(infoAndAbove()).noneMatch(line -> BOOK_WORDS.stream().anyMatch(line::contains));
    }

    private static ScriptedChatModel translations() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        BATCH,
                        batchReply(items(IntStream.rangeClosed(1, 2)
                                .mapToObj(id -> entry(Integer.toString(id), "Квіллон пройшов соляний сад " + id + "."))
                                .toArray(String[]::new))));
        return model.answerTimes(
                20, Result.ok(new ChatResponse(targetReply("Квіллон пройшов соляний сад."), FinishReason.STOP)));
    }

    private List<String> infoAndAbove() {
        return logged.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    // The provider's transport, answered by the scripted model: the profile and its log line are :llm's own.
    private record ScriptedTransport(ScriptedChatModel scripted) implements ProviderClient {

        @Override
        public ProviderCallResult<Boolean> probe() {
            return ProviderCallResult.withoutRetryAfter(Result.ok(true));
        }

        @Override
        public ProviderCallResult<List<ModelInfo>> listModels() {
            return ProviderCallResult.withoutRetryAfter(Result.ok(List.of()));
        }

        @Override
        public ProviderCallResult<ChatResponse> chat(final String modelId, final ChatRequest request) {
            return ProviderCallResult.withoutRetryAfter(scripted.chat(request));
        }

        @Override
        public ProviderKind kind() {
            return ProviderKind.OPENAI_COMPATIBLE;
        }
    }
}
