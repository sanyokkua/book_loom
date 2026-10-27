package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.state.ProjectRow;

/** A hand-written {@link FindBookPrompt} that records each question and lets the test answer the last one. */
public final class RecordingFindBookPrompt implements FindBookPrompt {

    /** One question asked. */
    public record Asked(
            ProjectRow row,
            ProjectOpening opening,
            @Nullable String error) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Runnable onChoose;
    private volatile @Nullable Runnable onCancelled;

    @Override
    public void ask(
            final ProjectRow row,
            final ProjectOpening opening,
            final @Nullable String error,
            final Runnable choose,
            final Runnable cancelled) {
        asked.add(new Asked(row, opening, error));
        onChoose = choose;
        onCancelled = cancelled;
    }

    /** Presses "Find the book..." on the last question. */
    public void choose() {
        require(onChoose).run();
    }

    /** Closes the last question with Cancel or Escape. */
    public void cancel() {
        require(onCancelled).run();
    }

    /** The questions asked, oldest first. */
    public List<Asked> asked() {
        return List.copyOf(asked);
    }

    private static <T> T require(final @Nullable T answer) {
        if (answer == null) {
            throw new IllegalStateException("nothing was asked");
        }
        return answer;
    }
}
