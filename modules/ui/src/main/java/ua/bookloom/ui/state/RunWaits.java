package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Predicate;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Carries on once the run reaches a state, following the mirror's run state instead of blocking the FX thread; used
 * where another book or project opens only after the run has stopped. FX thread only.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunWaits {

    /**
     * Runs {@code then} now when the run's state already satisfies {@code done}, else at the first change that does.
     *
     * @param mirror the run's state
     * @param done the states to wait for
     * @param then what to do once one is reached; runs once
     */
    static void when(final StateMirror mirror, final Predicate<RunState> done, final Runnable then) {
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(done, "done");
        Objects.requireNonNull(then, "then");
        final RunState now = mirror.runState().get();
        if (done.test(now)) {
            log.debug("the run is already {}: carrying on", now);
            then.run();
            return;
        }
        log.debug("waiting for the run: now {}", now);
        mirror.runState().addListener(new EndWatcher(mirror, done, then));
    }

    /** Carries on from the first state change that satisfies the condition, then stops watching. */
    private static final class EndWatcher implements ChangeListener<@Nullable RunState> {

        private final StateMirror mirror;
        private final Predicate<RunState> done;
        private final Runnable then;

        EndWatcher(final StateMirror mirror, final Predicate<RunState> done, final Runnable then) {
            this.mirror = mirror;
            this.done = done;
            this.then = then;
        }

        @Override
        public void changed(
                final ObservableValue<? extends @Nullable RunState> observed,
                final @Nullable RunState was,
                final @Nullable RunState now) {
            if (now == null || !done.test(now)) {
                return;
            }
            mirror.runState().removeListener(this);
            log.debug("the run reached {}: carrying on", now);
            then.run();
        }
    }
}
