package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CallLedgerTestModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.PrepSummary;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.TestBooks;

/**
 * The names pass and the export each end with one INFO {@code prep summary} line that counts, by kind, the calls the
 * ledger recorded since the step began; a call recorded before it began is not the step's. Here, in the names pass's
 * package, because the names pass's invented book and model are package-private.
 */
class PrepSummaryTest extends NamesPassTestBase {

    private static final String LINE_START = "prep summary byKind[";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(PrepSummary.class);
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
        appender.stop();
    }

    // On the invented book the pass makes ten term-reading calls, one batched name review and two target-suggestion
    // batches; the call recorded before the pass began is not its own.
    @Test
    void namesPass_tenReadingsOneReviewAndTwoSuggestCalls_endsWithOnePrepSummaryLine() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final CallLedger ledger = fixture.injector.getInstance(CallLedger.class);
        ledger.record(new CallRecord(CallKind.DRAFT, null, Duration.ofSeconds(1), null, null));

        succeeded(fixture.pass().run(PROJECT, recording(model(), ledger), null, events::add));

        assertThat(prepLines())
                .singleElement()
                .asString()
                .startsWith(LINE_START)
                .contains("REVIEW_TERMS=1/0 ")
                .contains("SUGGEST_TARGETS=2/0 ")
                .contains("TERM_READING=10/0 ")
                .doesNotContain("DRAFT=")
                .contains(" secondsPerCall[REVIEW_TERMS=2.0 SUGGEST_TARGETS=2.0 TERM_READING=2.0]")
                .endsWith("] stage=names project=" + PROJECT);
    }

    @Test
    void export_endsWithAnEmptyByKindList() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                new PipelineModule(),
                new ReviewModeTestModule(),
                new CallLedgerTestModule());
        final String projectId = importBook(injector);

        Objects.requireNonNull(injector.getInstance(ExportService.class)
                        .newExport(new ExportRequest(projectId, tempDir.resolve("Book.uk.md"), false, Set.of()))
                        .data())
                .run();

        assertThat(prepLines()).containsExactly("prep summary byKind[] stage=export project=" + projectId);
    }

    // Every call the names pass sends is recorded as the gated model records it: its kind, no reported figures.
    private static ChatModel recording(final NamesPassModel answers, final CallLedger ledger) {
        return request -> {
            ledger.record(
                    new CallRecord(Objects.requireNonNull(request.callKind(), "kind"), null, PER_CALL, null, null));
            return answers.chat(request);
        };
    }

    private String importBook(final Injector injector) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.");
        final String id = Objects.requireNonNull(
                Objects.requireNonNull(injector.getInstance(ProjectService.class)
                                .create(source)
                                .data())
                        .projectId(),
                "project id");
        final ProjectRepository projects = injector.getInstance(ProjectRepository.class);
        final Project project = Objects.requireNonNull(projects.find(id).data()).orElseThrow();
        projects.save(project.withBrief(GlossaryServiceFixtures.withTarget(project.brief(), "uk")));
        return id;
    }

    private List<String> prepLines() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("prep summary "))
                .toList();
    }
}
