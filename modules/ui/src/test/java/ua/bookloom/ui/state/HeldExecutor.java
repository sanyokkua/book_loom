package ua.bookloom.ui.state;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** An executor that runs the first task at once and keeps every later one waiting, as a slow model would. */
final class HeldExecutor extends AbstractExecutorService {

    private final List<Runnable> held = new CopyOnWriteArrayList<>();
    private boolean loaded;

    @Override
    public Future<?> submit(final Runnable task) {
        execute(task);
        return new CompletableFuture<Void>();
    }

    @Override
    public void execute(final Runnable command) {
        if (loaded) {
            held.add(command);
        } else {
            loaded = true;
            command.run();
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
