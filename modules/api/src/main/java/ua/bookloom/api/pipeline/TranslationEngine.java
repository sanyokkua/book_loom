package ua.bookloom.api.pipeline;

import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;

/**
 * Creates an in-memory translation job for a validated request and an already-bound model.
 */
public interface TranslationEngine {

    /**
     * Prepares a job without starting model inference.
     *
     * @param request the non-null source, destination and language request
     * @param model the non-null model to use for the job
     * @return the job, or a typed validation/opening failure
     */
    Result<TranslationJob> newJob(TranslationRequest request, ChatModel model);
}
