package ua.bookloom.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;

/**
 * A hand-written {@link ChatModelFactory} that hands out a model nobody is expected to ask, or a scripted error, and
 * records every selection it was asked about and whether the asking thread was the FX Application Thread.
 */
public final class ScriptedChatModelFactory implements ChatModelFactory {

    private final @Nullable AppError failure;
    private final List<ModelSelection> selections = new CopyOnWriteArrayList<>();
    private final List<Boolean> askedOnFxThread = new CopyOnWriteArrayList<>();

    private ScriptedChatModelFactory(final @Nullable AppError failure) {
        this.failure = failure;
    }

    /** A factory that always creates a model. */
    public static ScriptedChatModelFactory ok() {
        return new ScriptedChatModelFactory(null);
    }

    /** A factory that always answers with {@code error}. */
    public static ScriptedChatModelFactory failing(final AppError error) {
        return new ScriptedChatModelFactory(Objects.requireNonNull(error, "error"));
    }

    @Override
    public Result<ChatModel> create(final ModelSelection selection) {
        selections.add(selection);
        askedOnFxThread.add(Platform.isFxApplicationThread());
        if (failure != null) {
            return Result.err(failure);
        }
        return Result.ok(request -> Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted model is never expected to be asked.")));
    }

    public List<ModelSelection> selections() {
        return List.copyOf(selections);
    }

    /** One entry per call: {@code true} when the call was made on the FX Application Thread. */
    public List<Boolean> askedOnFxThread() {
        return List.copyOf(askedOnFxThread);
    }
}
