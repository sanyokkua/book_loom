package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** A hand-written {@link SwitchProviderPrompt} that records each question and lets the test answer the last one. */
public final class RecordingSwitchPrompt implements SwitchProviderPrompt {

    /** One question: the provider that would become current, and the provider and model used so far. */
    public record Asked(String chosenName, String earlierName, String earlierModel) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Runnable onUse;

    @Override
    public void ask(final String chosenName, final String earlierName, final String earlierModel, final Runnable use) {
        asked.add(new Asked(chosenName, earlierName, earlierModel));
        onUse = use;
    }

    /** Presses the button that uses the new provider. */
    public void use() {
        final Runnable action = onUse;
        if (action == null) {
            throw new IllegalStateException("nothing was asked");
        }
        action.run();
    }

    /** Presses the button that keeps the earlier provider, which does nothing. */
    public void keep() {
        if (onUse == null) {
            throw new IllegalStateException("nothing was asked");
        }
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
    }
}
