package ua.bookloom.ui;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;

/**
 * A hand-written {@link ProviderVerifier} that answers with what the test scripted (a result, or a thrown exception),
 * records every selection and policy it was asked about, and can hold its answer back until the test releases it.
 * The script can be replaced after the verifier has been wired into a graph, so one graph serves several scenarios.
 */
public final class ScriptedProviderVerifier implements ProviderVerifier {

    private static final long WAIT_SECONDS = 10;

    private volatile @Nullable Result<VerificationReport> answer;
    private volatile @Nullable RuntimeException failure;
    private volatile @Nullable CountDownLatch gate;
    private final Deque<CountDownLatch> unreleased = new ConcurrentLinkedDeque<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final List<ModelSelection> selections = new CopyOnWriteArrayList<>();
    private final List<VerificationPolicy> policies = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

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

    @Override
    public Result<VerificationReport> verify(final ModelSelection selection, final VerificationPolicy policy) {
        final CountDownLatch held = gate;
        selections.add(selection);
        policies.add(policy);
        calls.incrementAndGet();
        entered.countDown();
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

    /** Blocks until {@code verify} has been entered {@code expected} times in all. */
    public void awaitCalls(final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (calls.get() < expected) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the verifier was asked " + calls.get() + " time(s), not " + expected);
            }
            TimeUnit.MILLISECONDS.sleep(5);
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

    public List<VerificationPolicy> policies() {
        return List.copyOf(policies);
    }
}
