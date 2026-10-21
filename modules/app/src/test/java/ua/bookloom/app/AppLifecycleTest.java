package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.persistence.SqlitePersistenceModule;

/** Closing releases only what phase 2 opened, so a failed start is never followed by a second open at shutdown. */
class AppLifecycleTest {

    @TempDir
    private Path dataDir;

    @Test
    void close_afterPhaseTwoFailedToOpenTheDatabase_opensNothingAndDoesNotThrow() {
        final Path file = dataDir.resolve("missing-folder").resolve("bookloom.db");
        final Injector injector = Guice.createInjector(new SqlitePersistenceModule(file, Clock.systemUTC()));
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        assertThatThrownBy(() -> lifecycle.phaseTwo(injector)).isInstanceOf(ProvisionException.class);

        assertThatCode(lifecycle::close).doesNotThrowAnyException();
        assertThat(file).doesNotExist();
    }

    @Test
    void close_afterPhaseTwoOpenedTheDatabase_closesThatDatabase() {
        final Injector injector =
                Guice.createInjector(new SqlitePersistenceModule(dataDir.resolve("bookloom.db"), Clock.systemUTC()));
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        assertThat(injector.getInstance(ProjectRepository.class).delete("none").isOk())
                .isTrue();

        lifecycle.close();

        assertThat(injector.getInstance(ProjectRepository.class).delete("none").isErr())
                .isTrue();
    }
}
