package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * A database file the person cannot write stops the real start with the startup message, not with an exception no one
 * sees: the window's own init path builds the injector, the database opens while it does, and the failure reaches the
 * startup-message presenter exactly once with the data folder and the advice in it.
 */
class StartupStorageFailureTest {

    @TempDir
    private Path dataDir;

    private final List<AppError> shown = new ArrayList<>();
    private final List<Integer> exitCodes = new ArrayList<>();

    @AfterEach
    void restoreWritable() throws Exception {
        Files.setPosixFilePermissions(dataDir.resolve("bookloom.db"), PosixFilePermissions.fromString("rw-r--r--"));
        FxToolkit.cleanupStages();
    }

    @Test
    void init_readOnlyDatabaseFile_routesTheFailureToTheStartupMessageOnce() throws Exception {
        final AppPaths paths = AppPaths.of(dataDir, Files.createDirectories(dataDir.resolve("logs")));
        final SqliteDatabase existing = Objects.requireNonNull(
                SqliteDatabase.open(paths.databaseFile(), Clock.systemUTC()).data());
        existing.close();
        Files.setPosixFilePermissions(paths.databaseFile(), PosixFilePermissions.fromString("r--r--r--"));
        StartupContext.publish(
                new StartupContext(paths, AppEnvironment.DEV, ReviewModeResolver.resolve(name -> null, name -> null)));
        FxToolkit.registerPrimaryStage();
        final BookLoomApplication app = new BookLoomApplication((error, code) -> {
            shown.add(error);
            exitCodes.add(code);
        });

        assertThatThrownBy(app::init).isInstanceOf(RuntimeException.class);

        assertThat(shown).hasSize(1);
        assertThat(exitCodes).containsExactly(1);
        assertThat(shown.get(0).code()).isEqualTo(ErrorCode.internal);
        assertThat(shown.get(0).message())
                .contains(dataDir.toString())
                .containsAnyOf(
                        "Check the permissions of the data folder and start BookLoom again.",
                        "Перевірте дозволи теки даних і запустіть BookLoom знову.");
    }
}
