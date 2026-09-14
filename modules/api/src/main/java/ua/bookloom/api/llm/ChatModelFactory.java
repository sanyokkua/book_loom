package ua.bookloom.api.llm;

import ua.bookloom.api.Result;

/**
 * Resolves a provider and model selection into a bound {@link ChatModel}.
 */
public interface ChatModelFactory {

    /**
     * Creates a model bound to the requested provider and model identity.
     *
     * @param selection the non-null provider/model selection
     * @return the bound model, or a typed validation failure
     */
    Result<ChatModel> create(ModelSelection selection);
}
