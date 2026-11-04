package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.BatchSchema;

/**
 * A deterministic chat model that records requests and consumes scripted outcomes in order, safe to use from the job
 * thread and the test thread at once. Public so the {@code judge} and {@code heal} test packages can script the
 * {@link ua.bookloom.pipeline.prompt.ModelCalls} seam with it too.
 *
 * <p>An outcome added with {@link #answerTo(String, Result)} answers only a request whose response format has that
 * name — a translate-family call is also found under {@code draft}, {@code repair} or {@code structural-repair} by its
 * kind — and every other request takes the next outcome added with {@link #answer(Result)}.
 */
public final class ScriptedChatModel implements ChatModel {

    private static final long WAIT_SECONDS = 5;
    private static final String DRAFT = "draft";
    private static final String REPAIR = "repair";
    private static final String REASK = "structural-repair";
    private static final Pattern ITEM = Pattern.compile("<s id=\"[^\"]+\">");

    private final ConcurrentLinkedDeque<Supplier<Result<ChatResponse>>> outcomes = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Supplier<Result<ChatResponse>>>> named =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, CountDownLatch> arrivals = new ConcurrentHashMap<>();
    private final AtomicInteger seen = new AtomicInteger();
    private final AtomicInteger blockedOrdinal = new AtomicInteger();
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile ModelProfile profile = ModelProfile.defaults();

    /** Makes this model declare a profile whose batch is {@code batchSize} items, as a profiled real model would. */
    public ScriptedChatModel withBatchSize(final int batchSize) {
        final ModelProfile base = ModelProfile.defaults();
        profile = new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                batchSize,
                base.tier());
        return this;
    }

    @Override
    public ModelProfile profile() {
        return profile;
    }

    public ScriptedChatModel answer(final Result<ChatResponse> result) {
        Objects.requireNonNull(result, "result");
        outcomes.addLast(() -> result);
        return this;
    }

    /** Scripts the same answer for the next {@code times} requests. */
    public ScriptedChatModel answerTimes(final int times, final Result<ChatResponse> result) {
        java.util.stream.IntStream.range(0, times).forEach(ignored -> answer(result));
        return this;
    }

    public ScriptedChatModel answerTo(final String responseFormatName, final Result<ChatResponse> result) {
        Objects.requireNonNull(responseFormatName, "responseFormatName");
        Objects.requireNonNull(result, "result");
        named.computeIfAbsent(responseFormatName, name -> new ConcurrentLinkedDeque<>())
                .addLast(() -> result);
        return this;
    }

    public ScriptedChatModel throwFailure(final RuntimeException failure) {
        Objects.requireNonNull(failure, "failure");
        outcomes.addLast(() -> {
            throw failure;
        });
        return this;
    }

    /** Holds the n-th request (counted from one) until {@link #release()}; an interrupt answers it cancelled. */
    public ScriptedChatModel blockNthRequest(final int n) {
        blockedOrdinal.set(n);
        return this;
    }

    public void release() {
        released.countDown();
    }

    /** Waits until at least {@code n} requests have arrived, for at most five seconds. */
    public void awaitRequests(final int n) {
        final CountDownLatch latch = arrivals.computeIfAbsent(n, count -> new CountDownLatch(1));
        openLatchesReached();
        if (!awaitLatch(latch)) {
            throw new AssertionError("timed out waiting for " + n + " requests, saw " + requests.size());
        }
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final int ordinal = seen.incrementAndGet();
        requests.add(request);
        openLatchesReached();
        if (ordinal == blockedOrdinal.get() && !awaitRelease()) {
            return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The scripted request was interrupted."));
        }
        return Objects.requireNonNull(nextOutcome(request).get(), "scripted outcome");
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    private Supplier<Result<ChatResponse>> nextOutcome(final ChatRequest request) {
        for (final String name : namesOf(request)) {
            final var forName = named.get(name);
            final var byName = forName == null ? null : forName.pollFirst();
            if (byName != null) {
                return byName;
            }
        }
        return outcomes.removeFirst();
    }

    /**
     * The name an answer is scripted under: every translate-family call asks for the batch schema, so a repair is
     * found under {@value #REPAIR}, a format re-ask under {@value #REASK}, a batch of one under {@value #DRAFT} and only
     * a batch of two or more under the schema's own name.
     */
    private static List<String> namesOf(final ChatRequest request) {
        final var format = request.responseFormat();
        if (format == null) {
            return List.of();
        }
        if (!BatchSchema.FORMAT_NAME.equals(format.name())) {
            return List.of(format.name());
        }
        if (request.callKind() == CallKind.REPAIR) {
            return List.of(REPAIR);
        }
        if (request.callKind() == CallKind.STRUCTURAL_REPAIR) {
            return List.of(REASK);
        }
        return items(request) == 1 ? List.of(DRAFT) : List.of(format.name());
    }

    /**
     * The name a call is scripted and told apart by: its answer schema's name, a translate-family call named by its
     * kind ({@value #DRAFT} for a batch of one, {@value #REPAIR}, {@value #REASK}) since they all ask for the batch
     * schema.
     *
     * @param request the non-null request
     * @return the name; never null
     */
    public static String callName(final ChatRequest request) {
        final List<String> names = namesOf(request);
        return names.isEmpty() ? "" : names.getFirst();
    }

    private static long items(final ChatRequest request) {
        final String user = request.messages().getLast().content();
        final int block = user.lastIndexOf("<Items>");
        return block < 0 ? 0 : ITEM.matcher(user.substring(block)).results().count();
    }

    private void openLatchesReached() {
        final int arrived = requests.size();
        arrivals.forEach((n, latch) -> {
            if (arrived >= n) {
                latch.countDown();
            }
        });
    }

    private boolean awaitRelease() {
        try {
            if (!released.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("a blocked request was never released");
            }
            return true;
        } catch (InterruptedException _) {
            return false;
        }
    }

    private static boolean awaitLatch(final CountDownLatch latch) {
        try {
            return latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for requests", interrupted);
        }
    }
}
