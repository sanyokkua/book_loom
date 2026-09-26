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
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;

/**
 * A hand-written {@link TranslationEngine} that hands out the one job a test scripted, or a scripted error, and
 * records every request it was asked about and whether the asking thread was the FX Application Thread.
 */
public final class ScriptedTranslationEngine implements TranslationEngine {

    private final @Nullable TranslationJob job;
    private final @Nullable AppError failure;
    private final List<TranslationRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Boolean> askedOnFxThread = new CopyOnWriteArrayList<>();

    private ScriptedTranslationEngine(final @Nullable TranslationJob job, final @Nullable AppError failure) {
        this.job = job;
        this.failure = failure;
    }

    /** An engine whose every job is {@code job}. */
    public static ScriptedTranslationEngine returning(final TranslationJob job) {
        return new ScriptedTranslationEngine(Objects.requireNonNull(job, "job"), null);
    }

    /** An engine that always answers with {@code error}. */
    public static ScriptedTranslationEngine failing(final AppError error) {
        return new ScriptedTranslationEngine(null, Objects.requireNonNull(error, "error"));
    }

    /** An engine no test is expected to ask; it answers with an error if it is. */
    public static ScriptedTranslationEngine idle() {
        return failing(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted engine is never expected to run."));
    }

    @Override
    public Result<TranslationJob> newJob(final TranslationRequest request, final ChatModel model) {
        requests.add(request);
        askedOnFxThread.add(Platform.isFxApplicationThread());
        if (job != null) {
            return Result.ok(job);
        }
        return Result.err(Objects.requireNonNull(failure, "a failing engine carries its error"));
    }

    public List<TranslationRequest> requests() {
        return List.copyOf(requests);
    }

    /** One entry per call: {@code true} when the call was made on the FX Application Thread. */
    public List<Boolean> askedOnFxThread() {
        return List.copyOf(askedOnFxThread);
    }
}
