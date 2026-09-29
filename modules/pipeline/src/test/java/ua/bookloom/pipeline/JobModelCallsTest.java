package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

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
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
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
                .containsExactly(new ModelCallStarted("Book.md:0", CallKind.STRUCTURAL_REPAIR));
        assertThat(model.requests()).hasSize(1);
    }

    // A judge call scores a whole chunk, so it names no segment.
    @Test
    void call_judgeWithoutSegment_announcesJudgeWithNoSegmentId() {
        calls.call(CallKind.JUDGE, null, REQUEST);

        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .containsExactly(new ModelCallStarted(null, CallKind.JUDGE));
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

    // A failed call has no reply to count, so only its start is announced; the pause or flag that follows says why.
    @Test
    void call_answeredWithAnError_announcesNoFinish() {
        final ScriptedChatModel failing = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.")));
        final JobModelCalls failingCalls = new JobModelCalls(
                onSent -> new CancellableChatModel(failing, control, onSent), events::add, new StepClock(), "uk");

        failingCalls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        assertThat(events).containsExactly(new ModelCallStarted("Book.md:0", CallKind.DRAFT));
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
