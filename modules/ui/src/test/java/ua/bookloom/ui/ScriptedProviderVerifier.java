package ua.bookloom.ui;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationListener;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;

/**
 * A hand-written {@link ProviderVerifier} that answers with what the test scripted (a result, or a thrown exception),
 * records every selection and policy it was asked about, and can hold its answer back until the test releases it.
 * The script can be replaced after the verifier has been wired into a graph, so one graph serves several scenarios.
 */
public final class ScriptedProviderVerifier implements ProviderVerifier {

    private static final long WAIT_SECONDS = 10;

    /** One check of a description that is not saved: the description, the model asked about and the depth. */
    public record DraftCall(ProviderConfig config, String modelId, VerificationPolicy policy) {}

    /** One request the verifier tells its listener of before it answers, as the real one does before each call. */
    public record Announced(
            VerificationStage stage, @Nullable AdvancedCheck check, VerificationExchange request) {}

    private final List<Announced> announcements = new CopyOnWriteArrayList<>();
    private final List<VerificationStage> beginnings = new CopyOnWriteArrayList<>();
    private volatile @Nullable Result<VerificationReport> answer;
    private volatile @Nullable RuntimeException failure;
    private volatile @Nullable CountDownLatch gate;
    private final Deque<CountDownLatch> unreleased = new ConcurrentLinkedDeque<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final List<ModelSelection> selections = new CopyOnWriteArrayList<>();
    private final List<VerificationPolicy> policies = new CopyOnWriteArrayList<>();
    private final List<String> connectionCalls = new CopyOnWriteArrayList<>();
    private final List<DraftCall> drafts = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<ProviderConfig> detections = new CopyOnWriteArrayList<>();
    private volatile Result<EngineDetection> detection =
            Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer."));

    private ScriptedProviderVerifier(final @Nullable Result<VerificationReport> answer) {
        this.answer = answer;
    }

    /** A verifier that answers at once with {@code answer}. */
    public static ScriptedProviderVerifier returning(final Result<VerificationReport> answer) {
        return new ScriptedProviderVerifier(answer);
    }

    /** A verifier that throws {@code failure} at once, the way a defective adapter would. */
    public static ScriptedProviderVerifier throwing(final RuntimeException failure) {
        final ScriptedProviderVerifier verifier = new ScriptedProviderVerifier(null);
        verifier.failure = failure;
        return verifier;
    }

    /** A verifier that blocks inside {@code verify} until {@link #release()} and then answers with {@code answer}. */
    public static ScriptedProviderVerifier gated(final Result<VerificationReport> answer) {
        final ScriptedProviderVerifier verifier = new ScriptedProviderVerifier(answer);
        verifier.hold();
        return verifier;
    }

    /** A verifier that is never expected to be asked; it answers with an empty report if it is. */
    public static ScriptedProviderVerifier idle() {
        return returning(Result.ok(new VerificationReport(List.of())));
    }

    /** From now on answers with {@code next}; a previously scripted exception no longer applies. */
    public void respondWith(final Result<VerificationReport> next) {
        failure = null;
        answer = next;
    }

    /**
     * From now on blocks each call inside {@code verify} until a {@link #release()} reaches it; a call already held
     * keeps waiting on its own hold, so several checks can be in flight and be answered one at a time, oldest first.
     */
    public void hold() {
        final CountDownLatch latch = new CountDownLatch(1);
        unreleased.addLast(latch);
        gate = latch;
    }

    /** From now on answers Detect again with {@code next}; until then it answers that the server does not answer. */
    public void detectWith(final Result<EngineDetection> next) {
        detection = next;
    }

    /**
     * Records the description and answers with the scripted detection at once. A detection asks no model, so it is
     * not counted in {@link #callCount()} and is never held.
     */
    @Override
    public Result<EngineDetection> detectEngine(final ProviderConfig config) {
        detections.add(config);
        return detection;
    }

    /** Every description Detect again was asked about, oldest first. */
    public List<ProviderConfig> detections() {
        return List.copyOf(detections);
    }

    /** Records the provider id and answers as {@code verify} does, holding and throwing the same way. */
    @Override
    public Result<VerificationReport> verifyConnection(final String providerId) {
        connectionCalls.add(providerId);
        return answer();
    }

    @Override
    public Result<VerificationReport> verify(final ModelSelection selection, final VerificationPolicy policy) {
        final CountDownLatch held = gate;
        selections.add(selection);
        policies.add(policy);
        calls.incrementAndGet();
        entered.countDown();
        return answer(held);
    }

    /** Records the description (and, like {@code verify}, the selection and policy) and answers as {@code verify} does. */
    @Override
    public Result<VerificationReport> verify(
            final ProviderConfig draft, final String modelId, final VerificationPolicy policy) {
        final CountDownLatch held = gate;
        drafts.add(new DraftCall(draft, modelId, policy));
        selections.add(new ModelSelection(draft.id(), modelId));
        policies.add(policy);
        calls.incrementAndGet();
        entered.countDown();
        return answer(held);
    }

    /** From now on tells a listening check of {@code heard}, in order, before it holds or answers. */
    public void announce(final Announced... heard) {
        announcements.clear();
        announcements.addAll(List.of(heard));
    }

    /** From now on tells a listening check that each of {@code begun} has started, in order, before anything else. */
    public void begin(final VerificationStage... begun) {
        beginnings.clear();
        beginnings.addAll(List.of(begun));
    }

    /**
     * Tells the listener of every scripted stage start and request, then answers as the unheard form does, then tells it of each stage
     * of the answer, the order the real verifier keeps.
     */
    @Override
    public Result<VerificationReport> verify(
            final ProviderConfig draft,
            final String modelId,
            final VerificationPolicy policy,
            final VerificationListener listener) {
        beginnings.forEach(listener::stageStarted);
        announcements.forEach(heard -> listener.requestSent(heard.stage(), heard.check(), heard.request()));
        final Result<VerificationReport> result = verify(draft, modelId, policy);
        final VerificationReport report = result.data();
        if (report != null) {
            report.stages().forEach(listener::stageFinished);
        }
        return result;
    }

    private Result<VerificationReport> answer() {
        final CountDownLatch held = gate;
        calls.incrementAndGet();
        entered.countDown();
        return answer(held);
    }

    private Result<VerificationReport> answer(final @Nullable CountDownLatch held) {
        awaitGate(held);
        final RuntimeException thrown = failure;
        if (thrown != null) {
            throw thrown;
        }
        return Objects.requireNonNull(answer, "a verifier that does not throw must have an answer");
    }

    private static void awaitGate(final @Nullable CountDownLatch held) {
        if (held == null) {
            return;
        }
        try {
            if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the verifier");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", e);
        }
    }

    /** Lets the oldest still-held call answer. */
    public void release() {
        final CountDownLatch oldest = unreleased.pollFirst();
        if (oldest != null) {
            oldest.countDown();
        }
    }

    /** Blocks until {@code verify} or {@code verifyConnection} has been entered {@code expected} times in all. */
    public void awaitCalls(final int expected) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (calls.get() < expected) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the verifier was asked " + calls.get() + " time(s), not " + expected);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    /** Blocks until {@code verify} has been entered, so a test knows the check is truly in flight. */
    public void awaitEntered() throws InterruptedException {
        if (!entered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the verifier was never asked");
        }
    }

    public int callCount() {
        return calls.get();
    }

    public List<ModelSelection> selections() {
        return List.copyOf(selections);
    }

    /** The provider ids of every connection-only call, kept apart from {@link #selections()}. */
    public List<String> connectionCalls() {
        return List.copyOf(connectionCalls);
    }

    /** Every check of a description that was not saved, oldest first. */
    public List<DraftCall> drafts() {
        return List.copyOf(drafts);
    }

    public List<VerificationPolicy> policies() {
        return List.copyOf(policies);
    }
}
