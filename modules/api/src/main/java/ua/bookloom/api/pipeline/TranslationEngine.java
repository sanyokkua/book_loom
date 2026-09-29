package ua.bookloom.api.pipeline;

import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;

/**
 * Creates a translation job over a stored project for an already-bound model.
 */
public interface TranslationEngine {

    /**
     * Prepares a job without starting model inference.
     *
     * @param request the non-null project and review mode to run
     * @param model the non-null model to use for the job
     * @return the job, or a typed failure
     */
    Result<TranslationJob> newJob(RunRequest request, ChatModel model);
}
