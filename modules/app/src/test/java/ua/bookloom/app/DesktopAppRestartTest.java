package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** A restart only records the next copy's command and closes the window; the launcher starts it after shutdown. */
class DesktopAppRestartTest {

    private final List<String> events = new ArrayList<>();
    private final PendingRestart pending = new PendingRestart();

    private DesktopAppRestart restart(final FakeProcessInfo info, final Runnable exit) {
        return new DesktopAppRestart(pending, () -> info, exit);
    }

    @Test
    void restart_commandKnown_recordsThenExits_noSpawnYet() {
        final Result<Boolean> result = restart(
                        FakeProcessInfo.of("/usr/bin/java", "-jar", "a.jar"), () -> events.add("exit"))
                .restart();

        assertThat(result.data()).isTrue();
        assertThat(pending.requested()).contains(List.of("/usr/bin/java", "-jar", "a.jar"));
        assertThat(events).containsExactly("exit");
    }

    @Test
    void restart_noCommand_errorAndNoExit() {
        final Result<Boolean> result =
                restart(FakeProcessInfo.of(null), () -> events.add("exit")).restart();

        final AppError error = Objects.requireNonNull(result.error());
        assertThat(error.code()).isEqualTo(ErrorCode.internal);
        assertThat(error.title()).isEqualTo("BookLoom could not restart");
        assertThat(pending.requested()).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void restart_calledTwice_exitsOnce() {
        final DesktopAppRestart restart =
                restart(FakeProcessInfo.of("/usr/bin/java", "-jar", "a.jar"), () -> events.add("exit"));

        restart.restart();
        final Result<Boolean> second = restart.restart();

        assertThat(second.data()).isTrue();
        assertThat(events).containsExactly("exit");
    }

    @Test
    void restart_exitThrows_errorKeepsCause() {
        final IllegalStateException boom = new IllegalStateException("toolkit gone");
        final Result<Boolean> result = restart(FakeProcessInfo.of("/usr/bin/java", "-jar", "a.jar"), () -> {
                    throw boom;
                })
                .restart();

        final AppError error = Objects.requireNonNull(result.error());
        assertThat(error.code()).isEqualTo(ErrorCode.internal);
        assertThat(error.cause()).isSameAs(boom);
    }
}
