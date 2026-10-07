package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.SetupAssistant;

/** A setup assistant whose answers a test sets, and which records the projects it was asked about. */
public final class ScriptedSetupAssistant implements SetupAssistant {

    private static final Result<?> UNSCRIPTED =
            Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer."));

    private Result<String> fileName = cast();
    private Result<BriefSuggestion> brief = cast();
    private final List<String> asked = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> Result<T> cast() {
        return (Result<T>) UNSCRIPTED;
    }

    /** Sets what the next file name suggestions answer. */
    public ScriptedSetupAssistant answersFileName(final Result<String> answer) {
        this.fileName = answer;
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

    @Override
    public Result<FileNameSuggestion> suggestFileName(final String projectId, final ChatModel model) {
        asked.add(projectId);
        return fileName.map(name -> new FileNameSuggestion(name, false));
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
        asked.add(projectId);
        return brief;
    }
}
