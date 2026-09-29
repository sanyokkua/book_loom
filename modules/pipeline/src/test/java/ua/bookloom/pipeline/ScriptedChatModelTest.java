package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ResponseFormat;

/** The test double the job and the test thread share: scripted, blockable and readable while a call is running. */
class ScriptedChatModelTest {

    private static final long WAIT_SECONDS = 5;
    private static final int WRITES = 500;
    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "Hello."));

    private final ExecutorService workers = Executors.newCachedThreadPool();

    @AfterEach
    void stopWorkers() {
        workers.shutdownNow();
    }

    @Test
    void answerTo_responseFormatName_answersOnlyMatchingRequests() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo("judge", reply("SCORE")).answer(reply("OTHER"));

        final Result<ChatResponse> other = model.chat(request("target"));
        final Result<ChatResponse> judge = model.chat(request("judge"));

        assertThat(contentOf(other)).isEqualTo("OTHER");
        assertThat(contentOf(judge)).isEqualTo("SCORE");
    }

    @Test
    void blockNthRequest_untilRelease_returnsAfterRelease() {
        final ScriptedChatModel model =
                new ScriptedChatModel().blockNthRequest(2).answer(reply("ONE")).answer(reply("TWO"));
        final AtomicBoolean secondReturned = new AtomicBoolean();

        model.chat(request("target"));
        final Future<Result<ChatResponse>> second = workers.submit(() -> {
            final Result<ChatResponse> result = model.chat(request("target"));
            secondReturned.set(true);
            return result;
        });
        model.awaitRequests(2);
        final boolean returnedWhileHeld = secondReturned.get();
        model.release();

        assertThat(returnedWhileHeld).isFalse();
        assertThat(contentOf(get(second))).isEqualTo("TWO");
    }

    @Test
    void blockedRequest_interrupted_returnsCancelled() {
        final ScriptedChatModel model =
                new ScriptedChatModel().blockNthRequest(1).answer(reply("ONE"));
        final AtomicReference<Thread> caller = new AtomicReference<>();
        final Future<Result<ChatResponse>> blocked = workers.submit(() -> {
            caller.set(Thread.currentThread());
            return model.chat(request("target"));
        });
        model.awaitRequests(1);

        Objects.requireNonNull(caller.get(), "the calling thread").interrupt();

        final AppError error = get(blocked).error();
        assertThat(error).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.cancelled);
    }

    @Test
    void awaitRequests_countReached_returnsWithinTheBound() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("ONE")).answer(reply("TWO"));
        final Future<Result<ChatResponse>> first = workers.submit(() -> model.chat(request("target")));
        final Future<Result<ChatResponse>> second = workers.submit(() -> model.chat(request("target")));

        model.awaitRequests(2);

        assertThat(model.requests()).hasSize(2);
        assertThat(get(first).isOk()).isTrue();
        assertThat(get(second).isOk()).isTrue();
    }

    @Test
    void requests_readWhileAnotherThreadChats_seesNoTornList() {
        final ScriptedChatModel model = new ScriptedChatModel();
        IntStream.range(0, WRITES).forEach(i -> model.answer(reply("R" + i)));
        final Future<?> writer =
                workers.submit(() -> IntStream.range(0, WRITES).forEach(i -> model.chat(request("target"))));

        final long tornReads = tornReadsWhileWriting(model, writer);

        assertThat(tornReads).isZero();
        assertThat(model.requests()).hasSize(WRITES);
    }

    /** Reads the list as fast as it can until the writer is done, counting a snapshot that is smaller than the one before. */
    private static long tornReadsWhileWriting(final ScriptedChatModel model, final Future<?> writer) {
        final int[] previousSize = {0};
        return Stream.generate(model::requests)
                .takeWhile(snapshot -> !writer.isDone() || snapshot.size() < WRITES)
                .filter(snapshot -> isTorn(snapshot, previousSize))
                .count();
    }

    private static boolean isTorn(final List<ChatRequest> snapshot, final int[] previousSize) {
        final boolean torn = snapshot.size() < previousSize[0];
        previousSize[0] = snapshot.size();
        return torn;
    }

    private static ChatRequest request(final String formatName) {
        return new ChatRequest(MESSAGES, null, new ResponseFormat(formatName, "{}"));
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private static String contentOf(final Result<ChatResponse> result) {
        return Objects.requireNonNull(result.data(), "reply").content();
    }

    private static Result<ChatResponse> get(final Future<Result<ChatResponse>> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception cause) {
            throw new AssertionError("the blocked call did not return", cause);
        }
    }
}
