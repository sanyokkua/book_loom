package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;

/**
 * The book index an opened project holds: built when a book is imported, served by {@code OpenProjects.index},
 * replaced when the project's book is put again and dropped when it is removed or closed. A project put without an
 * import (as many tests do) gets its index built from the stored document on the first call and kept.
 */
class OpenProjectsIndexTest {

    private static final String PROJECT = "p1";

    @TempDir
    private Path tempDir;

    private ProjectService service;
    private OpenProjects openProjects;
    private ListAppender<ILoggingEvent> logEvents;
    private Logger pipelineLogger;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new PersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()));
        service = injector.getInstance(ProjectServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
        logEvents = new ListAppender<>();
        logEvents.start();
        pipelineLogger = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline");
        pipelineLogger.addAppender(logEvents);
    }

    @AfterEach
    void tearDown() {
        pipelineLogger.detachAppender(logEvents);
    }

    // IF a book is imported, THEN its index is held for the project and reads the imported document.
    @Test
    void index_afterImportBook_isHeldAndReadsTheImportedDocument() {
        final String id = importedId("It was a dark night.\n\nThe rain kept falling. The rain stopped.");

        final BookIndex index = openProjects.index(id);

        assertThat(index).isNotNull();
        assertThat(index.size())
                .isEqualTo(Objects.requireNonNull(openProjects.get(id)).units().stream()
                        .mapToInt(unit -> unit.segments().size())
                        .sum());
        assertThat(index.stats("rain").total()).isEqualTo(2);
        assertThat(index.stats("rain").segments()).isEqualTo(1);
    }

    // IF a book is imported, THEN the build is done by the import (one INFO line with its size) and not left to the
    // first reader.
    @Test
    void importBook_book_logsTheIndexBuildAtInfoWithItsSize() {
        final String id = importedId("It was a dark night.\n\nThe rain kept falling.");

        assertThat(logEvents.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("book index built")
                        .contains("segments="
                                + Objects.requireNonNull(openProjects.index(id)).size()));
    }

    @Test
    void index_calledTwice_isTheSameInstance() {
        final String id = importedId("It was a dark night.");

        assertThat(openProjects.index(id)).isSameAs(openProjects.index(id));
    }

    @Test
    void index_afterCloseOrRemove_isGone() {
        final String closed = importedId("It was a dark night.");
        assertThat(service.close(closed).isOk()).isTrue();
        openProjects.put(PROJECT, IndexTestBooks.oneUnit("Ivo walked."));
        openProjects.remove(PROJECT);

        assertThat(openProjects.index(closed)).isNull();
        assertThat(openProjects.index(PROJECT)).isNull();
    }

    @Test
    void index_unknownProject_isNull() {
        assertThat(openProjects.index("missing")).isNull();
    }

    // IF a project's book is put again, THEN the old index is dropped and the next one reads the new book.
    @Test
    void index_afterASecondPut_readsTheNewDocument() {
        openProjects.put(PROJECT, IndexTestBooks.oneUnit("Ivo walked."));
        final BookIndex first = openProjects.index(PROJECT);

        openProjects.put(PROJECT, IndexTestBooks.oneUnit("Ann ran.", "Ann sat."));
        final BookIndex second = openProjects.index(PROJECT);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull().isNotSameAs(first);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.stats("ivo").total()).isZero();
        assertThat(second.stats("ann").total()).isEqualTo(2);
    }

    // IF a document was put with no import, THEN the first call builds the index from it and later calls reuse it.
    @Test
    void index_projectPutWithoutImport_isBuiltOnFirstCallAndKept() {
        final Document book = IndexTestBooks.oneUnit("Ivo walked.", "Ivo ran.");
        openProjects.put(PROJECT, book);

        final BookIndex index = openProjects.index(PROJECT);

        assertThat(index).isNotNull().isSameAs(openProjects.index(PROJECT));
        assertThat(index.stats("ivo").total()).isEqualTo(2);
        assertThat(index.story()).containsExactly(0, 1);
    }

    private String importedId(final String text) {
        final var result = service.importBook(TestBooks.txt(tempDir.resolve("book.txt"), text));
        assertThat(result.isOk()).as("import result %s", result.error()).isTrue();
        return Objects.requireNonNull(
                Objects.requireNonNull(result.data(), "imported").projectId());
    }
}
