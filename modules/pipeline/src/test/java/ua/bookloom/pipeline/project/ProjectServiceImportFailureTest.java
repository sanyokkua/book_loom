package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.UuidIdSource;

/** An import that fails after the book was opened leaves no open book and no open document behind. */
class ProjectServiceImportFailureTest {

    @TempDir
    private Path tempDir;

    // IF storing the segments throws, THEN the import is an internal error, the opened document is closed and
    // nothing is registered as open.
    @Test
    void create_storeThrows_closesTheDocumentAndRegistersNothing() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()));
        final List<Document> closed = new CopyOnWriteArrayList<>();
        final List<String> projectIds = new CopyOnWriteArrayList<>();
        final OpenProjects openProjects = injector.getInstance(OpenProjects.class);
        final ProjectServiceImpl service = serviceWhoseStoreBreaks(injector, closed, projectIds);
        final Path book = TestBooks.txt(tempDir.resolve("book.txt"), "It was a dark night.\n\nThe rain kept falling.");

        final Result<ImportedBook> imported = service.create(book);

        assertThat(imported.isErr()).isTrue();
        assertThat(imported.error())
                .isNotNull()
                .satisfies(error -> assertThat(error.code()).isEqualTo(ErrorCode.internal));
        assertThat(closed).hasSize(1);
        assertThat(projectIds).hasSize(1);
        assertThat(openProjects.get(projectIds.getFirst())).isNull();
    }

    // IF the segment store refuses the book (a storage error, as SQLite answers a duplicate segment id), THEN the
    // project row written before it is removed again, so no empty project is left in the list.
    @Test
    void create_storeRefusesTheSegments_leavesNoProjectBehind() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()));
        final ProjectServiceImpl service =
                service(injector, refusingSaves(injector.getInstance(SegmentRepository.class)));
        final Path book = TestBooks.txt(tempDir.resolve("book.txt"), "It was a dark night.\n\nThe rain kept falling.");

        final Result<ImportedBook> imported = service.create(book);

        assertThat(imported.isErr()).isTrue();
        assertThat(service.list().data()).isEmpty();
    }

    private static SegmentRepository refusingSaves(final SegmentRepository real) {
        return (SegmentRepository) Proxy.newProxyInstance(
                SegmentRepository.class.getClassLoader(),
                new Class<?>[] {SegmentRepository.class},
                (proxy, method, args) -> {
                    if ("saveAll".equals(method.getName())) {
                        return Result.err(AppError.of(ErrorCode.internal, "Storage write failed", "duplicate id"));
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static ProjectServiceImpl service(final Injector injector, final SegmentRepository segments) {
        return new ProjectServiceImpl(
                injector.getInstance(BookInspector.class),
                injector.getInstance(DocumentPort.class),
                injector.getInstance(ProjectRepository.class),
                segments,
                injector.getInstance(OpenProjects.class),
                Clock.systemUTC(),
                new UuidIdSource(),
                injector.getInstance(RunRepository.class),
                new ProjectCopies(
                        injector.getInstance(GlossaryRepository.class),
                        injector.getInstance(LexiconRepository.class),
                        injector.getInstance(SegmentRepository.class),
                        new UuidIdSource()),
                injector.getInstance(SettingsRepository.class));
    }

    // Records each closed document and each saved project id; the segment store throws when asked to save.
    private static ProjectServiceImpl serviceWhoseStoreBreaks(
            final Injector injector, final List<Document> closed, final List<String> projectIds) {
        return new ProjectServiceImpl(
                injector.getInstance(BookInspector.class),
                watching(DocumentPort.class, injector.getInstance(DocumentPort.class), (method, args) -> {
                    if ("close".equals(method.getName())) {
                        closed.add((Document) args[0]);
                    }
                }),
                watching(ProjectRepository.class, injector.getInstance(ProjectRepository.class), (method, args) -> {
                    if ("save".equals(method.getName())) {
                        projectIds.add(((Project) args[0]).id());
                    }
                }),
                watching(SegmentRepository.class, injector.getInstance(SegmentRepository.class), (method, args) -> {
                    if ("saveAll".equals(method.getName())) {
                        throw new IllegalStateException("the store broke");
                    }
                }),
                injector.getInstance(OpenProjects.class),
                Clock.systemUTC(),
                new UuidIdSource(),
                injector.getInstance(RunRepository.class),
                new ProjectCopies(
                        injector.getInstance(GlossaryRepository.class),
                        injector.getInstance(LexiconRepository.class),
                        injector.getInstance(SegmentRepository.class),
                        new UuidIdSource()),
                injector.getInstance(SettingsRepository.class));
    }

    private interface Spy {
        void saw(Method method, Object[] args);
    }

    // The real collaborator behind a proxy that lets the spy see (and break) each call first.
    private static <T> T watching(final Class<T> type, final T real, final Spy spy) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            spy.saw(method, args);
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }));
    }
}
