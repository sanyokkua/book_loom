package ua.bookloom.api.pipeline;

import java.util.function.Consumer;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;

/**
 * The model's help with the setup of a translation, before any segment is translated: it reads the opened book and
 * proposes what the person would otherwise have to fill in. It only proposes; nothing is stored or changed.
 */
public interface SetupAssistant {

    /**
     * Proposes the name of the translated book's file.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @return the file name without its extension and without any character a file name cannot hold, in the target
     *     language, with whether its author part was left in Latin letters for a non-Latin target; {@code validation} when the project is not stored or its book is not open; the model's own error
     *     otherwise
     */
    Result<FileNameSuggestion> suggestFileName(String projectId, ChatModel model);

    /**
     * Proposes the tone and style of the Book Brief from the opening of the book.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @return the suggestion; {@code validation} when the project is not stored or its book is not open; the model's
     *     own error otherwise
     */
    Result<BriefSuggestion> suggestBrief(String projectId, ChatModel model);

    /**
     * As {@link #suggestFileName(String, ChatModel)}, announcing the model call so a screen can show it. An assistant
     * that shows nothing ignores {@code progress}.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @param progress the non-null receiver of each call's snapshot, called on the calling thread
     * @return as {@link #suggestFileName(String, ChatModel)}
     */
    default Result<FileNameSuggestion> suggestFileName(String projectId, ChatModel model, Consumer<JobEvent> progress) {
        return suggestFileName(projectId, model);
    }

    /**
     * As {@link #suggestBrief(String, ChatModel)}, announcing the model call so a screen can show it. An assistant
     * that shows nothing ignores {@code progress}.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @param progress the non-null receiver of each call's snapshot, called on the calling thread
     * @return as {@link #suggestBrief(String, ChatModel)}
     */
    default Result<BriefSuggestion> suggestBrief(String projectId, ChatModel model, Consumer<JobEvent> progress) {
        return suggestBrief(projectId, model);
    }
}
