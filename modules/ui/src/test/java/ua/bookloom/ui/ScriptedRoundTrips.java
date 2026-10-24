package ua.bookloom.ui;

import static ua.bookloom.ui.ScriptedProjects.awaitGate;
import static ua.bookloom.ui.ScriptedProjects.awaitLatch;
import static ua.bookloom.ui.ScriptedProjects.notScripted;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.RoundTripReport;

/** What {@link ScriptedProjectService#roundTrip} answers, and a record of who asked and on which thread. */
final class ScriptedRoundTrips {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private volatile Result<RoundTripReport> answer = notScripted();
    private volatile @Nullable CountDownLatch gate;
    private volatile @Nullable RuntimeException failure;

    Result<RoundTripReport> check(final String projectId) {
        final CountDownLatch held = gate;
        calls.add(Objects.requireNonNull(projectId, "projectId"));
        onFxThread.add(Platform.isFxApplicationThread());
        entered.countDown();
        awaitGate(held);
        final RuntimeException thrown = failure;
        if (thrown != null) {
            throw thrown;
        }
        return answer;
    }

    void answer(final Result<RoundTripReport> next) {
        answer = Objects.requireNonNull(next, "next");
    }

    void throwing(final RuntimeException thrown) {
        failure = thrown;
    }

    void hold() {
        gate = new CountDownLatch(1);
    }

    void release() {
        final CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    void awaitEntered() throws InterruptedException {
        awaitLatch(entered, "the service was never asked for a round-trip check");
    }

    List<String> projects() {
        return List.copyOf(calls);
    }

    List<Boolean> onFxThread() {
        return List.copyOf(onFxThread);
    }
}
