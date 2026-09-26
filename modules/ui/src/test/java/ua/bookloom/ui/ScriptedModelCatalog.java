package ua.bookloom.ui;

import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;

/**
 * A hand-written {@link ModelCatalog} that answers with what the test scripted (a list, an error, or a thrown
 * exception), records every provider id it was asked about, and can hold one answer back until the test releases it.
 *
 * <p>The answer is read when a call <em>enters</em>, before it waits on a hold, so a call held in flight keeps the
 * answer that was scripted when it began even if the script is replaced afterwards. A hold applies to the next call
 * only, so a test can keep one listing in flight while a later one answers at once.
 */
public final class ScriptedModelCatalog implements ModelCatalog {

    private static final long WAIT_SECONDS = 10;

    private volatile @Nullable Result<List<ModelInfo>> answer;
    private volatile @Nullable RuntimeException failure;
    private final Deque<CountDownLatch> pendingHolds = new ConcurrentLinkedDeque<>();
    private final Deque<CountDownLatch> unreleased = new ConcurrentLinkedDeque<>();
    private final List<String> providerIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    private ScriptedModelCatalog(final @Nullable Result<List<ModelInfo>> answer) {
        this.answer = answer;
    }

    /** A catalogue that answers at once with an empty list, the way a server that holds no models does. */
    public static ScriptedModelCatalog idle() {
        return new ScriptedModelCatalog(Result.ok(List.of()));
    }

    /** A catalogue that answers at once with {@code ids}, in that order. */
    public static ScriptedModelCatalog returning(final String... ids) {
        return new ScriptedModelCatalog(listOf(ids));
    }

    /** A catalogue that answers at once with {@code error}. */
    public static ScriptedModelCatalog failing(final AppError error) {
        return new ScriptedModelCatalog(Result.err(error));
    }

    /** A catalogue that throws {@code failure} at once, the way a defective adapter would. */
    public static ScriptedModelCatalog throwing(final RuntimeException failure) {
        final ScriptedModelCatalog catalog = new ScriptedModelCatalog(null);
        catalog.failure = failure;
        return catalog;
    }

    /** A catalogue whose first call blocks until {@link #release()}, then answers with {@code ids}. */
    public static ScriptedModelCatalog gated(final String... ids) {
        final ScriptedModelCatalog catalog = returning(ids);
        catalog.hold();
        return catalog;
    }

    /** From now on answers with {@code ids}; a previously scripted error or exception no longer applies. */
    public void respondWith(final String... ids) {
        failure = null;
        answer = listOf(ids);
    }

    /** From now on answers with {@code error}; a previously scripted exception no longer applies. */
    public void respondWith(final AppError error) {
        failure = null;
        answer = Result.err(error);
    }

    /** Makes the next call not yet made block inside {@code listModels} until a {@link #release()} reaches it. */
    public void hold() {
        final CountDownLatch latch = new CountDownLatch(1);
        pendingHolds.addLast(latch);
        unreleased.addLast(latch);
    }

    /** Lets the oldest still-held call answer. */
    public void release() {
        final CountDownLatch oldest = unreleased.pollFirst();
        if (oldest != null) {
            oldest.countDown();
        }
    }

    @Override
    public Result<List<ModelInfo>> listModels(final String providerId) {
        final Result<List<ModelInfo>> scripted = answer;
        final RuntimeException thrown = failure;
        final CountDownLatch held = pendingHolds.pollFirst();
        providerIds.add(providerId);
        calls.incrementAndGet();
        awaitHold(held);
        if (thrown != null) {
            throw thrown;
        }
        return Objects.requireNonNull(scripted, "a catalogue that does not throw must have an answer");
    }

    private static void awaitHold(final @Nullable CountDownLatch held) {
        if (held == null) {
            return;
        }
        try {
            if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the catalogue");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", e);
        }
    }

    /** Blocks until {@code listModels} has been entered {@code expected} times in all. */
    public void awaitCalls(final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (calls.get() < expected) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the catalogue was asked " + calls.get() + " time(s), not " + expected);
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
    }

    /** Blocks until {@code listModels} has been entered at least once. */
    public void awaitEntered() throws InterruptedException {
        awaitCalls(1);
    }

    public int callCount() {
        return calls.get();
    }

    /** The provider id of every call, oldest first. */
    public List<String> providerIds() {
        return List.copyOf(providerIds);
    }

    private static Result<List<ModelInfo>> listOf(final String... ids) {
        return Result.ok(Arrays.stream(ids).map(ModelInfo::new).toList());
    }
}
