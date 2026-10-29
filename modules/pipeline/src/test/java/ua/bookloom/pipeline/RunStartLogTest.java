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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.persistence.memory.InMemoryProviderConfigs;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The run log is the one file an installed app always keeps, so a run's start is written there as counts and choices
 * only: it explains a night's run without holding a word of the book. The book is invented, and its names are checked
 * present in it, so their absence from the log means something.
 */
class RunStartLogTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "invented-model:4b";
    private static final int WINDOW = 16_384;
    private static final List<String> BOOK_WORDS =
            List.of("Quillon", "Marbeck", "Ysolde", "Farran", "salt orchard", "Квіллон", "соляний сад");
    private static final String BOOK = """
            Quillon Marbeck walked through the salt orchard at dawn.

            Ysolde Farran waited for Quillon Marbeck by the gate.

            Quillon Marbeck said nothing to Ysolde Farran.

            Ysolde Farran laughed at the grey sky.

            Quillon Marbeck lit the lamp in the tower.

            Ysolde Farran climbed the stairs behind Quillon Marbeck.

            Quillon Marbeck counted the bells.

            Ysolde Farran closed the shutters of the salt orchard.
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
    void start_logsCountsAndChoicesOnly() throws Exception {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), BOOK);
        final TestProject project = project(book, brief("en", "uk"));
        final RunRequest request =
                new RunRequest(project.id(), ReviewMode.UNATTENDED, WINDOW, MODEL, new ModelSelection(PROVIDER, MODEL));
        final TranslationJob job = dataOf(
                TranslationEngineImplTest.engine(project, ollamaProvider()).newJob(request, translations()));

        job.run();

        final int entries = glossarySize(project);
        assertThat(Files.readString(book)).contains("Quillon Marbeck", "Ysolde Farran", "salt orchard");
        assertThat(entries).isPositive();
        assertThat(infoAndAbove())
                .filteredOn(line -> line.startsWith("Run start "))
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("model=" + MODEL)
                        .contains("providerKind=OLLAMA")
                        .contains("window=16384")
                        .contains("dial=FAST")
                        .contains("batch=8")
                        .contains("mode=UNATTENDED")
                        .contains("names pass entries=" + entries + " proposed=" + entries));
        assertThat(infoAndAbove()).noneMatch(line -> BOOK_WORDS.stream().anyMatch(line::contains));
    }

    private static InMemoryProviderConfigs ollamaProvider() {
        final InMemoryProviderConfigs providers = new InMemoryProviderConfigs();
        providers.save(new ProviderConfig(
                PROVIDER,
                ProviderKind.OLLAMA,
                URI.create("http://127.0.0.1:11434"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5)));
        return providers;
    }

    // One batch of the eight paragraphs, then plain drafts for any call the run makes again on its own.
    private static ScriptedChatModel translations() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        BATCH,
                        batchReply(items(IntStream.rangeClosed(1, 8)
                                .mapToObj(id -> entry(Integer.toString(id), "Квіллон пройшов соляний сад " + id + "."))
                                .toArray(String[]::new))));
        return model.answerTimes(
                20, Result.ok(new ChatResponse(targetReply("Квіллон пройшов соляний сад."), FinishReason.STOP)));
    }

    private static int glossarySize(final TestProject project) {
        final List<GlossaryEntry> held = Objects.requireNonNull(
                project.stores().glossary().all(project.id()).data(), "glossary");
        return held.size();
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
}
