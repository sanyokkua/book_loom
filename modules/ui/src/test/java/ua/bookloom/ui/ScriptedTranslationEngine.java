package ua.bookloom.ui;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;

/**
 * A hand-written {@link TranslationEngine} that hands out the jobs a test scripted in order, or a scripted error, and
 * records every request it was asked about and whether the asking thread was the FX Application Thread.
 */
public final class ScriptedTranslationEngine implements TranslationEngine {

    private final Queue<TranslationJob> jobs;
    private final @Nullable AppError failure;
    private final List<RunRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Boolean> askedOnFxThread = new CopyOnWriteArrayList<>();

    private ScriptedTranslationEngine(final List<TranslationJob> jobs, final @Nullable AppError failure) {
        this.jobs = new ConcurrentLinkedQueue<>(jobs);
        this.failure = failure;
    }

    /** An engine that hands out {@code jobs} in order, one per call, and answers an error once they are used up. */
    public static ScriptedTranslationEngine returning(final TranslationJob... jobs) {
        return new ScriptedTranslationEngine(List.of(jobs), null);
    }

    /** An engine that always answers with {@code error}. */
    public static ScriptedTranslationEngine failing(final AppError error) {
        return new ScriptedTranslationEngine(List.of(), Objects.requireNonNull(error, "error"));
    }

    /** An engine no test is expected to ask; it answers with an error if it is. */
    public static ScriptedTranslationEngine idle() {
        return failing(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted engine is never expected to run."));
    }

    @Override
    public Result<TranslationJob> newJob(final RunRequest request, final ChatModel model) {
        requests.add(request);
        askedOnFxThread.add(Platform.isFxApplicationThread());
        final TranslationJob next = jobs.poll();
        if (next != null) {
            return Result.ok(next);
        }
        return Result.err(failure != null ? failure : exhausted());
    }

    private static AppError exhausted() {
        return AppError.of(ErrorCode.internal, "Not scripted", "The scripted engine has no job left to hand out.");
    }

    public List<RunRequest> requests() {
        return List.copyOf(requests);
    }

    /** One entry per call: {@code true} when the call was made on the FX Application Thread. */
    public List<Boolean> askedOnFxThread() {
        return List.copyOf(askedOnFxThread);
    }
}
