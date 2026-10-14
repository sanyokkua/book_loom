package ua.bookloom.api.pipeline;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.Narrator;

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
     * @return the suggestion; {@code validation} when the project is not stored or its book is not open; {@code cancelled}
     *     when the person stopped any of its calls; the model's own error when every passage's call failed. A failed
     *     passage among several only counts as a passage with no reading.
     */
    Result<BriefSuggestion> suggestBrief(String projectId, ChatModel model);

    /**
     * Checks the narrator quotes the brief stored against the glossary as it stands now, with no model call and no
     * write: a quote that names the narrator by a glossary character of confirmed gender (the person's or verified,
     * never a suggestion) makes the narrator's gender verified (15i.T5).
     *
     * @param projectId the project whose book is open and whose brief holds the narrator
     * @return the narrator after the check, equal to the stored one when nothing changed; {@code validation} when the
     *     project is not stored or its book is not open
     */
    Result<Narrator> recheckNarrator(String projectId);

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
     * As {@link #suggestBrief(String, ChatModel)}, announcing each model call so a screen can show it and sizing each
     * passage the model reads to the context window the model really has. An assistant that shows nothing ignores
     * {@code progress}, and one that reads no passages ignores {@code detectedContext}.
     *
     * @param projectId the project whose book is open
     * @param model the model to ask
     * @param detectedContext the context length in tokens the provider reported for {@code model}, or null when it said
     *     nothing, which sizes the passages for the default window
     * @param progress the non-null receiver of each call's snapshot, called on the calling thread
     * @return as {@link #suggestBrief(String, ChatModel)}
     */
    default Result<BriefSuggestion> suggestBrief(
            String projectId, ChatModel model, @Nullable Integer detectedContext, Consumer<JobEvent> progress) {
        return suggestBrief(projectId, model);
    }
}
