package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Which model activities may run beside each other, and how a registration is counted, stopped and ended. */
class ActivityTrackerTest {

    private final ActivityTracker tracker = new ActivityTracker(new StateMirror());

    // IF two activities that ask the model could run together, THEN they would queue behind each other in the gate and
    // the slower one would look stalled; the light provider checks and the model listing may run during a run.
    @ParameterizedTest
    @CsvSource({
        "TRANSLATION,GLOSSARY_SCAN,true",
        "TRANSLATION,GLOSSARY_REVIEW,true",
        "TRANSLATION,REVIEW_RETRY,true",
        "TRANSLATION,PROVIDER_INFERENCE_TEST,true",
        "TRANSLATION,EXPORT,true",
        "GLOSSARY_SCAN,GLOSSARY_REVIEW,true",
        "GLOSSARY_SCAN,EXPORT,true",
        "REVIEW_RETRY,PROVIDER_INFERENCE_TEST,true",
        "TRANSLATION,PROVIDER_CHECK,false",
        "TRANSLATION,MODEL_LISTING,false",
        "GLOSSARY_SCAN,MODEL_LISTING,false",
        "EXPORT,PROVIDER_CHECK,false",
        "PROVIDER_CHECK,MODEL_LISTING,false"
    })
    void conflictsWith_twoKinds_isTheSameEitherWayRound(
            final ActivityKind one, final ActivityKind other, final boolean expected) {
        assertThat(one.conflictsWith(other)).isEqualTo(expected);
        assertThat(other.conflictsWith(one)).isEqualTo(expected);
    }

    // IF a kind could run twice at once, THEN two scans or two listings would race to write the same result.
    @ParameterizedTest
    @EnumSource(ActivityKind.class)
    void conflictsWith_itsOwnKind_isTrue(final ActivityKind kind) {
        assertThat(kind.conflictsWith(kind)).isTrue();
    }

    // IF the check ignored what runs, THEN a start would be offered beside a scan.
    @Test
    void conflictFor_aScanRunning_namesTheScanForATranslationButNotForAListing() {
        tracker.begin(ActivityKind.GLOSSARY_SCAN, null);

        assertThat(tracker.conflictFor(ActivityKind.TRANSLATION)).contains(ActivityKind.GLOSSARY_SCAN);
        assertThat(tracker.conflictFor(ActivityKind.MODEL_LISTING)).isEmpty();
        assertThat(tracker.blocker(ActivityKind.EXPORT).get()).isEqualTo(ActivityKind.GLOSSARY_SCAN);
    }

    // IF ending twice removed something else or threw, THEN a work that ends both by its answer and by a stop would
    // break the registry.
    @Test
    void end_calledTwice_removesTheActivityOnceAndLeavesOthers() {
        final ActivityTracker.Handle scan = tracker.begin(ActivityKind.GLOSSARY_SCAN, null);
        tracker.begin(ActivityKind.MODEL_LISTING, null);

        scan.end();
        scan.end();

        assertThat(tracker.running()).extracting(Activity::kind).containsExactly(ActivityKind.MODEL_LISTING);
        assertThat(tracker.blocker(ActivityKind.TRANSLATION).get()).isNull();
    }

    // IF the title bar could not count the requests, THEN a long scan would look stuck.
    @Test
    void requests_counted_replaceTheShownCountUntilEnded() {
        final ActivityTracker.Handle scan = tracker.begin(ActivityKind.GLOSSARY_SCAN, () -> {});

        scan.requests(2);
        scan.end();
        scan.requests(3);

        assertThat(tracker.running()).isEmpty();
    }

    // IF Stop reached an activity that cannot stop, or missed one that can, THEN the chip would lie about stopping.
    @Test
    void stopWhere_matchingKinds_runsOnlyTheirCancels() {
        final List<String> stopped = new ArrayList<>();
        tracker.begin(ActivityKind.GLOSSARY_SCAN, () -> stopped.add("scan"));
        tracker.begin(ActivityKind.EXPORT, null);
        tracker.begin(ActivityKind.MODEL_LISTING, () -> stopped.add("listing"));

        tracker.stopWhere(ActivityKind::isLeaveSensitive);

        assertThat(stopped).containsExactly("scan");
    }

    // IF the count did not reach the shown entry, THEN the chip would keep saying zero requests.
    @Test
    void requests_whileRunning_areShownOnTheEntry() {
        final ActivityTracker.Handle review = tracker.begin(ActivityKind.GLOSSARY_REVIEW, () -> {});

        review.requests(4);

        assertThat(tracker.running())
                .extracting(Activity::id, Activity::requests)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(review.id(), 4));
    }
}
