package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;

/** A hand-written {@link ContinuePrompt} that records each question and lets the test answer the last one. */
public final class RecordingContinuePrompt implements ContinuePrompt {

    /** One question asked: which kind, and the model(s) it named. */
    public record Asked(
            String kind, ModelSelection first, @Nullable ModelSelection second) {}

    /** The kind of {@link #asked()} entry for "AI model not answering". */
    public static final String NOT_ANSWERING = "notAnswering";

    /** The kind of {@link #asked()} entry for "A different model is chosen". */
    public static final String DIFFERENT = "different";

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Runnable onTryAgain;
    private volatile @Nullable Runnable onUseAnother;
    private volatile @Nullable Runnable onNotNow;
    private volatile @Nullable Runnable onEarlier;
    private volatile @Nullable Runnable onNew;
    private volatile @Nullable Runnable onCancelled;

    @Override
    public void askNotAnswering(
            final ModelSelection model, final Runnable tryAgain, final Runnable useAnother, final Runnable notNow) {
        asked.add(new Asked(NOT_ANSWERING, model, null));
        onTryAgain = tryAgain;
        onUseAnother = useAnother;
        onNotNow = notNow;
    }

    @Override
    public void askDifferent(
            final ModelSelection earlier,
            final ModelSelection now,
            final Runnable useEarlier,
            final Runnable useNew,
            final Runnable cancelled) {
        asked.add(new Asked(DIFFERENT, earlier, now));
        onEarlier = useEarlier;
        onNew = useNew;
        onCancelled = cancelled;
    }

    /** Presses "Try again" on the last not-answering question. */
    public void tryAgain() {
        run(onTryAgain, "a not-answering question");
    }

    /** Presses "Use another AI model…" on the last not-answering question. */
    public void useAnother() {
        run(onUseAnother, "a not-answering question");
    }

    /** Presses "Not now" on the last not-answering question. */
    public void notNow() {
        run(onNotNow, "a not-answering question");
    }

    /** Presses "Use the earlier model" on the last different-model question. */
    public void useEarlier() {
        run(onEarlier, "a different-model question");
    }

    /** Presses "Continue with the new one" on the last different-model question. */
    public void useNew() {
        run(onNew, "a different-model question");
    }

    /** Closes the last different-model question with Escape. */
    public void escape() {
        run(onCancelled, "a different-model question");
    }

    private static void run(final @Nullable Runnable action, final String what) {
        if (action == null) {
            throw new IllegalStateException("nothing was asked: " + what);
        }
        action.run();
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
    }

    /** The kinds asked, oldest first. */
    public List<String> kinds() {
        return asked.stream().map(Asked::kind).toList();
    }
}
