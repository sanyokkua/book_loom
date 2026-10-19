package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.RunState;

/** A hand-written {@link ReplaceRunPrompt} that records each question and lets the test answer it. */
public final class RecordingReplaceRunPrompt implements ReplaceRunPrompt {

    /** One question: the run's file, the file to import and the state the run was in. */
    public record Asked(String runFileName, String newFileName, RunState state) {}

    /** One earlier-translation question: the file about to be imported and how many of its segments are decided. */
    public record Earlier(String fileName, int decidedSegments) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private final List<Earlier> earlier = new CopyOnWriteArrayList<>();
    private volatile @Nullable Runnable pendingContinue;
    private volatile @Nullable Runnable pendingStartOver;
    private final AtomicInteger dismissals = new AtomicInteger();
    private volatile @Nullable Runnable pending;

    @Override
    public void ask(
            final String runFileName, final String newFileName, final RunState state, final Runnable onConfirm) {
        asked.add(new Asked(runFileName, newFileName, state));
        pending = onConfirm;
    }

    @Override
    public void askEarlierWork(
            final String fileName, final int decidedSegments, final Runnable onContinue, final Runnable onStartOver) {
        earlier.add(new Earlier(fileName, decidedSegments));
        pendingContinue = onContinue;
        pendingStartOver = onStartOver;
    }

    @Override
    public void dismiss() {
        dismissals.incrementAndGet();
    }

    /** Answers the last question with "discard and import". */
    public void confirm() {
        final Runnable action = pending;
        if (action == null) {
            throw new IllegalStateException("nothing was asked");
        }
        action.run();
    }

    /** Answers the last earlier-translation question with "continue". */
    public void continueEarlier() {
        final Runnable action = pendingContinue;
        if (action == null) {
            throw new IllegalStateException("no earlier translation was asked about");
        }
        action.run();
    }

    /** Answers the last earlier-translation question with "start over". */
    public void startOver() {
        final Runnable action = pendingStartOver;
        if (action == null) {
            throw new IllegalStateException("no earlier translation was asked about");
        }
        action.run();
    }

    public List<Earlier> earlier() {
        return List.copyOf(earlier);
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
    }

    public int dismissals() {
        return dismissals.get();
    }
}
