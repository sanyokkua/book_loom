package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.HiddenWork;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.ProgressDialogDefaults;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepEntry;
import ua.bookloom.ui.state.WorkVisibility;

/**
 * The work layer's one progress dialog, in the real window: the steps follow the work until a person chooses one,
 * Escape hides long work and cancels short work, long work continues in the background behind the title bar's chip and
 * comes back as it was, and the export's Cancel asks first, above the dialog. No second window ever opens.
 */
class ProgressDialogTest extends ShellTestBase {

    private static final long WITHIN_SECONDS = 3;

    private final List<String> cancelled = new CopyOnWriteArrayList<>();

    private ActivityTracker tracker() {
        return injector.getInstance(ActivityTracker.class);
    }

    private ActivityTracker.Handle begin(final ActivityKind kind) {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() -> handle.set(tracker().begin(kind, () -> cancelled.add(kind.name()))));
        return Objects.requireNonNull(handle.get(), "handle");
    }

    private void plan(final ActivityTracker.Handle handle, final int steps) {
        onFx(() -> handle.steps(new StepsPlanned(StepKind.REQUEST, steps, false)));
    }

    private void start(final ActivityTracker.Handle handle, final int index) {
        onFx(() -> handle.steps(new StepChanged(StepKind.REQUEST, "sample", index, StepState.STARTED, null, null)));
    }

    private void end(final ActivityTracker.Handle handle, final int index) {
        onFx(() -> handle.steps(
                new StepChanged(StepKind.REQUEST, "sample", index, StepState.ENDED, null, StepResult.DONE)));
    }

    // Steps 0 to count - 1 started and ended, in order.
    private void endSteps(final ActivityTracker.Handle handle, final int count) {
        IntStream.range(0, count).forEach(index -> {
            start(handle, index);
            end(handle, index);
        });
    }

    private boolean isDialogShown() {
        final Node host = scene.getRoot().lookup("#" + BusyHost.HOST_ID);
        final Node card = scene.getRoot().lookup("#busy-card");
        return host != null && host.isVisible() && host.getOpacity() > 0 && card != null && card.isVisible();
    }

    private void await(final BooleanSupplier condition) throws TimeoutException {
        WaitForAsyncUtils.waitFor(WITHIN_SECONDS, TimeUnit.SECONDS, condition::getAsBoolean);
        interact(() -> {});
    }

    private void awaitDialog() throws TimeoutException {
        await(this::isDialogShown);
    }

    @SuppressWarnings("unchecked")
    private ListView<StepEntry> steps() {
        return (ListView<StepEntry>) required("progress-steps");
    }

    private ToggleSwitch follow() {
        return (ToggleSwitch) required("progress-follow");
    }

    private int selectedIndex() {
        final StepEntry selected = steps().getSelectionModel().getSelectedItem();
        return selected == null ? -1 : selected.index();
    }

    private void escape() {
        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    }

    private boolean isRunning(final ActivityKind kind) {
        return tracker().running().stream().anyMatch(activity -> activity.kind() == kind);
    }

    // IF the code's own selection counted as a person's, THEN following would stop after the first step.
    @Test
    void codeSelection_whileFollowing_keepsFollowOn() throws TimeoutException {
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE);
        plan(brief, 6);
        start(brief, 0);
        awaitDialog();

        end(brief, 0);
        start(brief, 1);

        await(() -> selectedIndex() == 1);
        assertThat(follow().isSelected()).isTrue();
    }

    // IF a click on an earlier step left follow on, THEN the next step would snatch it away.
    @Test
    void click_onEarlierStep_turnsFollowOff() throws TimeoutException {
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE);
        plan(brief, 6);
        endSteps(brief, 1);
        start(brief, 1);
        awaitDialog();
        await(() -> selectedIndex() == 1);

        clickOn(required("progress-step-0"));
        end(brief, 1);
        start(brief, 2);
        WaitForAsyncUtils.sleep((long) (BusyHost.COALESCE.toMillis() * 3), TimeUnit.MILLISECONDS);
        interact(() -> {});

        assertThat(follow().isSelected()).isFalse();
        assertThat(selectedIndex()).isZero();
    }

    // IF Escape cancelled the names pass, THEN minutes of work would be lost to one key.
    @Test
    void escape_onNamesPass_hidesDialogAndWorkRuns() throws TimeoutException {
        begin(ActivityKind.NAMES_PASS);
        awaitDialog();

        escape();

        await(() -> !isDialogShown());
        assertThat(cancelled).isEmpty();
        assertThat(isRunning(ActivityKind.NAMES_PASS)).isTrue();
        assertThat(required("shell-activity").isVisible()).isTrue();
    }

    // IF Escape on the export's last check asked "Cancel the export?", THEN hiding would cost a second answer.
    @Test
    void escape_onExport_hidesWithoutAskingAQuestion() throws TimeoutException {
        begin(ActivityKind.EXPORT);
        awaitDialog();

        escape();

        await(() -> !isDialogShown());
        assertThat(scene.getRoot().lookup("#confirm-card")).isNull();
        assertThat(cancelled).isEmpty();
    }

    // IF Escape only hid the brief's suggestion, THEN short work would end out of sight.
    @Test
    void escape_onBrief_cancelsIt() throws TimeoutException {
        begin(ActivityKind.SUGGEST_STYLE);
        awaitDialog();

        escape();

        assertThat(cancelled).containsExactly("SUGGEST_STYLE");
    }

    // IF hiding named no screen, THEN Names & style could not turn read-only while the pass writes there.
    @Test
    void hide_namesPass_setsHiddenWorkToNamesStyle() throws TimeoutException {
        begin(ActivityKind.NAMES_PASS);
        awaitDialog();

        onFx(() -> ((Button) required("progress-background")).fire());

        await(() -> !isDialogShown());
        assertThat(injector.getInstance(HiddenWork.class).screen().get()).isEqualTo(ViewNames.NAMES_STYLE);
        assertThat(cancelled).isEmpty();
    }

    @Test
    void hide_namesPassAt53_chipReadsNamesPercentOpen() throws TimeoutException {
        final ActivityTracker.Handle names = begin(ActivityKind.NAMES_PASS);
        plan(names, 100);
        endSteps(names, 53);
        awaitDialog();

        onFx(() -> ((Button) required("progress-background")).fire());

        await(() -> !isDialogShown());
        assertThat(((Label) required("shell-activity-text")).getText()).isEqualTo("Preparing names · 53 %");
        final Hyperlink open = (Hyperlink) required("shell-activity-open");
        assertThat(open.isVisible()).isTrue();
        assertThat(open.getText()).isEqualTo("open");
        assertThat(TooltipProbe.tipText(open)).isNotBlank();
    }

    // IF the chip reopened a fresh dialog, THEN the person would lose the step they were reading.
    @Test
    void chipOpen_clicked_reopensSameDialogWithSelection() throws TimeoutException {
        final ActivityTracker.Handle names = begin(ActivityKind.NAMES_PASS);
        plan(names, 6);
        endSteps(names, 2);
        start(names, 2);
        awaitDialog();
        clickOn(required("progress-step-0"));
        onFx(() -> ((Button) required("progress-background")).fire());
        await(() -> !isDialogShown());

        onFx(() -> ((Hyperlink) required("shell-activity-open")).fire());

        awaitDialog();
        assertThat(selectedIndex()).isZero();
        assertThat(follow().isSelected()).isFalse();
    }

    // IF short work offered the background, THEN a brief suggestion could end with nobody looking.
    @Test
    void footer_brief_hasNoContinueInBackgroundAndReadsCancelWritesNothing() throws TimeoutException {
        begin(ActivityKind.SUGGEST_STYLE);
        awaitDialog();

        assertThat(scene.getRoot().lookup("#progress-background")).isNull();
        assertThat(((Label) required("progress-footnote")).getText()).isEqualTo("Cancel writes nothing.");
        assertThat(((Button) required("progress-cancel")).getText()).isEqualTo("Cancel");
    }

    // IF Cancel stopped the export at once, THEN one slip would throw away the whole last check.
    // IF the bar's tooltip did not give the steps, THEN a person could not relate the bar to the text beside it.
    @Test
    void bar_hovered_saysHowManyStepsAreDoneIncludingChecks() throws TimeoutException {
        final ActivityTracker.Handle names = begin(ActivityKind.NAMES_PASS);
        plan(names, 276);
        endSteps(names, 140);
        awaitDialog();

        assertThat(TooltipProbe.tipText(required("progress-bar"))).isEqualTo("140 of 276 steps, checks included");
    }

    // IF "How progress is counted" listed no kinds, THEN the breakdown the bar promises would be missing.
    @Test
    void howCounted_afterSomeSteps_listsEachKindWithItsDoneAndPlannedSteps() throws TimeoutException {
        final ActivityTracker.Handle names = begin(ActivityKind.NAMES_PASS);
        plan(names, 10);
        endSteps(names, 4);
        awaitDialog();

        final VBox kinds = (VBox) required("progress-how-kinds");

        assertThat(kinds.getChildren()).hasSize(1);
        assertThat(((Label) kinds.getChildren().getFirst()).getText()).startsWith("Requests: 4 of 10 steps");
    }

    @Test
    void exportCancel_pressed_asksCancelTheExportAboveDialog() throws TimeoutException {
        begin(ActivityKind.EXPORT);
        awaitDialog();

        onFx(() -> ((Button) required("progress-cancel")).fire());

        assertThat(textsUnder(required("confirm-card"))).contains("Cancel the export?", "No file is written.");
        assertThat(isDialogShown()).isTrue();
        assertThat(cancelled).isEmpty();
        onFx(() -> ((Button) required("confirm-yes")).fire());
        assertThat(cancelled).containsExactly("EXPORT");
    }

    // IF calls opened a window of their own, THEN it would get lost behind the main one.
    @Test
    void calls_arrive_openNoSecondWindow() throws TimeoutException {
        final ActivityTracker.Handle scan = begin(ActivityKind.GLOSSARY_SCAN);
        awaitDialog();

        onFx(() -> scan.calls(new LiveCalls(call(), null, java.util.Map.of(), null)));
        WaitForAsyncUtils.sleep((long) (BusyHost.COALESCE.toMillis() * 3), TimeUnit.MILLISECONDS);

        assertThat(ThemeTestSupport.onFx(() ->
                        Window.getWindows().stream().filter(Window::isShowing).count()))
                .isEqualTo(1L);
    }

    // IF the work layer did not say it is showing, THEN a dialog nobody asked for would open over the work.
    @Test
    void reveal_setsWorkVisibilityTrueAndTheEndSetsItFalse() throws TimeoutException {
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE);
        awaitDialog();
        final boolean whileShown = injector.getInstance(WorkVisibility.class).isVisible();

        onFx(brief::end);

        await(() -> !injector.getInstance(WorkVisibility.class).isVisible());
        assertThat(whileShown).isTrue();
    }

    // IF Simple showed the call kind and attempt, THEN most people would read machine words they never asked for.
    @Test
    void simple_byDefault_showsNoCallLine() throws TimeoutException {
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE);
        plan(brief, 2);
        start(brief, 0);
        onFx(() -> brief.calls(new LiveCalls(call(), null, java.util.Map.of(), null)));
        awaitDialog();

        assertThat(required("progress-call").isVisible()).isFalse();
    }

    // IF Detailed left out the call, THEN the person who asks for technical details would not see what was sent.
    @Test
    void detailed_chosenAsDefault_showsTheCallLine() throws TimeoutException {
        onFx(() -> injector.getInstance(ProgressDialogDefaults.class)
                .initialMode()
                .set(ProgressDialogModel.Mode.DETAILED));
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE);
        plan(brief, 2);
        start(brief, 0);
        onFx(() -> brief.calls(new LiveCalls(call(), null, java.util.Map.of(), null)));
        awaitDialog();

        await(() -> required("progress-call").isVisible());
        assertThat(((Label) required("progress-call")).getText())
                .isEqualTo("Call 42 · Translate · 4 segments · attempt 1 of 2");
    }

    private static CallSnapshot call() {
        final List<CallSegment> segments = IntStream.rangeClosed(1, 4)
                .mapToObj(n -> new CallSegment("s" + n, "p" + n, "Line " + n + "."))
                .toList();
        return CallSnapshot.waiting(
                42,
                CallKind.DRAFT,
                "draft-batch-json",
                null,
                segments,
                List.of(),
                LiveCallFixtures.STARTED,
                1,
                2,
                Duration.ofMinutes(2));
    }
}
