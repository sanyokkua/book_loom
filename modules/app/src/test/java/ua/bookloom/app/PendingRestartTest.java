package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The next copy is started once, after shutdown, and only when a restart asked for it. */
class PendingRestartTest {

    private final List<String> started = new ArrayList<>();

    @Test
    void spawnIfAsked_requested_startsTheCommand() {
        final PendingRestart pending = new PendingRestart();
        pending.request(List.of("/app/BookLoom", "--x"));

        pending.spawnIfAsked(command -> {
            started.addAll(command);
            return Optional.empty();
        });

        assertThat(started).containsExactly("/app/BookLoom", "--x");
    }

    @Test
    void spawnIfAsked_nothingRequested_startsNothing() {
        new PendingRestart().spawnIfAsked(command -> {
            started.addAll(command);
            return Optional.empty();
        });

        assertThat(started).isEmpty();
    }

    @Test
    void request_second_isRefused() {
        final PendingRestart pending = new PendingRestart();
        pending.request(List.of("a"));

        assertThat(pending.request(List.of("b"))).isFalse();
        assertThat(pending.requested()).contains(List.of("a"));
    }

    @Test
    void spawnIfAsked_processesThrow_doesNotThrow() {
        final PendingRestart pending = new PendingRestart();
        pending.request(List.of("a"));

        assertThatCode(() -> pending.spawnIfAsked(command -> {
                    throw new IllegalStateException("no");
                }))
                .doesNotThrowAnyException();
    }

    // IF the next copy were not told where the window was, THEN Restart now would bring it back at the default size.
    @Test
    void spawnIfAsked_windowKept_givesItToTheNextCopy() {
        final PendingRestart pending = new PendingRestart();
        pending.request(List.of("/app/BookLoom"));
        pending.keepWindow("40 60 1300 820 false");
        final Map<String, String> environment = new HashMap<>();

        pending.spawnIfAsked(new Processes() {
            @Override
            public Optional<Process> start(final List<String> command) {
                return Optional.empty();
            }

            @Override
            public Optional<Process> start(final List<String> command, final Map<String, String> extra) {
                environment.putAll(extra);
                return Optional.empty();
            }
        });

        assertThat(environment).containsExactly(Map.entry(WindowPlacement.ENVIRONMENT, "40 60 1300 820 false"));
    }
}
