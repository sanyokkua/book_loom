package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.ProjectServices;
import ua.bookloom.pipeline.TestBooks;

/** A new book starts at the quality the person chose in Settings › Automation (finalize 7.4). */
class ProjectServiceDefaultDialTest {

    @TempDir
    private Path tempDir;

    private Injector injector;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()));
    }

    @Test
    void importBook_maxStored_briefDialMax() {
        final SettingsRepository settings = injector.getInstance(SettingsRepository.class);
        settings.put(AppSetting.DEFAULT_DIAL, QualityDial.MAX);

        assertThat(importWith(settings)).isEqualTo(QualityDial.MAX);
    }

    @Test
    void importBook_nothingStored_balanced() {
        assertThat(importWith(injector.getInstance(SettingsRepository.class))).isEqualTo(QualityDial.BALANCED);
    }

    @Test
    void importBook_settingsUnreadable_balanced() {
        assertThat(importWith(new UnreadableSettings())).isEqualTo(QualityDial.BALANCED);
    }

    // Existing behaviour, pinned: an untargeted project is made again from the book, so it takes the new default.
    @Test
    void create_sameBookUntargetedTwice_secondTakesTheNewDefault() {
        final SettingsRepository settings = injector.getInstance(SettingsRepository.class);
        final ProjectServiceImpl service = serviceOver(settings);
        final Path book = TestBooks.txt(tempDir.resolve("again.txt"), "It was a dark night.\n\nThe rain kept falling.");
        final ImportedBook first = Objects.requireNonNull(service.create(book).data());
        settings.put(AppSetting.DEFAULT_DIAL, QualityDial.MAX);

        final ImportedBook second = Objects.requireNonNull(service.create(book).data());

        assertThat(Objects.requireNonNull(first.brief()).dial()).isEqualTo(QualityDial.BALANCED);
        assertThat(second.projectId()).isEqualTo(first.projectId());
        assertThat(Objects.requireNonNull(second.brief()).dial()).isEqualTo(QualityDial.MAX);
    }

    private ProjectServiceImpl serviceOver(final SettingsRepository settings) {
        return ProjectServices.service(
                injector,
                injector.getInstance(DocumentPort.class),
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                new OpenProjects(),
                settings);
    }

    private QualityDial importWith(final SettingsRepository settings) {
        final ProjectServiceImpl service = serviceOver(settings);
        final Path book = TestBooks.txt(tempDir.resolve("book.txt"), "It was a dark night.\n\nThe rain kept falling.");
        final ImportedBook imported =
                Objects.requireNonNull(service.create(book).data());
        return Objects.requireNonNull(imported.brief()).dial();
    }

    /** A settings store whose every read fails. */
    private static final class UnreadableSettings implements SettingsRepository {

        private static final AppError BROKEN = AppError.of(ErrorCode.internal, "Storage failure", "unreadable");

        @Override
        public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
            return Result.err(BROKEN);
        }

        @Override
        public <T> Result<T> put(final AppSetting<T> setting, final T value) {
            return Result.err(BROKEN);
        }

        @Override
        public Result<Map<String, String>> all() {
            return Result.err(BROKEN);
        }

        @Override
        public Result<Boolean> remove(final AppSetting<?> setting) {
            return Result.err(BROKEN);
        }
    }
}
