package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;

/** A setup assistant whose answers a test sets, and which records the projects it was asked about. */
public final class ScriptedSetupAssistant implements SetupAssistant {

    private static final Result<?> UNSCRIPTED =
            Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer."));

    private Result<FileNameSuggestion> fileName = cast();
    private Result<BriefSuggestion> brief = cast();
    private final List<String> asked = new ArrayList<>();
    private final List<@Nullable Integer> detectedContexts = new ArrayList<>();

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
        return suggestBrief(projectId, model);
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
}
