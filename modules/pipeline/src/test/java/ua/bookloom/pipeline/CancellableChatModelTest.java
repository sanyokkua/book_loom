package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;

/** The decorator sends nothing once a stop or a pause is requested, and always closes the call it opened. */
class CancellableChatModelTest {

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")));

    private final JobControl control = new JobControl();
    private final ScriptedChatModel delegate = TranslationJobTestSupport.replies("ONE.");
    private final AtomicInteger entered = new AtomicInteger();
    private final CancellableChatModel model = new CancellableChatModel(delegate, control, entered::incrementAndGet);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void chat_noRequest_returnsTheDelegateAnswer() {
        final Result<ChatResponse> result = model.chat(REQUEST);

        assertThat(result.isOk()).isTrue();
        assertThat(delegate.requests()).hasSize(1);
    }

    @Test
    void chat_pauseRequested_returnsCancelledWithoutCallingTheDelegate() {
        control.pause();

        final Result<ChatResponse> result = model.chat(REQUEST);

        assertThat(result.error()).isNotNull().extracting(error -> error.code()).isEqualTo(ErrorCode.cancelled);
        assertThat(delegate.requests()).isEmpty();
    }

    @Test
    void chat_cancelRequested_returnsCancelledWithoutCallingTheDelegate() {
        control.cancel();

        final Result<ChatResponse> result = model.chat(REQUEST);

        assertThat(result.error()).isNotNull().extracting(error -> error.code()).isEqualTo(ErrorCode.cancelled);
        assertThat(delegate.requests()).isEmpty();
    }

    // A call left open would let a later pause interrupt the job thread during export.
    @Test
    void chat_afterItReturns_aLaterPauseInterruptsNothing() {
        model.chat(REQUEST);

        control.pause();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void chat_delegateThrows_stillClosesTheCall() {
        final CancellableChatModel failing = new CancellableChatModel(
                new ScriptedChatModel().throwFailure(new IllegalStateException("boom")),
                control,
                entered::incrementAndGet);

        assertThatThrownBy(() -> failing.chat(REQUEST)).isInstanceOf(IllegalStateException.class);
        control.pause();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    // Announcing a call that was refused would tell the screen the model is busy when nothing was sent.
    @Test
    void chat_pauseRequested_announcesNoCall() {
        control.pause();

        model.chat(REQUEST);

        assertThat(entered).hasValue(0);
    }

    // One announcement per model call entered (draft, each repair); transport retries inside a call announce nothing.
    @Test
    void chat_twoCalls_announcesEachOnce() {
        final CancellableChatModel twice = new CancellableChatModel(
                TranslationJobTestSupport.replies("ONE.", "TWO."), control, entered::incrementAndGet);

        twice.chat(REQUEST);
        twice.chat(REQUEST);

        assertThat(entered).hasValue(2);
    }
}
