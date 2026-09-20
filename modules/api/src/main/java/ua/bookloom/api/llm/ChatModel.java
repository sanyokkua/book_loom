package ua.bookloom.api.llm;

import ua.bookloom.api.Result;

/**
 * The provider-neutral seam through which a caller sends one chat request.
 */
public interface ChatModel {

    /**
     * Sends the ordered conversation to this already-bound model.
     *
     * @param request the non-null conversation to send
     * @return the model response, or a typed failure
     */
    Result<ChatResponse> chat(ChatRequest request);
}
