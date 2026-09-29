package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;

/** A refused export is one failure, so the log holds one warning for it. */
class ExportJobLogTest {

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExportJobImpl.class);
    private ExportServiceImpl service;
    private ProjectRepository projects;

    @BeforeEach
    void setUp() {
        final var injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ExportServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void run_refusedExport_logsTheRefusalOnce() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Source.");
        projects.save(new Project("p1", source, BookFormat.MARKDOWN, "hash", BookBrief.defaults("en")));
        final ExportJob job = Objects.requireNonNull(
                service.newExport(new ExportRequest("p1", tempDir.resolve("Book.uk.md"), true, Set.of(), false), null)
                        .data(),
                "export job");

        final Result<ExportReport> result = job.run();

        assertThat(Objects.requireNonNull(result.error(), "refusal").code()).isEqualTo(ErrorCode.validation);
        assertThat(appender.list)
                .filteredOn(line -> line.getLevel().isGreaterOrEqual(Level.WARN))
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .startsWith("Refused export check=")
                .endsWith("code=validation");
    }
}
