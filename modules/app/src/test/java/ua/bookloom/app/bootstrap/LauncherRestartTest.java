package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.app.PendingRestart;

/** The next copy starts only after the application returned and the lock was let go, never before. */
class LauncherRestartTest {

    @TempDir
    private Path dir;

    private final List<String> events = new ArrayList<>();
    private final AtomicBoolean lockFreeAtSpawn = new AtomicBoolean();

    private SingleInstanceLock held() {
        return Objects.requireNonNull(
                SingleInstanceLock.acquire(dir.resolve("bookloom.lock")).data());
    }

    @Test
    void runGuarded_restartAsked_spawnsAfterApplicationAndAfterLockClosed() {
        final PendingRestart pending = new PendingRestart();

        Launcher.runGuarded(
                held(),
                pending,
                command -> {
                    events.add("spawn");
                    final var other = SingleInstanceLock.acquire(dir.resolve("bookloom.lock"));
                    lockFreeAtSpawn.set(other.isOk());
                    if (other.isOk()) {
                        Objects.requireNonNull(other.data()).close();
                    }
                    return Optional.empty();
                },
                () -> {
                    events.add("application");
                    pending.request(List.of("/app/BookLoom"));
                });

        assertThat(events).containsExactly("application", "spawn");
        assertThat(lockFreeAtSpawn).isTrue();
    }

    @Test
    void runGuarded_noRestartAsked_spawnsNothing() {
        Launcher.runGuarded(
                held(),
                new PendingRestart(),
                command -> {
                    events.add("spawn");
                    return Optional.empty();
                },
                () -> events.add("application"));

        assertThat(events).containsExactly("application");
    }
}
