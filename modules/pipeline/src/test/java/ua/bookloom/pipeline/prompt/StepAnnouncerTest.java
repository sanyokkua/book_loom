package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/** How the announcer numbers steps within a kind and which events it sends, with a plain list for a sink. */
class StepAnnouncerTest {

    private final List<JobEvent> events = new ArrayList<>();
    private final StepAnnouncer steps = StepAnnouncer.over(events::add);

    private static StepChanged started(final StepKind kind, final String label, final int index, final String item) {
        return new StepChanged(kind, label, index, StepState.STARTED, item, null);
    }

    private static StepChanged ended(
            final StepKind kind, final String label, final int index, final String item, final StepResult result) {
        return new StepChanged(kind, label, index, StepState.ENDED, item, result);
    }

    // IF a step retried after a pause took a new index, THEN the plan would count it twice and the bar would stall.
    @Test
    void started_sameKeyTwice_keepsOneIndex() {
        steps.started(StepKind.DRAFT, "draft", "s0", "s0");
        steps.started(StepKind.DRAFT, "draft", "s0", "s0");
        steps.started(StepKind.DRAFT, "draft", "s1", "s1");

        assertThat(events)
                .containsExactly(
                        started(StepKind.DRAFT, "draft", 0, "s0"),
                        started(StepKind.DRAFT, "draft", 0, "s0"),
                        started(StepKind.DRAFT, "draft", 1, "s1"));
    }

    // Indices are per kind: the first check is index 0 even after two drafts.
    @Test
    void started_otherKind_countsFromZeroAgain() {
        steps.started(StepKind.DRAFT, "draft", "s0", "s0");
        steps.started(StepKind.CHECK, "check-1", "check:s0:1", "s0");

        assertThat(events).last().isEqualTo(started(StepKind.CHECK, "check-1", 0, "s0"));
    }

    @Test
    void added_newKey_plansOneThenStarts() {
        steps.added(StepKind.REPAIR, "repair-round-1", "s3:r1", "s3");

        assertThat(events)
                .containsExactly(
                        new StepsPlanned(StepKind.REPAIR, 1, false),
                        started(StepKind.REPAIR, "repair-round-1", 0, "s3"));
    }

    // IF a reopened step planned itself again, THEN a paused repair round would count as two steps.
    @Test
    void added_reopenedKey_plansNothingMore() {
        steps.added(StepKind.REPAIR, "repair-round-1", "s3:r1", "s3");
        events.clear();

        steps.added(StepKind.REPAIR, "repair-round-1", "s3:r1", "s3");

        assertThat(events).containsExactly(started(StepKind.REPAIR, "repair-round-1", 0, "s3"));
    }

    @Test
    void ended_openKey_carriesTheLabelItemAndResultOfItsStart() {
        steps.started(StepKind.CHECK, "check-2", "check:s0:2", "s0");

        steps.ended(StepKind.CHECK, "check:s0:2", StepResult.DONE);

        assertThat(events).last().isEqualTo(ended(StepKind.CHECK, "check-2", 0, "s0", StepResult.DONE));
    }

    @Test
    void ended_unknownKey_emitsNothing() {
        steps.ended(StepKind.DRAFT, "never-started", StepResult.DONE);

        assertThat(events).isEmpty();
    }

    @Test
    void planned_zero_emitsNothing() {
        steps.planned(StepKind.CHECK, 0, false);

        assertThat(events).isEmpty();
    }

    @Test
    void planned_positive_emitsTheCountAndTheEstimateFlag() {
        steps.planned(StepKind.REVISION, 1, true);

        assertThat(events).containsExactly(new StepsPlanned(StepKind.REVISION, 1, true));
    }

    @Test
    void instant_newKey_startsAndEndsAtOnce() {
        steps.instant(StepKind.CHECK, "check-1", "check:s0:1", "s0", StepResult.NO_CALL);

        assertThat(events)
                .containsExactly(
                        started(StepKind.CHECK, "check-1", 0, "s0"),
                        ended(StepKind.CHECK, "check-1", 0, "s0", StepResult.NO_CALL));
    }

    @Test
    void endOpen_prefix_endsOnlyMatching() {
        steps.started(StepKind.CHECK, "check-1", "check:a:1", "a");
        steps.started(StepKind.CHECK, "check-2", "check:a:2", "a");
        steps.started(StepKind.CHECK, "check-1", "check:b:1", "b");
        events.clear();

        steps.endOpen(StepKind.CHECK, "check:a", StepResult.FAILED);

        assertThat(events)
                .containsExactly(
                        ended(StepKind.CHECK, "check-1", 0, "a", StepResult.FAILED),
                        ended(StepKind.CHECK, "check-2", 1, "a", StepResult.FAILED));
    }

    @Test
    void silent_anyCall_doesNotFail() {
        final StepAnnouncer silent = StepAnnouncer.silent();

        silent.planned(StepKind.DRAFT, 3, false);
        silent.added(StepKind.RESEND, "resend-2", "k", null);
        silent.endOpen(StepKind.RESEND, "k", StepResult.FAILED);

        assertThat(events).isEmpty();
    }
}
