package ua.bookloom.ui.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** An executor that holds each task until the test runs it, so a test decides when, and in which order, answers land. */
final class QueuedExecutor extends AbstractExecutorService {

    private final Deque<Runnable> queued = new ArrayDeque<>();
    private boolean shutDown;

    @Override
    public void execute(final Runnable command) {
        queued.add(command);
    }

    /** The number of tasks waiting to run. */
    int pending() {
        return queued.size();
    }

    /** Runs every waiting task in the order it was submitted. */
    void runAll() {
        final List<Runnable> tasks = new ArrayList<>(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    /** Runs every waiting task, the last one submitted first. */
    void runNewestFirst() {
        final List<Runnable> tasks = new ArrayList<>(queued);
        queued.clear();
        Collections.reverse(tasks);
        tasks.forEach(Runnable::run);
    }

    @Override
    public void shutdown() {
        shutDown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutDown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutDown;
    }

    @Override
    public boolean isTerminated() {
        return shutDown;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
        return shutDown;
    }
}
