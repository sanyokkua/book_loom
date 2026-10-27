package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.project.ProjectWorld.changeOf;
import static ua.bookloom.pipeline.project.ProjectWorld.saved;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CountingIdSource;
import ua.bookloom.pipeline.ProjectServices;
import ua.bookloom.pipeline.TestBooks;

/** Making a second translation of a used pair stores the new brief and the typed name together, or nothing. */
class ProjectServiceMakeSecondTranslationTest {

    private static final BookBrief UK = BookBrief.defaults("en").withLanguages("en", "uk");

    @TempDir
    private Path tempDir;

    private ProjectWorld world;
    private Path book;
    private String first;
    private String second;

    @BeforeEach
    void setUp() {
        world = new ProjectWorld();
        book = TestBooks.markdown(
                tempDir.resolve("earth-gravity.md"), "It was a dark night.\n\nThe rain kept falling.\n");
        first = world.create(book);
        world.translate(first, "en", "uk");
        second = world.create(book);
    }

    @Test
    void makeSecondTranslation_freeName_storesBriefAndTypedNameAsTheUsersOwn() {
        final Project made = saved(world.service.makeSecondTranslation(second, UK, "  Earth Gravity · 26b "));

        assertThat(made.name()).isEqualTo("Earth Gravity · 26b");
        assertThat(made.nameOrigin()).isEqualTo(NameOrigin.USER);
        assertThat(world.stored(second).brief().targetLanguage()).isEqualTo("uk");
        assertThat(world.stored(second).name()).isEqualTo("Earth Gravity · 26b");
        assertThat(world.stored(second).nameOrigin()).isEqualTo(NameOrigin.USER);
    }

    // IF the name is taken by another project of this book in another case, THEN nothing is stored.
    @ParameterizedTest
    @ValueSource(strings = {"Земне тяжіння", "ЗЕМНЕ ТЯЖІННЯ", "  земне тяжіння  "})
    void makeSecondTranslation_nameTakenInTheSameBook_answersNameInUseAndStoresNothing(final String typed) {
        saved(world.service.rename(first, "Земне тяжіння"));
        final Project before = world.stored(second);

        final ProjectChange change = changeOf(world.service.makeSecondTranslation(second, UK, typed));

        assertThat(change).isInstanceOf(ProjectChange.NameInUse.class);
        assertThat(((ProjectChange.NameInUse) change).holder().id()).isEqualTo(first);
        assertThat(world.stored(second).name()).isEqualTo(before.name());
        assertThat(world.stored(second).brief()).isEqualTo(before.brief());
        assertThat(world.stored(second).brief().targetLanguage()).isNull();
    }

    @Test
    void makeSecondTranslation_nameUsedInAnotherBook_isSaved() {
        final Path other = TestBooks.markdown(tempDir.resolve("other.md"), "Another text altogether.\n");
        final String otherId = world.create(other);
        saved(world.service.rename(otherId, "Earth Gravity · 26b"));

        final Project made = saved(world.service.makeSecondTranslation(second, UK, "Earth Gravity · 26b"));

        assertThat(made.name()).isEqualTo("Earth Gravity · 26b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void makeSecondTranslation_blankName_answersValidationAndStoresNothing(final String blank) {
        final Result<ProjectChange> result = world.service.makeSecondTranslation(second, UK, blank);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(world.stored(second).brief().targetLanguage()).isNull();
        assertThat(world.stored(second).nameOrigin()).isEqualTo(NameOrigin.AUTO);
    }

    @Test
    void makeSecondTranslation_unknownProject_answersValidation() {
        final Result<ProjectChange> result = world.service.makeSecondTranslation("missing", UK, "Name");

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the one save fails, THEN the stored project keeps its old name and brief (no half change).
    @Test
    void makeSecondTranslation_saveFails_leavesTheStoredProjectUnchanged() {
        final Injector injector =
                Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule(), binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new CountingIdSource());
                });
        final ProjectRepository real = injector.getInstance(ProjectRepository.class);
        final AtomicBoolean failing = new AtomicBoolean();
        final ProjectServiceImpl service = ProjectServices.service(
                injector,
                injector.getInstance(DocumentPort.class),
                failingOnSave(real, failing),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(OpenProjects.class));
        final String firstId = idOf(service.create(book));
        saved(service.updateBrief(firstId, UK));
        final String secondId = idOf(service.create(book));
        final Project before =
                Objects.requireNonNull(real.find(secondId).data()).orElseThrow();
        failing.set(true);

        final Result<ProjectChange> result = service.makeSecondTranslation(secondId, UK, "Earth Gravity · 26b");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(real.find(secondId).data()).orElseThrow())
                .isEqualTo(before);
    }

    private static String idOf(final Result<ua.bookloom.api.pipeline.ImportedBook> created) {
        return Objects.requireNonNull(Objects.requireNonNull(created.data()).projectId());
    }

    // The real repository behind a proxy that answers every save with an error while the switch is on.
    private static ProjectRepository failingOnSave(final ProjectRepository real, final AtomicBoolean failing) {
        return (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class<?>[] {ProjectRepository.class},
                (proxy, method, args) -> {
                    if (failing.get() && "save".equals(method.getName())) {
                        return Result.err(AppError.of(ErrorCode.internal, "Disk full", "The save failed."));
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
