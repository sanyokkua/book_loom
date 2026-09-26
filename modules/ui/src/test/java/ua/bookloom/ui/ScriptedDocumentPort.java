package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;

/**
 * A hand-written {@link DocumentPort} that answers {@code open} with what the test scripted for that path (or a
 * default, or a thrown exception), records every path it was asked to open and every document it was asked to close
 * together with whether the call arrived on the FX Application Thread, and can hold each {@code open} until the test
 * releases it. {@code write} and {@code unmask} are never expected and answer with an {@code internal} error.
 */
public final class ScriptedDocumentPort implements DocumentPort {

    private static final long WAIT_SECONDS = 10;

    private final Map<Path, Result<Document>> byPath = new ConcurrentHashMap<>();
    private volatile Result<Document> fallback;
    private volatile @Nullable RuntimeException failure;
    private final Deque<CountDownLatch> unreleased = new ConcurrentLinkedDeque<>();
    private volatile @Nullable CountDownLatch gate;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final List<Path> opened = new CopyOnWriteArrayList<>();
    private final List<Boolean> openedOnFxThread = new CopyOnWriteArrayList<>();
    private final List<Document> closed = new CopyOnWriteArrayList<>();
    private final List<Boolean> closedOnFxThread = new CopyOnWriteArrayList<>();
    private final AtomicInteger openCalls = new AtomicInteger();

    private ScriptedDocumentPort(final Result<Document> fallback) {
        this.fallback = fallback;
    }

    /** A port nobody is expected to ask; an unscripted path answers with an {@code internal} error. */
    public static ScriptedDocumentPort idle() {
        return new ScriptedDocumentPort(Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer for this file.")));
    }

    /** A port that answers every path with {@code answer} until a path is scripted on its own. */
    public static ScriptedDocumentPort returning(final Result<Document> answer) {
        return new ScriptedDocumentPort(answer);
    }

    /** A port that blocks each {@code open} until {@link #release()} and then answers with {@code answer}. */
    public static ScriptedDocumentPort gated(final Result<Document> answer) {
        final ScriptedDocumentPort port = new ScriptedDocumentPort(answer);
        port.hold();
        return port;
    }

    /** From now on answers {@code source} with {@code answer}; other paths keep the default. */
    public void on(final Path source, final Result<Document> answer) {
        byPath.put(source, answer);
    }

    /** From now on answers every unscripted path with {@code next}; a previously scripted exception no longer applies. */
    public void respondWith(final Result<Document> next) {
        failure = null;
        fallback = next;
    }

    /** From now on throws {@code thrown} from every unscripted {@code open}, the way a defective adapter would. */
    public void throwing(final RuntimeException thrown) {
        failure = thrown;
    }

    /** From now on blocks each {@code open} until a {@link #release()} reaches it, oldest first. */
    public void hold() {
        final CountDownLatch latch = new CountDownLatch(1);
        unreleased.addLast(latch);
        gate = latch;
    }

    /** Lets the oldest still-held {@code open} answer. */
    public void release() {
        final CountDownLatch oldest = unreleased.pollFirst();
        if (oldest != null) {
            oldest.countDown();
        }
    }

    /** Blocks until {@code open} has been entered, so a test knows the call is truly in flight. */
    public void awaitEntered() throws InterruptedException {
        if (!entered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the port was never asked to open a book");
        }
    }

    @Override
    public Result<Document> open(final Path source) {
        final CountDownLatch held = gate;
        opened.add(source);
        openedOnFxThread.add(Platform.isFxApplicationThread());
        openCalls.incrementAndGet();
        entered.countDown();
        awaitGate(held);
        final Result<Document> scripted = byPath.get(source);
        if (scripted != null) {
            return scripted;
        }
        final RuntimeException thrown = failure;
        if (thrown != null) {
            throw thrown;
        }
        return fallback;
    }

    @Override
    public Result<Boolean> close(final Document document) {
        closed.add(document);
        closedOnFxThread.add(Platform.isFxApplicationThread());
        return Result.ok(true);
    }

    @Override
    public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
        return Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The import tests never write a book."));
    }

    @Override
    public Result<String> unmask(final BookFormat format, final Segment segment, final String translatedMasked) {
        return Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The import tests never unmask a segment."));
    }

    private static void awaitGate(final @Nullable CountDownLatch held) {
        if (held == null) {
            return;
        }
        try {
            if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the port");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", e);
        }
    }

    public int openCallCount() {
        return openCalls.get();
    }

    /** Every path {@code open} was asked about, in call order. */
    public List<Path> openedPaths() {
        return List.copyOf(opened);
    }

    /** For each {@code open} call, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> openCallsOnFxThread() {
        return List.copyOf(openedOnFxThread);
    }

    /** Every document {@code close} was asked to release, in call order. */
    public List<Document> closedDocuments() {
        return List.copyOf(closed);
    }

    /** For each {@code close} call, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> closeCallsOnFxThread() {
        return List.copyOf(closedOnFxThread);
    }
}
