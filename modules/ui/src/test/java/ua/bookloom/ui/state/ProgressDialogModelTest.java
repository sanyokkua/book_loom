package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a progress dialog holds: its steps, the selection that follows the running step until a person chooses
 * another, its figures, its view and the calls each step made.
 */
class ProgressDialogModelTest {

    private static final Instant AT = Instant.parse("2026-03-01T09:00:00Z");

    private StepLog log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, 6, false));
    private @Nullable CallSnapshot current;

    private ProgressDialogModel model(final ProgressDialogModel.Mode mode) {
        return new ProgressDialogModel(activity(), new LedgerFigures(), mode, () -> AT);
    }

    private Activity activity() {
        final LiveCalls calls = new LiveCalls(current, null, Map.of(), null);
        return new Activity(
                7,
                ActivityKind.SUGGEST_STYLE,
                0,
                true,
                MessageKey.ACTIVITY_SUGGEST_STYLE,
                AT,
                new Activity.Progress(null, null, List.of(), null, calls, log),
                false,
                false);
    }

    private void start(final int index) {
        log = log.apply(new StepChanged(StepKind.REQUEST, "sample", index, StepState.STARTED, null, null));
    }

    private void end(final int index) {
        log = log.apply(new StepChanged(StepKind.REQUEST, "sample", index, StepState.ENDED, null, StepResult.DONE));
    }

    // Steps 1 to 4 ended and step 5 (index 4) running.
    private void runToStepFive() {
        IntStream.range(0, 4).forEach(index -> {
            start(index);
            end(index);
        });
        start(4);
    }

    private static CallSnapshot call(final long id) {
        return CallSnapshot.waiting(
                id,
                CallKind.DRAFT,
                "draft",
                null,
                List.of(new CallSegment("s" + id, "p" + id, "A line of text.")),
                List.of(new PromptSection("styleSheet", "Style:", PromptSection.Origin.SYSTEM, List.of("Plain."))),
                AT,
                1,
                2,
                Duration.ofMinutes(2));
    }

    private static StepEntry row(final ProgressDialogModel model, final int index) {
        return model.rows().stream()
                .filter(row -> row.index() == index)
                .findFirst()
                .orElseThrow();
    }

    // Step n (index n - 1) runs with call n, for every n from 1 to count; each step ends before the next starts.
    private void callsOneStepEach(final ProgressDialogModel model, final int count) {
        log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, count, false));
        IntStream.range(0, count).forEach(index -> {
            start(index);
            current = call(index + 1L);
            model.accept(activity());
            end(index);
        });
        model.accept(activity());
    }

    // IF following did not move the selection, THEN the person would watch a step that ended long ago.
    @Test
    void selectRunning_followOn_selectsStepFive() {
        runToStepFive();
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);

        model.accept(activity());

        assertThat(model.selected().get()).extracting(StepEntry::index).isEqualTo(4);
        assertThat(model.follow().get()).isTrue();
    }

    // IF a person's choice left follow on, THEN the next step would snatch the step being read.
    @Test
    void choose_stepTwoWhileFiveRuns_turnsFollowOff() {
        runToStepFive();
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);

        model.choose(row(model, 1));

        assertThat(model.follow().get()).isFalse();
        assertThat(model.selected().get()).extracting(StepEntry::index).isEqualTo(1);
    }

    @Test
    void stepSixStarts_followOff_keepsStepTwo() {
        runToStepFive();
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);
        model.choose(row(model, 1));
        end(4);
        start(5);

        model.accept(activity());

        assertThat(model.selected().get()).extracting(StepEntry::index).isEqualTo(1);
        assertThat(model.follow().get()).isFalse();
    }

    // IF turning follow on again waited for the next step, THEN the switch would look broken.
    @Test
    void follow_turnedOn_selectsRunningStep() {
        runToStepFive();
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);
        model.choose(row(model, 1));

        model.follow().set(true);

        assertThat(model.selected().get()).extracting(StepEntry::index).isEqualTo(4);
    }

    // IF a step not yet announced were missing, THEN the list could not say step 6 has not started.
    @Test
    void rows_fiveOfSixAnnounced_endWithAPendingStep() {
        runToStepFive();
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);

        assertThat(model.rows()).hasSize(6);
        assertThat(model.rows().getLast().isPending()).isTrue();
    }

    @Test
    void figures_26of49WithOneMinuteLeft_reads51PercentLine() {
        final ProgressFigures fake = (steps, now) -> new ProgressFigures.Figures(51, 26, 49, Duration.ofMinutes(1));
        final ProgressDialogModel model =
                new ProgressDialogModel(activity(), fake, ProgressDialogModel.Mode.SIMPLE, () -> AT);
        final Messages messages = new Messages((LocaleProvider) () -> Locale.ENGLISH);

        assertThat(model.figures().get().line(messages)).isEqualTo("51 % · 26 of about 49 steps · Left about 1 min");
    }

    // IF the bar's percentage fell when work was added, THEN the dialog would contradict the title bar.
    @Test
    void figures_morePlanned_percentNeverFalls() {
        IntStream.range(0, 3).forEach(index -> {
            start(index);
            end(index);
        });
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);
        log = log.apply(new StepsPlanned(StepKind.REPAIR, 6, false));

        model.accept(activity());

        assertThat(model.figures().get().percent()).isEqualTo(50);
        assertThat(model.figures().get().total()).isEqualTo(12);
    }

    // IF the dialog ignored the stored choice, THEN "Show technical details" would not open it in Detailed.
    @ParameterizedTest
    @EnumSource(ProgressDialogModel.Mode.class)
    void mode_initial_isTheOneGiven(final ProgressDialogModel.Mode mode) {
        assertThat(model(mode).mode().get()).isEqualTo(mode);
    }

    // IF a finished step lost its call, THEN the person could not read what step 3 sent once step 4 runs.
    @Test
    void callOf_finishedStep_keepsItsCall() {
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);

        callsOneStepEach(model, 2);

        assertThat(model.callOf(row(model, 0))).extracting(CallSnapshot::callId).isEqualTo(1L);
        assertThat(model.callOf(row(model, 1))).extracting(CallSnapshot::callId).isEqualTo(2L);
    }

    // IF every prompt were kept, THEN Max's thousands of consistency calls would fill the memory.
    @Test
    void callOf_51stOlderCall_hasNoPrompt() {
        final ProgressDialogModel model = model(ProgressDialogModel.Mode.SIMPLE);

        callsOneStepEach(model, 51);

        assertThat(model.callOf(row(model, 0)))
                .extracting(CallSnapshot::sections, InstanceOfAssertFactories.LIST)
                .isEmpty();
        assertThat(model.isPromptDropped(row(model, 0))).isTrue();
        assertThat(model.callOf(row(model, 50)))
                .extracting(CallSnapshot::sections, InstanceOfAssertFactories.LIST)
                .hasSize(1);
        assertThat(model.isPromptDropped(row(model, 50))).isFalse();
    }
}
