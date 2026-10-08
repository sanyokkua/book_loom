package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.i18n.MessageKey;

/** What the busy card reads off an activity: whether it blocks, its title, progress, details, elapsed base and ETA. */
class OperationStateTest {

    private final MutableClock clock = new MutableClock();
    private final ActivityTracker tracker = new ActivityTracker(new StateMirror(), new KeepAwake.Off(), clock);

    // IF a kind that asks the model did not block, THEN the person could change the book under a running scan.
    @ParameterizedTest
    @CsvSource({
        "IMPORT,true",
        "RUN_PREPARATION,true",
        "EXPORT,true",
        "GLOSSARY_SCAN,true",
        "GLOSSARY_REVIEW,true",
        "SUGGEST_STYLE,true",
        "SUGGEST_NAME,true",
        "PROVIDER_INFERENCE_TEST,true",
        "REVIEW_RETRY,true",
        "TRANSLATION,false",
        "MODEL_LISTING,false",
        "PROVIDER_CHECK,false"
    })
    void isBlocking_eachKind_isAsTheBusyRuleSays(final ActivityKind kind, final boolean expected) {
        assertThat(kind.isBlocking()).isEqualTo(expected);
    }

    // IF the shared observable lagged the list, THEN the footer buttons would stay enabled behind the busy card.
    @Test
    void blocking_blockingActivityBeginsAndEnds_followsIt() {
        assertThat(tracker.blocking().get()).isFalse();

        final ActivityTracker.Handle listing = tracker.begin(ActivityKind.MODEL_LISTING, null);
        assertThat(tracker.blocking().get()).isFalse();
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        assertThat(tracker.blocking().get()).isTrue();
        assertThat(tracker.blockingActivity().get().kind()).isEqualTo(ActivityKind.EXPORT);
        export.end();
        listing.end();

        assertThat(tracker.blocking().get()).isFalse();
        assertThat(tracker.blockingActivity().get()).isNull();
    }

    // IF the start were not taken from the injected clock, THEN the elapsed time could not be tested or trusted.
    @Test
    void begin_anyKind_recordsStartFromTheClockAndTitleFromTheKind() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);

        final Activity shown = tracker.running().getFirst();

        assertThat(shown.id()).isEqualTo(export.id());
        assertThat(shown.startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(shown.title()).isEqualTo(MessageKey.ACTIVITY_EXPORT);
        assertThat(shown.fraction()).isNull();
        assertThat(shown.eta()).isNull();
        assertThat(shown.details()).isEmpty();
    }

    // IF a counted step showed no fraction, THEN a bar with a known total would still spin.
    @Test
    void progress_unitsDone_giveFractionAndEtaFromTheAverageSoFar() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        clock.advance(Duration.ofSeconds(20));

        export.progress(2, 8, "Checking against neighbours");

        final Activity shown = tracker.running().getFirst();
        assertThat(shown.fraction()).isEqualTo(0.25);
        assertThat(shown.eta()).isEqualTo(Duration.ofSeconds(60));
        assertThat(shown.stepText()).isEqualTo("Checking against neighbours");
    }

    // IF the time left were read from the activity's start, THEN after a ten-minute retry step the next step at 1 of 50
    // would promise hours; each counted step is timed from its own start.
    @Test
    void progress_secondStepAfterALongFirstOne_takesItsEtaFromItsOwnStart() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        export.progress(0, 10, "Drafting doubted segments again");
        clock.advance(Duration.ofMinutes(10));
        export.progress(10, 10, "Drafting doubted segments again");
        export.progress(0, 50, "Checking against neighbours");
        clock.advance(Duration.ofSeconds(6));

        export.progress(1, 50, "Checking against neighbours");

        assertThat(tracker.running().getFirst().eta()).isEqualTo(Duration.ofSeconds(294));
    }

    // IF a step that announced no start were timed from the moment its text first appeared, THEN its first ETA would
    // be zero; a new step's text alone starts its clock too.
    @Test
    void progress_newStepTextWithoutAZeroAnnouncement_isTimedFromWhenItsTextAppeared() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        clock.advance(Duration.ofSeconds(100));
        export.progress(4, 4, "Drafting doubted segments again");
        clock.advance(Duration.ofSeconds(10));
        export.progress(1, 3, "Checking against neighbours");
        clock.advance(Duration.ofSeconds(10));

        export.progress(2, 3, "Checking against neighbours");

        assertThat(tracker.running().getFirst().eta()).isEqualTo(Duration.ofSeconds(10));
    }

    // IF zero finished units gave an ETA, THEN it would divide by zero or promise nothing real.
    @Test
    void progress_nothingDoneYet_hasAFractionButNoEta() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        clock.advance(Duration.ofSeconds(5));

        export.progress(0, 4, "Starting");

        final Activity shown = tracker.running().getFirst();
        assertThat(shown.fraction()).isZero();
        assertThat(shown.eta()).isNull();
    }

    // IF the step's progress stayed after the step changed to an uncounted one, THEN the bar would show a stale value.
    @Test
    void indeterminate_afterProgress_clearsFractionAndEta() {
        final ActivityTracker.Handle export = tracker.begin(ActivityKind.EXPORT, null);
        clock.advance(Duration.ofSeconds(10));
        export.progress(1, 2, "A");

        export.indeterminate("Writing");

        final Activity shown = tracker.running().getFirst();
        assertThat(shown.fraction()).isNull();
        assertThat(shown.eta()).isNull();
        assertThat(shown.stepText()).isEqualTo("Writing");
    }

    @Test
    void details_set_replaceThePreviousLinesInOrder() {
        final ActivityTracker.Handle scan = tracker.begin(ActivityKind.GLOSSARY_SCAN, null);

        scan.details(List.of(new Activity.Detail("Requests", "3"), new Activity.Detail("Model", "gemma")));
        scan.details(List.of(new Activity.Detail("Requests", "4")));

        assertThat(tracker.running().getFirst().details()).containsExactly(new Activity.Detail("Requests", "4"));
    }

    // IF a press of Cancel did not mark the entry, THEN the card could not say "Cancelling" or stop a second press.
    @Test
    void stop_cancellableActivity_marksItCancellingOnceAndRunsItsCancel() {
        final int[] runs = {0};
        final ActivityTracker.Handle scan = tracker.begin(ActivityKind.GLOSSARY_SCAN, () -> runs[0]++);

        tracker.stop(scan.id());
        tracker.stop(scan.id());

        assertThat(tracker.running().getFirst().cancelling()).isTrue();
        assertThat(runs[0]).isEqualTo(1);
    }
}
