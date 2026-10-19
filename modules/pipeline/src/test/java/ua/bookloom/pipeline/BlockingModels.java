package ua.bookloom.pipeline;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/** Models that hold a request until the test releases it, answering it anyway, as a provider ignoring an interrupt. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BlockingModels {

    static final class BlockingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        BlockingChatModel(final ChatModel delegate) {
            this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            entered.countDown();
            await(released);
            return delegate.chat(request);
        }

        void awaitEntered() {
            await(entered);
        }

        void release() {
            released.countDown();
        }

        private void await(final CountDownLatch latch) {
            TranslationJobTestSupport.awaitIgnoringInterrupt(latch, "controlled model");
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
            TranslationJobTestSupport.awaitIgnoringInterrupt(latch, "second model call");
        }
    }
}
