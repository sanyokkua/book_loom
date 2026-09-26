package ua.bookloom.ui.state;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * An executor that fails one {@code execute} on demand and otherwise hands work to a real executor, so a test can
 * make the background pool refuse a run and then prove the runner is usable again.
 */
final class FailingOnceExecutor extends AbstractExecutorService {

    private final ExecutorService delegate;
    private volatile @Nullable Runnable failure;

    FailingOnceExecutor(final ExecutorService delegate) {
        this.delegate = delegate;
    }

    /** The next {@code execute} runs {@code thrower}, which is expected to throw, instead of running the task. */
    void failNextWith(final Runnable thrower) {
        failure = thrower;
    }

    @Override
    public void execute(final Runnable command) {
        final Runnable thrower = failure;
        if (thrower != null) {
            failure = null;
            thrower.run();
            return;
        }
        delegate.execute(command);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
