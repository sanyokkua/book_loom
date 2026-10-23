package ua.bookloom.ui;

import java.util.concurrent.atomic.AtomicInteger;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.Result;

/** An {@link AppRestart} that restarts nothing: it counts the asks, runs a hook at each and answers as told. */
public final class RecordingAppRestart implements AppRestart {

    private final Result<Boolean> answer;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile Runnable onCall = () -> {};

    /** Answers {@code ok(true)}: a new copy was started. */
    public RecordingAppRestart() {
        this(Result.ok(true));
    }

    /**
     * Answers as told.
     *
     * @param answer what every ask returns
     */
    public RecordingAppRestart(final Result<Boolean> answer) {
        this.answer = answer;
    }

    /** Runs {@code hook} at each ask, before the answer, so a test can look at what had happened by then. */
    public RecordingAppRestart onCall(final Runnable hook) {
        onCall = hook;
        return this;
    }

    /** How many times a restart was asked for. */
    public int calls() {
        return calls.get();
    }

    @Override
    public Result<Boolean> restart() {
        calls.incrementAndGet();
        onCall.run();
        return answer;
    }
}
