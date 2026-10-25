package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** A hand-written {@link RenamePrompt} that records each question and lets the test answer the last one. */
public final class RecordingRenamePrompt implements RenamePrompt {

    /** One question asked. */
    public record Asked(
            ProjectRow row,
            ProjectNameRules rules,
            @Nullable String error,
            @Nullable String typed) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Consumer<@Nullable String> onRename;
    private volatile @Nullable Runnable onRemove;
    private volatile @Nullable Runnable onCancelled;

    @Override
    public void ask(
            final ProjectRow row,
            final ProjectNameRules rules,
            final @Nullable String error,
            final @Nullable String typed,
            final Consumer<@Nullable String> rename,
            final Runnable remove,
            final Runnable cancelled) {
        asked.add(new Asked(row, rules, error, typed));
        onRename = rename;
        onRemove = remove;
        onCancelled = cancelled;
    }

    /** Presses Rename on the last question with {@code name}; {@code null} means "use the automatic name". */
    public void rename(final @Nullable String name) {
        require(onRename).accept(name);
    }

    /** Presses Remove project... on the last question. */
    public void remove() {
        require(onRemove).run();
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
