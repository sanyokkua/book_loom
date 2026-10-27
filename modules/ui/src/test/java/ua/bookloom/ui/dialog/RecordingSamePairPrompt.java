package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** A hand-written {@link SamePairPrompt} that records each question and lets the test answer the last one. */
public final class RecordingSamePairPrompt implements SamePairPrompt {

    /** One question asked. */
    public record Asked(ProjectRow existing, String languageName, String keepName, ProjectNameRules rules) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Runnable onOpenExisting;
    private volatile @Nullable Consumer<String> onMakeSecond;
    private volatile @Nullable Runnable onCancelled;

    @Override
    public void ask(
            final ProjectRow existing,
            final String languageName,
            final String keepName,
            final ProjectNameRules rules,
            final Runnable openExisting,
            final Consumer<String> makeSecond,
            final Runnable cancelled) {
        asked.add(new Asked(existing, languageName, keepName, rules));
        onOpenExisting = openExisting;
        onMakeSecond = makeSecond;
        onCancelled = cancelled;
    }

    /** Presses "Open that project" on the last question. */
    public void openExisting() {
        require(onOpenExisting).run();
    }

    /** Presses "Use {language} with this name" on the last question with {@code name}. */
    public void makeSecond(final String name) {
        require(onMakeSecond).accept(name);
    }

    /** Presses Keep/Cancel on the last question. */
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
