package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationListener;
import ua.bookloom.api.llm.VerificationStage;

/**
 * What the running provider test has said so far, for the test card: each model request as it was sent, and each
 * stage as it ended. The verifier speaks on its own thread; everything here is changed on the FX thread, and only while
 * the test that spoke is still the current one. Emptied when a test starts and when it is made stale.
 */
@Slf4j
public final class TestProgress {

    private final Clock clock;
    private final ObservableList<SentCall> sent = FXCollections.observableArrayList();
    private final ObservableList<SentCall> sentView = FXCollections.unmodifiableObservableList(sent);
    private final ObservableList<StageChip> finished = FXCollections.observableArrayList();
    private final ObservableList<StageChip> finishedView = FXCollections.unmodifiableObservableList(finished);

    /**
     * Starts empty.
     *
     * @param clock where a request's send time and the card's live time are read
     */
    TestProgress(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The requests of the running test, oldest first, retries included.
     *
     * @return a read-only list; empty before the first request and after the test is made stale
     */
    public ObservableList<SentCall> sent() {
        return sentView;
    }

    /**
     * The stages of the running test that have ended, before its report arrives.
     *
     * @return a read-only list in the order the stages ended
     */
    public ObservableList<StageChip> finished() {
        return finishedView;
    }

    /**
     * The time now, by the settings' clock.
     *
     * @return the instant the card's live time is measured to
     */
    public Instant now() {
        return clock.instant();
    }

    void clear() {
        sent.clear();
        finished.clear();
    }

    /**
     * A listener for one test that hands what it hears to the FX thread.
     *
     * @param epoch the test's number
     * @param current the number of the current test, read on the FX thread
     */
    VerificationListener listener(final long epoch, final LongSupplier current) {
        Objects.requireNonNull(current, "current");
        return new VerificationListener() {
            @Override
            public void requestSent(
                    final VerificationStage stage,
                    final @Nullable AdvancedCheck check,
                    final VerificationExchange request) {
                Platform.runLater(() -> heard(epoch, current, new SentCall(stage, check, request, clock.instant())));
            }

            @Override
            public void stageFinished(final StageOutcome outcome) {
                Platform.runLater(() -> ended(epoch, current, outcome));
            }
        };
    }

    private void heard(final long epoch, final LongSupplier current, final SentCall call) {
        if (epoch != current.getAsLong()) {
            log.debug("request of stale test {} not shown", epoch);
            return;
        }
        log.debug("test {} sent a {} request, check {}", epoch, call.stage(), call.check());
        sent.add(call);
    }

    private void ended(final long epoch, final LongSupplier current, final StageOutcome outcome) {
        if (epoch != current.getAsLong()) {
            log.debug("stage end of stale test {} not shown", epoch);
            return;
        }
        log.debug("test {} finished stage {} with {}", epoch, outcome.stage(), outcome.status());
        finished.add(TestChips.of(outcome.stage(), outcome));
    }
}
