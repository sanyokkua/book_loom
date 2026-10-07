package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Runs one of the model's setup proposals — a file name, a style for the Book Brief — in the background with the model
 * the person chose, registered as model work so it never overlaps a translation, and hands the answer back on the FX
 * thread. It proposes only: what to do with the answer is the caller's.
 */
@Slf4j
@Singleton
public final class SetupHelper {

    /** Why a proposal did not start. */
    public enum Refusal {
        /** No provider and model are chosen yet. */
        NO_MODEL,
        /** Other model work is running, named by the kind. */
        BUSY
    }

    private final SetupAssistant assistant;
    private final ChatModelFactory models;
    private final SettingsViewModel settings;
    private final ExecutorService executor;
    private final ActivityTracker activities;

    /**
     * Creates the helper.
     *
     * @param assistant the port that asks the model
     * @param models where the chosen model is built
     * @param settings where the chosen provider and model are read
     * @param executor the daemon executor the call runs on, never the FX thread
     * @param activities the model work under way
     */
    @Inject
    public SetupHelper(
            final SetupAssistant assistant,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities) {
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.models = Objects.requireNonNull(models, "models");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.activities = Objects.requireNonNull(activities, "activities");
    }

    /**
     * The port the proposals are asked of, for the work a caller passes to {@link #run}.
     *
     * @return the assistant; never null
     */
    public SetupAssistant assistant() {
        return assistant;
    }

    /**
     * Starts one proposal. FX thread only.
     *
     * @param kind the model work it is registered as
     * @param work asks the assistant with the model it is given
     * @param done receives the answer on the FX thread, once
     * @return empty when it started, else why it did not, in which case {@code done} is never called
     */
    public <T> Optional<Refusal> run(
            final ActivityKind kind, final Function<ChatModel, Result<T>> work, final Consumer<Result<T>> done) {
        final Optional<ModelSelection> selection = settings.selection();
        if (selection.isEmpty()) {
            log.debug("setup proposal {} not started: no model is chosen", kind);
            return Optional.of(Refusal.NO_MODEL);
        }
        if (activities.conflictFor(kind).isPresent()) {
            log.debug("setup proposal {} not started: {} is running", kind, activities.conflictFor(kind));
            return Optional.of(Refusal.BUSY);
        }
        log.info(
                "setup proposal {} started with provider {}",
                kind,
                selection.get().providerId());
        final ActivityTracker.Handle handle = activities.begin(kind, null);
        try {
            executor.execute(() -> deliver(kind, handle, answer(selection.get(), work), done));
        } catch (RejectedExecutionException rejected) {
            log.error("the setup proposal {} could not be submitted", kind, rejected);
            handle.end();
            done.accept(Result.err(AppError.of(ErrorCode.internal, "Suggestion failed", "It could not be started.")));
        }
        return Optional.empty();
    }

    private <T> void deliver(
            final ActivityKind kind,
            final ActivityTracker.Handle handle,
            final Result<T> answer,
            final Consumer<Result<T>> done) {
        Platform.runLater(() -> {
            handle.end();
            log.info("setup proposal {} ended ok={}", kind, answer.isOk());
            done.accept(answer);
        });
    }

    private <T> Result<T> answer(final ModelSelection selection, final Function<ChatModel, Result<T>> work) {
        try {
            return models.create(selection).flatMap(work);
        } catch (Throwable thrown) {
            log.error("a setup proposal threw instead of returning a result", thrown);
            return Result.err(
                    AppError.of(ErrorCode.internal, "Suggestion failed", "An unexpected failure stopped it."));
        }
    }
}
