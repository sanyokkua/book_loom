package ua.bookloom.util.paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The resolved-paths record's invariants.
 *
 * <p>Mechanical: these assert the type's construction rules, not a product
 * requirement — the requirements about <em>where</em> the paths point are covered by {@link AppPathsResolverTest}.
 */
class AppPathsTest {

    private static final Path DATA_DIR = Path.of("/tmp/bookloom-test").toAbsolutePath();
    private static final Path LOG_DIR = DATA_DIR.resolve("logs");

    @Test
    void of_twoRoots_derivesTheLockAndDatabaseFiles() {
        final AppPaths paths = AppPaths.of(DATA_DIR, LOG_DIR);

        assertThat(paths.lockFile()).isEqualTo(DATA_DIR.resolve("bookloom.lock"));
        assertThat(paths.databaseFile()).isEqualTo(DATA_DIR.resolve("bookloom.db"));
        assertThat(paths.onNetworkFilesystem()).isFalse();
    }

    // A relative data dir would resolve against the working directory, which for a double-clicked application is
    // wherever the launcher happened to start. That is the "database written somewhere arbitrary" failure the
    // resolver exists to prevent, so the type refuses to represent it at all.
    @Test
    void constructor_relativeDataDir_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AppPaths.of(Path.of("bookloom"), Path.of("bookloom/logs")))
                .withMessageContaining("absolute");
    }

    @Test
    void constructor_relativeLogDir_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AppPaths.of(DATA_DIR, Path.of("logs")))
                .withMessageContaining("absolute");
    }

    // Deriving the two files rather than accepting them is what stops the lock a second launch tests drifting away
    // from the database that lock exists to protect, so the direct constructor refuses an inconsistent pair.
    @Test
    void constructor_lockFileOutsideTheDataDir_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AppPaths(
                        DATA_DIR,
                        LOG_DIR,
                        Objects.requireNonNull(DATA_DIR.getParent()).resolve("bookloom.lock"),
                        DATA_DIR.resolve("bookloom.db"),
                        false))
                .withMessageContaining("bookloom.lock");
    }

    @Test
    void constructor_databaseFileOutsideTheDataDir_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AppPaths(
                        DATA_DIR,
                        LOG_DIR,
                        DATA_DIR.resolve("bookloom.lock"),
                        Objects.requireNonNull(DATA_DIR.getParent()).resolve("bookloom.db"),
                        false))
                .withMessageContaining("bookloom.db");
    }

    // The EC-ENV-8 flag's propagation. The probe itself needs a real network share and cannot be provoked
    // hermetically; what can be proven is that a flagged value survives intact to whoever emits the warning.
    @Test
    void withNetworkFilesystem_flagSet_copiesEveryPathUnchanged() {
        final AppPaths flagged = AppPaths.of(DATA_DIR, LOG_DIR).withNetworkFilesystem(true);

        assertThat(flagged.onNetworkFilesystem()).isTrue();
        assertThat(flagged.dataDir()).isEqualTo(DATA_DIR);
        assertThat(flagged.logDir()).isEqualTo(LOG_DIR);
        assertThat(flagged.lockFile()).isEqualTo(DATA_DIR.resolve("bookloom.lock"));
    }

    @Test
    void withNetworkFilesystem_unchangedFlag_returnsTheSameInstance() {
        final AppPaths paths = AppPaths.of(DATA_DIR, LOG_DIR);

        assertThat(paths.withNetworkFilesystem(false)).isSameAs(paths);
    }
}
