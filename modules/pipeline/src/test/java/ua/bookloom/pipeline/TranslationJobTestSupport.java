package ua.bookloom.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;

/** Shared real-document and controlled-model setup for translation job acceptance tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslationJobTestSupport {

    private static final long WAIT_SECONDS = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentLinkedQueue<ExecutorService> EXECUTORS = new ConcurrentLinkedQueue<>();

    static DocumentPort documents() {
        return Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    static TranslationJobImpl job(
            final DocumentPort documents, final Path source, final Path destination, final ChatModel model) {
        return new TranslationJobImpl(
                documents, new TranslationRequest(source, destination, "uk", "en", false), model, new ObjectMapper());
    }

    static SegmentTranslator segmentTranslator(
            final DocumentPort documents,
            final ChatModel model,
            final ua.bookloom.api.document.BookFormat format,
            final String targetLanguage,
            @Nullable final String sourceLanguage) {
        return new SegmentTranslator(
                documents,
                model,
                format,
                new DraftPromptBuilder(sourceLanguage, targetLanguage),
                new DraftReplyParser(new ObjectMapper()));
    }

    static ScriptedChatModel replies(final String... content) {
        final ScriptedChatModel model = new ScriptedChatModel();
        for (final String reply : content) {
            model.answer(Result.ok(new ChatResponse(targetReply(reply), FinishReason.STOP)));
        }
        return model;
    }

    static String targetReply(final String target) {
        try {
            return MAPPER.writeValueAsString(Map.of("target", target));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode scripted target", cause);
        }
    }

    static JobReport report(final Result<JobReport> result) {
        return java.util.Objects.requireNonNull(result.data(), "job report");
    }

    static ExecutorService executor() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        EXECUTORS.add(executor);
        return executor;
    }

    static <T> T await(final Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException cause) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for translation job", cause);
        } catch (Exception cause) {
            future.cancel(true);
            throw new AssertionError("timed out waiting for translation job", cause);
        }
    }

    static JobEvent await(final LinkedBlockingQueue<JobEvent> events) {
        try {
            final JobEvent event = events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            return java.util.Objects.requireNonNull(event, "expected job event");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for job event", cause);
        }
    }

    static Paused awaitPaused(final LinkedBlockingQueue<Paused> pauses) {
        try {
            final Paused pause = pauses.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            return java.util.Objects.requireNonNull(pause, "expected pause event");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for pause event", cause);
        }
    }

    static void capturePaused(final LinkedBlockingQueue<Paused> pauses, final JobEvent event) {
        if (event instanceof Paused pause) {
            pauses.add(pause);
        }
    }

    static void shutdown(final ExecutorService executor) {
        EXECUTORS.remove(executor);
        executor.shutdownNow();
        awaitTermination(executor);
    }

    static void shutdownAll() {
        while (!EXECUTORS.isEmpty()) {
            shutdown(java.util.Objects.requireNonNull(EXECUTORS.poll(), "executor"));
        }
    }

    private static void awaitTermination(final ExecutorService executor) {
        try {
            if (!executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("translation job executor did not terminate");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while terminating translation job executor", cause);
        }
    }

    static final class BlockingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        BlockingChatModel(final ChatModel delegate) {
            this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            entered.countDown();
            await(released);
            throwIfFailed();
            return delegate.chat(request);
        }

        void awaitEntered() {
            await(entered);
        }

        void release() {
            released.countDown();
        }

        private void await(final CountDownLatch latch) {
            try {
                if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for controlled model");
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, cause);
            }
        }

        private void throwIfFailed() {
            final Throwable cause = failure.get();
            if (cause != null) {
                throw new AssertionError("controlled model interrupted", cause);
            }
        }
    }

    static final class SecondBlockingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        SecondBlockingChatModel(final ChatModel delegate) {
            this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            if (calls.incrementAndGet() == 2) {
                entered.countDown();
                await(released);
            }
            return delegate.chat(request);
        }

        void awaitSecondCall() {
            await(entered);
        }

        void releaseSecondCall() {
            released.countDown();
        }

        private static void await(final CountDownLatch latch) {
            try {
                if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for second model call");
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for second model call", cause);
            }
        }
    }

    static final class BlockingClosePort extends BookExporterTestSupport.ForwardingDocumentPort {

        private final AtomicInteger closes = new AtomicInteger();
        private final int blockingClose;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        BlockingClosePort(final DocumentPort delegate, final int blockingClose) {
            super(delegate);
            this.blockingClose = blockingClose;
        }

        @Override
        public Result<Boolean> close(final ua.bookloom.api.document.Document document) {
            final Result<Boolean> result = super.close(document);
            if (closes.incrementAndGet() == blockingClose) {
                entered.countDown();
                await(released);
            }
            return result;
        }

        void awaitBlockingClose() {
            await(entered);
        }

        void releaseClose() {
            released.countDown();
        }

        private static void await(final CountDownLatch latch) {
            try {
                if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for export close");
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for export close", cause);
            }
        }
    }
}
