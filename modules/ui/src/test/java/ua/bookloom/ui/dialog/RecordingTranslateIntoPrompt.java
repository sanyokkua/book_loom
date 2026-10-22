package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.ProjectNameRules;

/** A hand-written {@link TranslateIntoPrompt} that records each question and lets the test answer the last one. */
public final class RecordingTranslateIntoPrompt implements TranslateIntoPrompt {

    /** One question asked. */
    public record Asked(BookGroup group, ProjectNameRules rules) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable BiConsumer<String, @Nullable String> onCreate;
    private volatile @Nullable Runnable onCancelled;

    @Override
    public void ask(
            final BookGroup group,
            final ProjectNameRules rules,
            final BiConsumer<String, @Nullable String> create,
            final Runnable cancelled) {
        asked.add(new Asked(group, rules));
        onCreate = create;
        onCancelled = cancelled;
    }

    /** Presses Create on the last question; a {@code null} name means "the automatic name". */
    public void create(final String target, final @Nullable String name) {
        require(onCreate).accept(target, name);
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
