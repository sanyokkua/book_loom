package ua.bookloom.pipeline.eval;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * Passes every call to the model under test and keeps what was sent, so a stage suite can count calls, errors and
 * repeats per case and a test can compare the request with what the production class builds. The model seam the
 * production classes get is the run's own {@link JobModelCalls}, sized to the eval window as in the other suites. Each
 * call's reported usage is kept too, so a case's record can carry its times and tokens.
 */
final class StageProbe implements ChatModel {

    /** What the calls since a mark came to. */
    record Counts(int calls, int failed, int repeated) {}

    /**
     * One call as it was sent and answered.
     *
     * @param request the request sent
     * @param failed whether the call ended in an error
     * @param reply the reply's text; empty when the call failed
     * @param usage the figures the provider reported, or null when it reported none
     */
    record Call(
            ChatRequest request,
            boolean failed,
            String reply,
            @Nullable TokenUsage usage) {

        /** Rejects a missing part. */
        Call {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(reply, "reply");
        }
    }

    private final ChatModel model;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    StageProbe(final ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /** The run's model-call seam over this probe. */
    ModelCalls calls(final String targetLanguage) {
        return new JobModelCalls(onSent -> this, event -> {}, Clock.systemUTC(), targetLanguage, EvalProject.window());
    }

    /** Every request sent so far, in order. */
    List<ChatRequest> requests() {
        return calls.stream().map(Call::request).toList();
    }

    /** The calls made after {@code mark}, with the figures each reply reported, for a case's record. */
    List<Call> callsSince(final int mark) {
        return List.copyOf(calls.subList(mark, calls.size()));
    }

    /** The number of calls so far, to hand to {@link #since}. */
    int mark() {
        return calls.size();
    }

    /** The calls made after {@code mark}; a repeat is a request whose messages an earlier call after the mark sent. */
    Counts since(final int mark) {
        return since(mark, kind -> true);
    }

    /** As {@link #since(int)}, for the calls of one kind. */
    Counts since(final int mark, final CallKind kind) {
        return since(mark, kind::equals);
    }

    private Counts since(final int mark, final Predicate<CallKind> wanted) {
        final Set<List<?>> seen = new HashSet<>();
        int count = 0;
        int repeated = 0;
        int failed = 0;
        for (final Call call : callsSince(mark)) {
            if (wanted.test(call.request().callKind())) {
                count++;
                repeated += seen.add(call.request().messages()) ? 0 : 1;
                failed += call.failed() ? 1 : 0;
            }
        }
        return new Counts(count, failed, repeated);
    }

    /**
     * The reply the model gave to the first call of a kind made after {@code mark}.
     *
     * @return its text, or empty when no such call was made or it failed
     */
    Optional<String> replyOf(final int mark, final CallKind kind) {
        return callsSince(mark).stream()
                .filter(call -> call.request().callKind() == kind)
                .findFirst()
                .filter(call -> !call.failed())
                .map(Call::reply);
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
        final ChatResponse response = result.data();
        final String reply = response == null ? "" : response.content();
        calls.add(new Call(request, result.isErr(), reply, response == null ? null : response.usage()));
        return result;
    }
}
