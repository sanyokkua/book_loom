package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * A short piece of work whose answer needs a decision stays registered after its work has ended: it holds the answer's
 * rows and an offer, lets the machine sleep, and ends only on the offer's action or on Stop.
 */
class ActivityTrackerOfferTest extends FxTestBase {

    private final List<String> awake = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private ActivityTracker tracker;
    private ActivityTracker.Handle handle;

    @BeforeEach
    void beginSuggestion() {
        tracker = onFx(() -> new ActivityTracker(new StateMirror(), new KeepAwake() {
            @Override
            public void start() {
                awake.add("start");
            }

            @Override
            public void stop() {
                awake.add("stop");
            }
        }));
        handle = onFx(() -> tracker.begin(ActivityKind.SUGGEST_STYLE, () -> events.add("interrupt")));
    }

    private void offerTheAnswer() {
        onFx(() -> {
            handle.offer(new ResultOffer(
                    MessageKey.BRIEF_APPLY, true, () -> events.add("apply"), () -> events.add("discard")));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Activity activity() {
        return onFx(() -> tracker.running().getFirst());
    }

    private ResultOffer held() {
        return java.util.Objects.requireNonNull(activity().progress().outcome().offer());
    }

    private int running() {
        return onFx(() -> tracker.running().size());
    }

    // IF the answer ended the activity, THEN the dialog would close before the person could look at it.
    @Test
    void offer_workEnded_keepsTheActivityRegisteredWithTheOffer() {
        offerTheAnswer();

        assertThat(running()).isEqualTo(1);
        assertThat(held().label()).isEqualTo(MessageKey.BRIEF_APPLY);
        assertThat(held().enabled()).isTrue();
        assertThat(activity().cancellable()).isTrue();
    }

    // IF the held answer kept the machine awake, THEN a person reading it for an hour would block sleep for nothing.
    @Test
    void offer_workEnded_releasesTheKeepAwakeOnce() {
        offerTheAnswer();

        assertThat(awake).containsExactly("start", "stop");
    }

    @Test
    void offer_thenEnd_doesNotReleaseTheKeepAwakeAgain() {
        offerTheAnswer();

        onFx(() -> {
            handle.end();
            handle.end();
            return null;
        });

        assertThat(awake).containsExactly("start", "stop");
        assertThat(running()).isZero();
    }

    // IF a held answer did not block, THEN other model work could start under the open dialog.
    @Test
    void offer_held_stillBlocksOtherModelWork() {
        offerTheAnswer();

        assertThat(onFx(() -> tracker.conflictFor(ActivityKind.SUGGEST_NAME))).isPresent();
    }

    // IF Stop interrupted work that had already finished, THEN Esc on a held answer would report a cancelled call.
    @Test
    void stop_offerHeld_discardsTheAnswerAndDoesNotInterruptTheFinishedWork() {
        offerTheAnswer();
        final long id = activity().id();

        onFx(() -> {
            tracker.stop(id);
            return null;
        });

        assertThat(events).containsExactly("discard");
        assertThat(running()).isZero();
        assertThat(awake).containsExactly("start", "stop");
    }

    // IF the offer's action left the dialog open, THEN Apply would write the brief and the dialog would stay.
    @Test
    void apply_offerHeld_runsTheCallersApplyOnceAndEndsTheActivity() {
        offerTheAnswer();

        onFx(() -> {
            held().apply().run();
            return null;
        });

        assertThat(events).containsExactly("apply");
        assertThat(running()).isZero();
        assertThat(awake).containsExactly("start", "stop");
    }

    @Test
    void discard_offerHeld_runsTheCallersDiscardOnceAndEndsTheActivity() {
        offerTheAnswer();

        onFx(() -> {
            held().discard().run();
            return null;
        });

        assertThat(events).containsExactly("discard");
        assertThat(running()).isZero();
    }

    // The rows of "What it decides" travel on the activity next to the steps.
    @Test
    void decisions_set_areReadFromTheActivityOutcome() {
        final DecisionRow register = new DecisionRow(BriefField.REGISTER, "Formal", 3, 4, false, List.of());

        onFx(() -> {
            handle.decisions(List.of(register));
            return null;
        });

        assertThat(activity().progress().outcome().decisions()).containsExactly(register);
        assertThat(activity().progress().outcome().offer()).isNull();
    }

    // IF a failed or stopped answer left an offer, THEN Apply would write nothing the person was shown.
    @Test
    void end_noOfferMade_leavesNothingRegistered() {
        onFx(() -> {
            handle.end();
            return null;
        });

        assertThat(running()).isZero();
        assertThat(events).isEmpty();
    }
}
