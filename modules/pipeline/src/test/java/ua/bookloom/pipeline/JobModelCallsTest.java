package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.RequestSummary;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.run.JobModelCalls;

/** The one place a run's model calls go through: each is announced as it goes out, and a refused one is not. */
class JobModelCallsTest {

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")));

    private final JobControl control = new JobControl(Set.of());
    private final ScriptedChatModel model = TranslationJobTestSupport.replies("ONE.", "TWO.");
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();
    private final JobModelCalls calls = new JobModelCalls(
            onSent -> new CancellableChatModel(model, control, onSent), events::add, new StepClock(), "uk");

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    // The old announcement named DRAFT for every call, so a screen could not tell a slow repair from a slow draft.
    @Test
    void call_structuralRepair_announcesStructuralRepairForItsSegment() {
        final Result<ChatResponse> result = calls.call(CallKind.STRUCTURAL_REPAIR, "Book.md:0", REQUEST);

        assertThat(result.isOk()).isTrue();
        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .map(ModelCallStarted.class::cast)
                .extracting(ModelCallStarted::segmentId, ModelCallStarted::kind, ModelCallStarted::attempt)
                .containsExactly(tuple("Book.md:0", CallKind.STRUCTURAL_REPAIR, 1));
        assertThat(model.requests()).hasSize(1);
    }

    // A judge call scores a whole chunk, so it names no single segment but lists every segment it judges.
    @Test
    void callAbout_judgeOfTwoSegments_announcesBothSegmentsAndNoSoleSegment() {
        calls.callAbout(CallKind.REVIEW, List.of("Book.md:0", "Book.md:1"), REQUEST);

        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .map(ModelCallStarted.class::cast)
                .extracting(ModelCallStarted::segmentId, ModelCallStarted::segmentIds)
                .containsExactly(tuple(null, List.of("Book.md:0", "Book.md:1")));
    }

    // The size of the request is what tells a long prompt from a stalled server; its text never leaves the job.
    @Test
    void call_cappedRequest_announcesItsSizeAndCap() {
        final ChatRequest capped = new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, "Rules."), new ChatMessage(ChatRole.USER, "Hello.")),
                null,
                null,
                null,
                8192,
                null,
                256);

        calls.call(CallKind.DRAFT, "Book.md:0", capped);

        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .map(event -> ((ModelCallStarted) event).request())
                .containsExactly(new RequestSummary(12, 16384, 256));
    }

    // A model whose window is 4096 must never be asked for the 8192 the prompt builders default to, nor for a reply
    // that fills the window.
    @Test
    void call_windowOf4096_sendsItAsTheContextAndLimitsTheCap() {
        final JobModelCalls small = new JobModelCalls(
                onSent -> new CancellableChatModel(model, control, onSent), events::add, new StepClock(), "uk", 4096);
        final ChatRequest asked =
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")), null, null, null, 8192, null, 3900);

        small.call(CallKind.DRAFT, "Book.md:0", asked);

        assertThat(model.requests())
                .singleElement()
                .satisfies(sent -> assertThat(sent)
                        .extracting(ChatRequest::contextWindow, ChatRequest::maxOutputTokens)
                        .containsExactly(4096, 2048));
    }

    // A call that states no window (a verification probe) is the provider's own business and stays as built.
    @Test
    void call_requestWithoutAWindow_isSentAsBuilt() {
        calls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(model.requests().getFirst().contextWindow()).isNull();
    }

    // The start and the finish bracket one call, timed by the job's clock from the moment the request leaves.
    @Test
    void call_answered_announcesTheFinishAfterTheStartWithTheClocksElapsedTime() {
        calls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events).hasSize(2);
        assertThat(events.getLast())
                .isInstanceOfSatisfying(
                        ModelCallFinished.class,
                        finished -> assertThat(finished)
                                .extracting(
                                        ModelCallFinished::kind,
                                        ModelCallFinished::segmentId,
                                        ModelCallFinished::elapsed)
                                .containsExactly(CallKind.DRAFT, "Book.md:0", Duration.ofSeconds(3)));
    }

    // A failed attempt has no reply to count; its end carries the code it failed with and no usage.
    @Test
    void call_answeredWithAnError_announcesTheFailedAttemptWithItsCode() {
        final ScriptedChatModel failing = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.")));
        final JobModelCalls failingCalls = new JobModelCalls(
                onSent -> new CancellableChatModel(failing, control, onSent), events::add, new StepClock(), "uk");

        failingCalls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events).hasSize(2);
        assertThat(events.getLast())
                .isInstanceOfSatisfying(
                        ModelCallFinished.class,
                        finished -> assertThat(finished)
                                .extracting(
                                        ModelCallFinished::failure,
                                        ModelCallFinished::usage,
                                        ModelCallFinished::elapsed)
                                .containsExactly(ErrorCode.unreachable, null, Duration.ofSeconds(3)));
    }

    // An announcement for a call that never went out would start a "waiting for the model" clock over nothing.
    @Test
    void call_pauseRequested_announcesNothingAndReachesNoModel() {
        control.pause();

        final Result<ChatResponse> result = calls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        final AppError error = result.error();
        assertThat(error).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.cancelled);
        assertThat(events).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    // The provider client retries a timed-out attempt itself; the second attempt is work the bar must count.
    @Test
    void send_secondAttemptAfterTimeout_addsOneResendStep() {
        final ChatModel retrying = new RetryingModel(true);
        final JobModelCalls stepped = new JobModelCalls(onSent -> retrying, events::add, new StepClock(), "uk")
                .withSteps(StepAnnouncer.over(events::add));

        stepped.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events)
                .filteredOn(event -> event instanceof StepsPlanned || event instanceof StepChanged)
                .containsExactly(
                        new StepsPlanned(StepKind.RESEND, 1, false),
                        new StepChanged(StepKind.RESEND, "resend-2", 0, StepState.STARTED, "Book.md:0", null),
                        new StepChanged(StepKind.RESEND, "resend-2", 0, StepState.ENDED, "Book.md:0", StepResult.DONE));
    }

    // A seam built without steps (setup, glossary, export) sends the same attempts and announces no step.
    @Test
    void send_seamWithoutSteps_announcesNoStepEvent() {
        final JobModelCalls plain =
                new JobModelCalls(onSent -> new RetryingModel(true), events::add, new StepClock(), "uk");

        plain.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events).noneMatch(event -> event instanceof StepsPlanned || event instanceof StepChanged);
    }

    // A resend that also fails ends its step FAILED, so the bar still reaches the total.
    @Test
    void send_secondAttemptFailsToo_endsTheResendStepFailed() {
        final ChatModel failing = new RetryingModel(false);
        final JobModelCalls stepped = new JobModelCalls(onSent -> failing, events::add, new StepClock(), "uk")
                .withSteps(StepAnnouncer.over(events::add));

        stepped.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events)
                .filteredOn(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filteredOn(change -> change.state() == StepState.ENDED)
                .extracting(StepChanged::result)
                .containsExactly(StepResult.FAILED);
    }

    /** Attempt 1 times out; attempt 2 answers {@code succeeds ? a reply : a timeout} like the provider client does. */
    private record RetryingModel(boolean succeeds) implements ChatModel {

        private static final Duration LIMIT = Duration.ofSeconds(60);

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            return chat(request, CallAttemptListener.NONE);
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
            final CallAttempt first = new CallAttempt(1, 2, LIMIT, null);
            attempts.started(first);
            attempts.failed(first, ErrorCode.timeout);
            final CallAttempt second = new CallAttempt(2, 2, LIMIT, null);
            attempts.started(second);
            if (succeeds) {
                return Result.ok(new ChatResponse("ONE.", FinishReason.STOP));
            }
            attempts.failed(second, ErrorCode.timeout);
            return Result.err(AppError.of(ErrorCode.timeout, "Timed out", "The second attempt timed out."));
        }
    }

    /** A clock three seconds later at every reading, so an elapsed time is exact. */
    private static final class StepClock extends Clock {

        private Instant next = Instant.parse("2026-09-29T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            final Instant now = next;
            next = next.plusSeconds(3);
            return now;
        }
    }
}
