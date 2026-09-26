package ua.bookloom.ui.state;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** An executor that runs each task on the calling thread, so a test controls exactly when background work happens. */
final class DirectExecutor extends AbstractExecutorService {

    private volatile boolean shutDown;

    @Override
    public void execute(final Runnable command) {
        command.run();
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
