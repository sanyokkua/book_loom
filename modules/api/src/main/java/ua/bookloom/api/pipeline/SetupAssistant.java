package ua.bookloom.api.pipeline;

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
     *     language; {@code validation} when the project is not stored or its book is not open; the model's own error
     *     otherwise
     */
    Result<String> suggestFileName(String projectId, ChatModel model);

    /**
     * Proposes the tone and style of the Book Brief from the opening of the book.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @return the suggestion; {@code validation} when the project is not stored or its book is not open; the model's
     *     own error otherwise
     */
    Result<BriefSuggestion> suggestBrief(String projectId, ChatModel model);
}
