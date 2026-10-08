package ua.bookloom.pipeline.eval;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * Passes every call to the model under test and keeps what was sent, so a stage suite can count calls, errors and
 * repeats per case and a test can compare the request with what the production class builds. The model seam the
 * production classes get is the run's own {@link JobModelCalls}, sized to the eval window as in the other suites.
 */
final class StageProbe implements ChatModel {

    /** What the calls since a mark came to. */
    record Counts(int calls, int failed, int repeated) {}

    private final ChatModel model;
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Boolean> failures = new CopyOnWriteArrayList<>();

    StageProbe(final ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /** The run's model-call seam over this probe. */
    ModelCalls calls(final String targetLanguage) {
        return new JobModelCalls(onSent -> this, event -> {}, Clock.systemUTC(), targetLanguage, EvalProject.window());
    }

    /** Every request sent so far, in order. */
    List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    /** The number of calls so far, to hand to {@link #since}. */
    int mark() {
        return requests.size();
    }

    /** The calls made after {@code mark}; a repeat is a request whose messages an earlier call after the mark sent. */
    Counts since(final int mark) {
        final Set<List<?>> seen = new HashSet<>();
        int repeated = 0;
        int failed = 0;
        for (int i = mark; i < requests.size(); i++) {
            repeated += seen.add(requests.get(i).messages()) ? 0 : 1;
            failed += failures.get(i) ? 1 : 0;
        }
        return new Counts(requests.size() - mark, failed, repeated);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return seen(request, model.chat(request));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        return seen(request, model.chat(request, attempts));
    }

    private Result<ChatResponse> seen(final ChatRequest request, final Result<ChatResponse> result) {
        requests.add(request);
        failures.add(result.isErr());
        return result;
    }
}
