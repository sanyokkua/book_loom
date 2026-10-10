package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.i18n.MessageKey;

/** Retrying a segment from the review panel, holding the run while it is in flight, and applying a proposal. */
class ReviewViewModelRetryTest extends ReviewViewModelTestBase {

    private static final String SEGMENT = "ch05.xhtml:11";

    private void selectLowScore(final RunState state) {
        chooseModel(MODEL);
        select(SEGMENT);
        setRunState(state);
    }

    private String retryCall(final String note, final boolean lower) {
        return "retry(" + projectId + ", " + SEGMENT + ", note=" + note + ", lowerTemperature=" + lower + ")";
    }

    private List<Raised> warnings() {
        return toasts.raised().stream()
                .filter(raised -> raised.severity().equals("warning"))
                .toList();
    }

    /** Runs each task on the calling thread until it is told to refuse, as a shut-down executor does. */
    private static final class RefusingExecutor extends AbstractExecutorService {

        private volatile boolean refusing;

        @Override
        public void execute(final Runnable command) {
            if (refusing) {
                throw new RejectedExecutionException("shut down");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            refusing = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            refusing = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return refusing;
        }

        @Override
        public boolean isTerminated() {
            return refusing;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return refusing;
        }
    }

    // IF a retry the executor refused kept its activity and its hold on the run, THEN the busy state and the disabled
    // Resume would stay until the app was closed.
    @Test
    void retry_executorRefusesTheCall_endsTheActivityAndReleasesTheRun() {
        final RefusingExecutor executor = new RefusingExecutor();
        buildReview(executor);
        selectLowScore(RunState.PAUSED);
        executor.shutdown();

        press(() -> review.retry(null, false));

        assertThat(onFx(() -> List.copyOf(activities.running()))).isEmpty();
        assertThat(onFx(() -> mirror.review().retryInFlight().get())).isFalse();
        assertThat(desk.calls()).doesNotContain(retryCall("null", false));
    }

    @Test
    void retry_noteAndLowerTemperature_reachTheDeskTogether() {
        buildReview();
        selectLowScore(RunState.PAUSED);

        press(() -> review.retry("keep it more formal", true));

        assertThat(desk.calls()).contains(retryCall("keep it more formal", true));
    }

    @Test
    void retry_plain_passesNoNoteAndNoLowerTemperature() {
        buildReview();
        selectLowScore(RunState.PAUSED);

        press(() -> review.retry(null, false));

        assertThat(desk.calls()).contains(retryCall("null", false));
    }

    // IF a retry were refused after a stop or on a finished run, THEN the person could not fix a segment then.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSED", "STOPPED", "COMPLETED", "IDLE"})
    void retry_runNotTranslating_reachesTheDesk(final RunState state) {
        buildReview();
        selectLowScore(state);

        press(() -> review.retry(null, false));

        assertThat(desk.calls()).contains(retryCall("null", false));
        assertThat(models.selections()).hasSize(1);
    }

    // IF a retry's calls were not published like a run's, THEN the person could not see what the retry sent the model.
    @Test
    void retry_callsTheDeskAnnounces_reachTheActivityAndStayAfterIt() {
        buildReview();
        selectLowScore(RunState.PAUSED);
        final CallSnapshot call = CallSnapshot.waiting(
                        1,
                        CallKind.DRAFT,
                        "draft",
                        null,
                        List.of(new CallSegment(SEGMENT, "ch5 · p11", "Source.")),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:10Z"),
                        1,
                        1,
                        null)
                .withSent(List.of(new ChatMessage(ChatRole.USER, "Translate this.")));
        desk.willAnnounceOnRetry(List.of(new CallSnapshotUpdated(call)));

        press(() -> review.retry(null, false));
        WaitForAsyncUtils.waitForFxEvents();

        final LiveCalls kept = onFx(() -> activities.lastCalls().get());
        assertThat(kept.current()).isNotNull();
        assertThat(kept.current().sent()).extracting(ChatMessage::content).containsExactly("Translate this.");
        assertThat(onFx(() -> activities.running())).isEmpty();
    }

    // IF Resume stayed live during a retry, THEN the run and the retry would race for the model.
    @Test
    void retry_inFlight_holdsResumeAndTheReviewActionsUntilTheDeskAnswers() {
        buildReview(queued);
        buildViewModel();
        selectLowScoreQueued();

        press(() -> review.retry(null, false));

        assertThat(onFx(() -> mirror.review().retryInFlight().get())).isTrue();
        assertThat(controls().resume()).isEqualTo(ControlState.DISABLED);
        assertThat(onFx(() -> review.actionsAvailable().get())).isFalse();
        assertThat(onFx(() -> review.acceptAvailable().get())).isFalse();

        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.review().retryInFlight().get())).isFalse();
        assertThat(controls().resume()).isEqualTo(ControlState.ENABLED);
        assertThat(onFx(() -> review.actionsAvailable().get())).isTrue();
    }

    private void selectLowScoreQueued() {
        chooseModel(MODEL);
        select(SEGMENT);
        queued.runAll();
        setRunState(RunState.PAUSED);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a retry could start while a run translates, THEN two callers would drive the one model at once.
    @Test
    void retry_whileRunning_raisesOneBusyWarningAndCreatesNoModel() {
        buildReview();
        selectLowScore(RunState.RUNNING);

        press(() -> review.retry(null, false));

        assertThat(warnings()).extracting(Raised::key).containsExactly(MessageKey.REVIEW_BUSY);
        assertThat(models.selections()).isEmpty();
        assertThat(desk.calls()).noneMatch(call -> call.startsWith("retry("));
    }

    @Test
    void retry_deskAnswersBusy_raisesOneWarningAndReleasesTheHold() {
        buildReview();
        selectLowScore(RunState.PAUSED);
        desk.willAnswer(Result.err(AppError.of(ErrorCode.busy, "Busy", "A run is translating.")));

        press(() -> review.retry(null, false));

        assertThat(warnings()).extracting(Raised::key).containsExactly(MessageKey.REVIEW_BUSY);
        assertThat(onFx(() -> mirror.review().retryInFlight().get())).isFalse();
    }

    @Test
    void retry_noModelChosen_showsTheMessageInPlaceAndCallsNoDesk() {
        buildReview();
        select(SEGMENT);

        press(() -> review.retry(null, false));

        assertThat(onFx(() -> review.problem().get()))
                .isEqualTo("Choose a model in the provider settings before retrying.");
        assertThat(desk.calls()).noneMatch(call -> call.startsWith("retry("));
        assertThat(models.selections()).isEmpty();
    }

    // IF the proposal were applied on sight, THEN the person's own edit would be replaced unseen.
    @Test
    void acceptProposal_notPressed_changesNothingAndPressedCallsTheDesk() {
        buildReview();
        desk.willAnswerSegment(withProposal(lowScore(), "Він пішов."));
        select(SEGMENT);
        final String call = "acceptProposal(" + projectId + ", " + SEGMENT + ")";

        assertThat(desk.calls()).doesNotContain(call);
        assertThat(onFx(() -> review.selected().get().proposal())).isEqualTo("Він пішов.");

        press(review::acceptProposal);

        assertThat(desk.calls()).contains(call);
    }

    private static SegmentView withProposal(final SegmentView base, final String proposal) {
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                "Вона пішла.",
                null,
                null,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                proposal);
    }
}
