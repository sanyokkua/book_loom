package ua.bookloom.api.pipeline;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;

/**
 * The one autonomous names pass (15i.N8): the steps the Names &amp; style buttons run, in order, over a staged copy of
 * both tables — scan when the glossary is empty, read and decide the names, scan and read the recurring terms,
 * translate both, lock by rule — and then one write to the repositories. A stopped or failed pass writes nothing.
 */
public interface NamesPass {

    /**
     * Says what a pass would cost before its first call.
     *
     * @param projectId the non-null project whose book is open
     * @return the calls and minutes the dial's plan reads; {@code validation} when the project is not stored or its
     *     book is not open
     */
    Result<NamesEstimate> estimate(String projectId);

    /**
     * Prepares the glossary and the recurring terms of a project with the model.
     *
     * @param projectId the non-null project whose book is open
     * @param model the non-null model to call
     * @param detectedContext the context length in tokens the provider reported for {@code model}, or null when it
     *     said nothing
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return what the pass changed in each table, how many calls it made and how long it took; {@code validation}
     *     when the project has no target language or its book is not open; {@code cancelled} when the calling thread
     *     was interrupted or a call was cancelled; the failed call's own error otherwise. On any error neither table
     *     has changed.
     */
    Result<NamesPassReport> run(
            String projectId, ChatModel model, @Nullable Integer detectedContext, Consumer<JobEvent> progress);
}
