package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.util.paths.AppPaths;

/**
 * The single-instance lock — startup step 4, and the guard that has to be in place before anything can open the
 * database it protects.
 *
 * <p>Exercised through {@link SingleInstanceLock} directly rather than through {@link Launcher#main}. The launcher's
 * refusal path ends in {@code System.exit}, so driving it in-process would terminate the test JVM mid-suite; what
 * matters — that the second acquirer is refused, and that it touches nothing — is a property of the lock itself.
 */
class SingleInstanceLockTest {

    @TempDir
    private Path dataDir;

    private static ua.bookloom.api.AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    // The system SHALL enforce a single-instance lock held on `bookloom.lock` in the data
    // directory, so a second launch is refused while the first holds it.
    @Test
    void acquire_lockAlreadyHeld_isRefusedAsBusy() {
        final AppPaths paths = AppPaths.of(dataDir, dataDir.resolve("logs"));

        try (SingleInstanceLock first = Objects.requireNonNull(
                SingleInstanceLock.acquire(paths.lockFile()).data())) {

            final Result<SingleInstanceLock> second = SingleInstanceLock.acquire(paths.lockFile());

            assertThat(second.isErr()).isTrue();
            assertThat(errorOf(second).code()).isEqualTo(ErrorCode.busy);
            assertThat(errorOf(second).retryable())
                    .as("the second launch is told to use the running window, not to retry in a loop")
                    .isFalse();
            assertThat(errorOf(second).title()).isEqualTo("BookLoom is already running");
            assertThat(first).isNotNull();
        }
    }

    /**
     * The half of EC-ENV-3 that is easy to leave unasserted: the refused launch must not merely fail, it must fail
     * <em>before touching anything</em>. A second process that created or opened the database on its way to being
     * refused would have already done the damage the lock exists to prevent.
     */
    // WHEN a second instance is refused, the system SHALL NOT open or create the database.
    @Test
    void acquire_lockAlreadyHeld_neverCreatesTheDatabaseFile() {
        final AppPaths paths = AppPaths.of(dataDir, dataDir.resolve("logs"));

        try (SingleInstanceLock first = Objects.requireNonNull(
                SingleInstanceLock.acquire(paths.lockFile()).data())) {

            SingleInstanceLock.acquire(paths.lockFile());

            assertThat(paths.databaseFile()).doesNotExist();
            assertThat(first).isNotNull();
        }
    }

    @Test
    void acquire_afterTheHolderReleases_succeeds() {
        final Path lockFile = dataDir.resolve(AppPaths.LOCK_FILE_NAME);

        final SingleInstanceLock first =
                Objects.requireNonNull(SingleInstanceLock.acquire(lockFile).data());
        first.close();

        final Result<SingleInstanceLock> second = SingleInstanceLock.acquire(lockFile);

        assertThat(second.isOk())
                .as("closing the first instance must leave the application startable again")
                .isTrue();
        Objects.requireNonNull(second.data()).close();
    }

    /**
     * EC-ENV-4's lock-level half. That a development and a production run <em>resolve</em> to disjoint folders is
     * proved against the resolver; what this adds is that two distinct lock files really can be held at the same
     * time, which is what makes running both builds simultaneously safe rather than merely intended.
     */
    // WHERE a development and a production build run at once, the system SHALL let both
    // acquire their own lock, because the folders — and therefore the lock files — are separate.
    @Test
    void acquire_twoDistinctDataDirectories_bothSucceedSimultaneously() throws Exception {
        final Path devDir = Files.createDirectory(dataDir.resolve("BookLoom-Dev"));
        final Path prodDir = Files.createDirectory(dataDir.resolve("BookLoom"));

        try (SingleInstanceLock dev =
                        Objects.requireNonNull(SingleInstanceLock.acquire(devDir.resolve(AppPaths.LOCK_FILE_NAME))
                                .data());
                SingleInstanceLock prod =
                        Objects.requireNonNull(SingleInstanceLock.acquire(prodDir.resolve(AppPaths.LOCK_FILE_NAME))
                                .data())) {

            assertThat(dev).isNotNull();
            assertThat(prod).isNotNull();
        }
    }

    @Test
    void acquire_lockFilePathUnusable_isAValidationFailure() throws Exception {
        // A regular file standing where the data directory should be: opening a lock file beneath it cannot work
        // on any platform, and the launcher must report that rather than proceeding without a lock.
        final Path blocker = dataDir.resolve("blocker");
        Files.writeString(blocker, "not a directory");

        final Result<SingleInstanceLock> result = SingleInstanceLock.acquire(blocker.resolve("bookloom.lock"));

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }
}
