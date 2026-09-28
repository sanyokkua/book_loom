package ua.bookloom.pipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/**
 * A deterministic chat model that records requests and consumes scripted outcomes in order. Public so the
 * {@code judge} and {@code heal} test packages can script the {@link ua.bookloom.pipeline.prompt.ModelCalls} seam
 * with it too.
 */
public final class ScriptedChatModel implements ChatModel {

    private final Deque<Supplier<Result<ChatResponse>>> outcomes = new ArrayDeque<>();
    private final List<ChatRequest> requests = new ArrayList<>();

    public ScriptedChatModel answer(final Result<ChatResponse> result) {
        Objects.requireNonNull(result, "result");
        outcomes.addLast(() -> result);
        return this;
    }

    public ScriptedChatModel throwFailure(final RuntimeException failure) {
        Objects.requireNonNull(failure, "failure");
        outcomes.addLast(() -> {
            throw failure;
        });
        return this;
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        requests.add(Objects.requireNonNull(request, "request"));
        return Objects.requireNonNull(outcomes.removeFirst().get(), "scripted outcome");
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }
}
