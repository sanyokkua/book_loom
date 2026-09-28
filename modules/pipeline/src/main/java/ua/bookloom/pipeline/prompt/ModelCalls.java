package ua.bookloom.pipeline.prompt;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The one seam every model call of a run goes through, so a later task can announce, time and redo a call in one
 * place instead of every call site reimplementing it (design D7/D8).
 */
@FunctionalInterface
public interface ModelCalls {

    /**
     * Sends one model call.
     *
     * @param kind which call this is
     * @param segmentId the segment the call is about, or null for a call that names no single segment (a judge
     *     call, which scores a whole chunk)
     * @param request the request to send
     * @return the model's reply, or the failure the call ended with
     */
    Result<ChatResponse> call(CallKind kind, @Nullable String segmentId, ChatRequest request);
}
