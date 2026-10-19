package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The keep-awake command per operating system, and its start and stop: idempotent, ordered and never fatal. */
class OsKeepAwakeTest {

    private static final long PID = 4242;

    @ParameterizedTest
    @CsvSource({
        "Mac OS X, true, caffeinate -i -m -w 4242",
        "Linux, true, systemd-inhibit --what=idle:sleep --who=BookLoom --why=A translation is running --mode=block"
                + " tail --pid=4242 -f /dev/null",
        "Linux, false, -",
        "Windows 11, true, -"
    })
    void forOs_eachSystem_choosesItsCommand(final String os, final boolean inhibitInstalled, final String expected) {
        final Optional<List<String>> command = KeepAwakeCommand.forOs(os, PID, program -> inhibitInstalled);

        assertThat(command.map(parts -> String.join(" ", parts)).orElse("-")).isEqualTo(expected);
    }

    @Test
    void start_twiceThenStop_startsOneProcessAndEndsIt() {
        final List<FakeProcess> started = new CopyOnWriteArrayList<>();
        final OsKeepAwake keepAwake = new OsKeepAwake(List.of("caffeinate"), command -> {
            final FakeProcess process = new FakeProcess();
            started.add(process);
            return Optional.of(process);
        });

        keepAwake.start();
        keepAwake.start();
        keepAwake.stop();
        keepAwake.stop();
        settle(keepAwake);

        assertThat(started).hasSize(1);
        assertThat(started.getFirst().isAlive()).isFalse();
    }

    // IF a missing command failed the run, THEN a computer without caffeinate could not translate at all.
    @Test
    void start_commandMissing_changesNothing() {
        final OsKeepAwake keepAwake = new OsKeepAwake(List.of("caffeinate"), command -> Optional.empty());

        keepAwake.start();
        keepAwake.stop();
        settle(keepAwake);

        assertThat(keepAwake).isNotNull();
    }

    @Test
    void start_noCommandForThisOs_startsNothing() {
        final List<List<String>> asked = new CopyOnWriteArrayList<>();
        final OsKeepAwake keepAwake = new OsKeepAwake(null, command -> {
            asked.add(command);
            return Optional.empty();
        });

        keepAwake.start();
        settle(keepAwake);

        assertThat(asked).isEmpty();
    }

    // The worker runs each request in order; one more request that has finished means every earlier one has too.
    private static void settle(final OsKeepAwake keepAwake) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        keepAwake.afterPending(done::countDown);
        try {
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError(cause);
        }
    }

    /** A process that runs until destroyed. */
    private static final class FakeProcess extends Process {

        private volatile boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return PID;
        }
    }
}
