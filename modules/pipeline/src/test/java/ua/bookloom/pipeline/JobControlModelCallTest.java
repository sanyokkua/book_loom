package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.PauseReason;

/** The interrupt Pause and Stop send to a model call is sent only inside one, and never left behind. */
class JobControlModelCallTest {

    private final JobControl control = new JobControl();

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
        Thread.interrupted();
    }

    @Test
    void enterModelCall_noRequest_isAllowed() {
        assertThat(control.enterModelCall()).isTrue();
    }

    @Test
    void enterModelCall_pauseRequested_isRefused() {
        control.pause();

        assertThat(control.enterModelCall()).isFalse();
    }

    @Test
    void enterModelCall_cancelRequested_isRefused() {
        control.cancel();

        assertThat(control.enterModelCall()).isFalse();
    }

    // Interrupting only while a call is in progress is what keeps the export and the pause wait interrupt-free.
    @Test
    void pause_outsideModelCall_interruptsNothing() {
        control.pause();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void pause_afterTheCallEnded_interruptsNothing() {
        control.enterModelCall();
        control.exitModelCall();

        control.pause();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void pause_insideModelCall_interruptsTheCallingThread() {
        assertThat(interruptedByRequest(JobControl::pause)).isTrue();
    }

    @Test
    void cancel_insideModelCall_interruptsTheCallingThread() {
        assertThat(interruptedByRequest(JobControl::cancel)).isTrue();
    }

    // A stale interrupt left after the call would abort the next blocking operation, such as the pause wait.
    @Test
    void exitModelCall_afterInterrupt_clearsTheInterrupt() {
        control.enterModelCall();
        control.pause();

        control.exitModelCall();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void abortedCallBoundary_pauseStillRequested_pausesAsRequested() {
        control.enterModelCall();
        control.pause();
        control.exitModelCall();

        final BoundaryDecision decision = control.abortedCallBoundary();

        assertThat(decision.pauseReason()).isEqualTo(PauseReason.REQUESTED);
    }

    // Resume pressed before the job thread noticed the abort must retry the segment, not end the run.
    @Test
    void abortedCallBoundary_pauseWithdrawnByResume_continues() {
        control.enterModelCall();
        control.pause();
        control.exitModelCall();
        control.resume();

        final BoundaryDecision decision = control.abortedCallBoundary();

        assertThat(decision.cancelled()).isFalse();
        assertThat(decision.pauseReason()).isNull();
    }

    @Test
    void abortedCallBoundary_cancelRequested_cancels() {
        control.enterModelCall();
        control.cancel();
        control.exitModelCall();

        assertThat(control.abortedCallBoundary().cancelled()).isTrue();
    }

    // An interrupt from outside the control, such as an executor shutdown, keeps ending the run as it always did.
    @Test
    void abortedCallBoundary_noRequestAtAll_cancels() {
        assertThat(control.abortedCallBoundary().cancelled()).isTrue();
    }

    private boolean interruptedByRequest(final Consumer<JobControl> request) {
        final ExecutorService workers = TranslationJobTestSupport.executor();
        final CountDownLatch inCall = new CountDownLatch(1);
        final Future<Boolean> sawInterrupt = workers.submit(() -> waitInsideCall(inCall));

        TranslationJobTestSupport.await(inCall);
        request.accept(control);
        return TranslationJobTestSupport.await(sawInterrupt);
    }

    private boolean waitInsideCall(final CountDownLatch inCall) {
        control.enterModelCall();
        inCall.countDown();
        try {
            Thread.sleep(Duration.ofSeconds(10));
            return false;
        } catch (InterruptedException expected) {
            return true;
        } finally {
            control.exitModelCall();
        }
    }
}
