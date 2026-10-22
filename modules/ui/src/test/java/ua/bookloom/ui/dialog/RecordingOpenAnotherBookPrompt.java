package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.RunState;

/** A hand-written {@link OpenAnotherBookPrompt} that records each question and lets the test answer it. */
public final class RecordingOpenAnotherBookPrompt implements OpenAnotherBookPrompt {

    /** One question: the open project's name, the file to import and the state the run was in. */
    public record Asked(String openProjectName, String newFileName, RunState state) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private final AtomicInteger dismissals = new AtomicInteger();
    private volatile @Nullable Runnable pendingOpenNew;
    private volatile @Nullable Runnable pendingReplace;

    @Override
    public void ask(
            final String openProjectName,
            final String newFileName,
            final RunState state,
            final Runnable onOpenNew,
            final Runnable onReplace) {
        asked.add(new Asked(openProjectName, newFileName, state));
        pendingOpenNew = onOpenNew;
        pendingReplace = onReplace;
    }

    @Override
    public void dismiss() {
        dismissals.incrementAndGet();
    }

    /** Answers the last question with "Open as a new project". */
    public void openNew() {
        run(pendingOpenNew);
    }

    /** Answers the last question with "Replace and delete this translation". */
    public void replace() {
        run(pendingReplace);
    }

    private static void run(final @Nullable Runnable action) {
        if (action == null) {
            throw new IllegalStateException("nothing was asked");
        }
        action.run();
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
    }

    public int dismissals() {
        return dismissals.get();
    }
}
