package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Narrator;

/** A setup assistant whose answers a test sets, and which records the projects it was asked about. */
public final class ScriptedSetupAssistant implements SetupAssistant {

    private static final Result<?> UNSCRIPTED =
            Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer."));

    private Result<FileNameSuggestion> fileName = cast();
    private Result<BriefSuggestion> brief = cast();
    private Result<Narrator> recheck = cast();
    private final List<String> rechecked = new ArrayList<>();
    private final List<Boolean> recheckedOnFxThread = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private final List<@Nullable Integer> detectedContexts = new ArrayList<>();
    private final List<JobEvent> announced = new CopyOnWriteArrayList<>();
    private volatile @Nullable CountDownLatch gate;

    @SuppressWarnings("unchecked")
    private static <T> Result<T> cast() {
        return (Result<T>) UNSCRIPTED;
    }

    /** Sets what the next file name suggestions answer. */
    public ScriptedSetupAssistant answersFileName(final Result<String> answer) {
        this.fileName = answer.map(name -> new FileNameSuggestion(name, false));
        return this;
    }

    /** Sets the next file name suggestion whole, with whether the author's name stayed in the source alphabet. */
    public ScriptedSetupAssistant answersFileName(final FileNameSuggestion answer) {
        this.fileName = Result.ok(answer);
        return this;
    }

    /** Sets what the next brief suggestions answer. */
    public ScriptedSetupAssistant answersBrief(final Result<BriefSuggestion> answer) {
        this.brief = answer;
        return this;
    }

    /** Sets what the narrator re-check answers; unscripted, it fails, which the window must survive. */
    public ScriptedSetupAssistant answersRecheck(final Result<Narrator> answer) {
        this.recheck = answer;
        return this;
    }

    /** From now on announces these events to the progress receiver of each brief suggestion, before it answers. */
    public ScriptedSetupAssistant announces(final List<? extends JobEvent> events) {
        announced.clear();
        announced.addAll(events);
        return this;
    }

    /** From now on holds each brief suggestion, after its announcements, until {@link #releaseAnswer()}. */
    public void holdAnswer() {
        gate = new CountDownLatch(1);
    }

    /** Lets the held brief suggestions answer. */
    public void releaseAnswer() {
        final CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    /** The project ids whose narrator was re-checked, in order. */
    public List<String> rechecked() {
        return List.copyOf(rechecked);
    }

    /** For each re-check, in order, whether it ran on the FX thread, which a whole-book scan must not. */
    public List<Boolean> recheckedOnFxThread() {
        return List.copyOf(recheckedOnFxThread);
    }

    /** The project ids a suggestion was asked for, in order. */
    public List<String> asked() {
        return List.copyOf(asked);
    }

    /** The context length each brief suggestion was asked with, in order; null where none was detected. */
    public List<@Nullable Integer> detectedContexts() {
        return Collections.unmodifiableList(detectedContexts);
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(
            final String projectId,
            final ChatModel model,
            @Nullable final Integer detectedContext,
            final Consumer<JobEvent> progress) {
        detectedContexts.add(detectedContext);
        announced.forEach(progress);
        awaitGate();
        return suggestBrief(projectId, model);
    }

    @Override
    public Result<Narrator> recheckNarrator(final String projectId) {
        recheckedOnFxThread.add(Platform.isFxApplicationThread());
        rechecked.add(projectId);
        return recheck;
    }

    @Override
    public Result<FileNameSuggestion> suggestFileName(final String projectId, final ChatModel model) {
        asked.add(projectId);
        return fileName;
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
        asked.add(projectId);
        return brief;
    }

    private void awaitGate() {
        final CountDownLatch held = gate;
        if (held == null) {
            return;
        }
        try {
            held.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
