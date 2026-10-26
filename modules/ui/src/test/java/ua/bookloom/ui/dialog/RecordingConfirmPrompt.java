package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A hand-written {@link ConfirmPrompt} that records each question and confirms none. */
public final class RecordingConfirmPrompt implements ConfirmPrompt {

    private final List<Question> asked = new CopyOnWriteArrayList<>();

    @Override
    public void ask(
            final Question question,
            final List<Object> titleArgs,
            final List<Object> textArgs,
            final Runnable confirmed) {
        asked.add(question);
    }

    public List<Question> asked() {
        return List.copyOf(asked);
    }
}
