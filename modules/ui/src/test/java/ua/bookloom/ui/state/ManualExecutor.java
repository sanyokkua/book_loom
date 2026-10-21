package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** An executor that keeps every task until the test runs them, so a test decides when background work answers. */
final class ManualExecutor extends AbstractExecutorService {

    private final List<Runnable> waiting = new CopyOnWriteArrayList<>();

    @Override
    public void execute(final Runnable command) {
        waiting.add(command);
    }

    /** Runs every task kept so far, and any that those tasks add, on the calling thread. */
    void runAll() {
        while (!waiting.isEmpty()) {
            final List<Runnable> batch = new ArrayList<>(waiting);
            waiting.clear();
            batch.forEach(Runnable::run);
        }
    }

    @Override
    public void shutdown() {
        // Nothing to stop.
    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
        return true;
    }
}
