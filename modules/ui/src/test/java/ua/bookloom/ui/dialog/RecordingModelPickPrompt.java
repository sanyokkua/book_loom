package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** A hand-written {@link ModelPickPrompt} that records each question and lets the test answer the last one. */
public final class RecordingModelPickPrompt implements ModelPickPrompt {

    /** One question: the provider id and the models offered, in order. */
    public record Asked(String providerId, List<String> modelIds) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Consumer<String> onUse;
    private volatile @Nullable Runnable onNotNow;

    @Override
    public void ask(
            final String providerId, final List<String> modelIds, final Consumer<String> use, final Runnable notNow) {
        asked.add(new Asked(providerId, List.copyOf(modelIds)));
        onUse = use;
        onNotNow = notNow;
    }

    /** Presses "Use this model" with {@code modelId} picked. */
    public void use(final String modelId) {
        final Consumer<String> action = onUse;
        if (action == null) {
            throw new IllegalStateException("nothing was asked");
        }
        action.accept(modelId);
    }

    /** Presses "Not now". */
    public void notNow() {
        final Runnable action = onNotNow;
        if (action == null) {
            throw new IllegalStateException("nothing was asked");
        }
        action.run();
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
    }
}
