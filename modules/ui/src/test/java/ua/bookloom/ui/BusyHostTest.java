package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.WorkVisibility;

/**
 * The work layer: it waits before showing the progress dialog, describes the work from the tracker, stops it on
 * Cancel, keeps every key from the window behind, and leaves the navigation and toolbar disabled for as long as short
 * blocking work runs.
 */
class BusyHostTest extends ShellTestBase {

    private static final long APPEARS_WITHIN_SECONDS = 3;

    private final List<String> cancelled = new CopyOnWriteArrayList<>();

    private ActivityTracker tracker() {
        return injector.getInstance(ActivityTracker.class);
    }

    private ActivityTracker.Handle begin(final ActivityKind kind, final boolean cancellable) {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() -> handle.set(tracker().begin(kind, cancellable ? () -> cancelled.add(kind.name()) : null)));
        return Objects.requireNonNull(handle.get(), "handle");
    }

    private boolean isCardShown() {
        final Node host = scene.getRoot().lookup("#" + BusyHost.HOST_ID);
        return host != null && host.isVisible() && host.getOpacity() > 0;
    }

    // The card turns visible a few statements before it takes the keyboard focus, inside the same FX-thread step; this
    // check polls from the test thread, so it can see the card between the two. Settling the FX thread after it lets
    // every check that follows read the finished reveal.
    private void awaitCard() throws TimeoutException {
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, this::isCardShown);
        interact(() -> {});
    }

    private String text(final String id) {
        return ((Label) required(id)).getText();
    }

    // IF the card showed at once, THEN every quick import or name suggestion would flash a dialog.
    @Test
    void card_blockingWorkJustStarted_isNotShownYetButShowsAfterTheDelay() throws TimeoutException {
        begin(ActivityKind.EXPORT, true);

        assertThat(isCardShown()).isFalse();
        awaitCard();

        assertThat(text("progress-title")).isEqualTo("Export");
    }

    // IF the delay were not cancelled by the end of the work, THEN a card would appear for work that already finished.
    @Test
    void card_workEndsBeforeTheDelay_neverShows() throws InterruptedException {
        final ActivityTracker.Handle quick = begin(ActivityKind.IMPORT, false);
        onFx(quick::end);

        Thread.sleep((long) BusyHost.DELAY.toMillis() + 250);

        assertThat(isCardShown()).isFalse();
    }

    // IF work the window does not wait for blocked it, THEN a model listing would grey out the whole window.
    @Test
    void card_nonBlockingWork_neverShowsAndLeavesTheNavigationEnabled() throws InterruptedException {
        begin(ActivityKind.MODEL_LISTING, true);
        begin(ActivityKind.TRANSLATION, false);

        Thread.sleep((long) BusyHost.DELAY.toMillis() + 250);

        assertThat(isCardShown()).isFalse();
        assertThat(required("shell-nav").isDisabled()).isFalse();
    }

    // IF the dialog did not read the tracker, THEN the person would see a spinner with nothing said about the work.
    @Test
    void card_workWithProgressAndDetails_showsStepFractionAndDetails() throws TimeoutException {
        final ActivityTracker.Handle export = begin(ActivityKind.EXPORT, true);
        onFx(() -> {
            export.progress(1, 4, "Checking paragraphs");
            export.details(List.of(new Activity.Detail("Model", "gemma"), new Activity.Detail("File", "b.epub")));
        });
        awaitCard();

        assertThat(text("progress-purpose")).isEqualTo("Checking paragraphs");
        assertThat(((ProgressBar) required("progress-bar")).getProgress()).isEqualTo(0.25);
        assertThat(text("progress-details")).isEqualTo("Model gemma · File b.epub");
    }

    // IF the figures line did not count the steps, THEN the person could not tell how much is left.
    @Test
    void figures_twoOfFourStepsEnded_readFiftyPercent() throws TimeoutException {
        final ActivityTracker.Handle brief = begin(ActivityKind.SUGGEST_STYLE, true);
        onFx(() -> {
            brief.steps(new StepsPlanned(StepKind.REQUEST, 4, false));
            brief.steps(new StepChanged(StepKind.REQUEST, "a", 0, StepState.ENDED, null, StepResult.DONE));
            brief.steps(new StepChanged(StepKind.REQUEST, "b", 1, StepState.ENDED, null, StepResult.DONE));
        });
        awaitCard();

        assertThat(text("progress-figures")).isEqualTo("50 % · 2 of about 4 steps");
        assertThat(((ProgressBar) required("progress-bar")).getProgress()).isEqualTo(0.5);
    }

    // IF work with no known fraction drew a determinate bar, THEN it would sit at zero and look stuck.
    @Test
    void card_workWithoutAFraction_isIndeterminate() throws TimeoutException {
        begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();

        assertThat(((ProgressBar) required("progress-bar")).getProgress()).isLessThan(0);
    }

    // IF Cancel did not reach the activity, THEN the card would be a trap; once pressed it must not be pressable again
    // and the step must say what is happening.
    @Test
    void cancel_pressed_stopsTheActivityOnceAndSaysCancelling() throws TimeoutException {
        begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();
        final Button cancel = (Button) required("progress-cancel");

        onFx(cancel::fire);
        onFx(cancel::fire);
        // The dialog redraws from the tracker at most every COALESCE.
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, cancel::isDisabled);
        interact(() -> {});

        assertThat(cancelled).containsExactly("GLOSSARY_SCAN");
        assertThat(cancel.isDisabled()).isTrue();
        assertThat(text("progress-purpose")).isEqualTo("Cancelling...");
        assertThat(TooltipProbe.tipText(cancel)).isNotBlank();
    }

    // IF a card offered Cancel for work that cannot be stopped, THEN pressing it would do nothing.
    @Test
    void card_workThatCannotBeStopped_hasNoCancelButtonAndSaysSo() throws TimeoutException {
        begin(ActivityKind.IMPORT, false);
        awaitCard();

        assertThat(required("progress-cancel").isVisible()).isFalse();
        assertThat(text("progress-purpose")).isEqualTo("This work cannot be stopped; it ends by itself.");
    }

    // IF the card went when the work ended only after a stop, THEN a finished export would leave the window locked.
    @Test
    void card_workEnds_fadesOutAndFreesTheWindow() throws TimeoutException {
        final ActivityTracker.Handle scan = begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();

        onFx(scan::end);
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, () -> !isCardShown());

        assertThat(required("shell-nav").isDisabled()).isFalse();
        assertThat(required("shell-actions").isDisabled()).isFalse();
        assertThat(injector.getInstance(WorkVisibility.class).isVisible()).isFalse();
    }

    // IF the navigation stayed enabled behind the scrim, THEN a click in the first moments could leave the screen.
    @Test
    void blocking_work_disablesTheNavigationAndTheToolbarFromTheStart() {
        begin(ActivityKind.GLOSSARY_SCAN, true);

        assertThat(required("shell-nav").isDisabled()).isTrue();
        assertThat(required("shell-actions").isDisabled()).isTrue();
    }

    // IF an error dialog replaced the busy card, THEN the work would continue invisibly; it must sit above it.
    @Test
    void errorDialog_whileBusy_isShownAboveTheCardWhichStays() throws TimeoutException {
        begin(ActivityKind.EXPORT, true);
        awaitCard();
        final StackPane dialog = new StackPane();
        dialog.setId("some-error-dialog");

        onFx(() -> injector.getInstance(ModalHost.class).show(dialog, false));

        assertThat(isCardShown()).isTrue();
        assertThat(scene.getRoot().lookup("#some-error-dialog")).isNotNull();
        final StackPane shell = (StackPane) scene.getRoot();
        assertThat(shell.getChildren().indexOf(required(BusyHost.HOST_ID)))
                .isLessThan(shell.getChildren().indexOf(required("shell-modal-host")));
    }

    private static boolean isInside(final @Nullable Node node, final Node root) {
        return node != null && (node.equals(root) || isInside(node.getParent(), root));
    }

    // IF a card with nothing to press left the focus behind the scrim, THEN Enter or Space would press a button the
    // person cannot even see.
    @Test
    void card_workThatCannotBeStopped_takesTheKeyboardFocusOntoTheCard() throws TimeoutException {
        begin(ActivityKind.IMPORT, false);
        awaitCard();

        assertThat(isInside(scene.getFocusOwner(), required("busy-card"))).isTrue();
    }

    // IF a key could pass the scrim, THEN the window behind would act on it while it is working.
    @ParameterizedTest
    @EnumSource(
            value = KeyCode.class,
            names = {"ENTER", "SPACE", "A"})
    void key_pressedWhileTheCardShows_neverReachesTheWindowBehind(final KeyCode key) throws TimeoutException {
        final List<KeyCode> reached = new CopyOnWriteArrayList<>();
        final Node behind = required("shell-content");
        onFx(() -> behind.addEventHandler(KeyEvent.KEY_PRESSED, event -> reached.add(event.getCode())));
        begin(ActivityKind.IMPORT, false);
        awaitCard();

        onFx(() ->
                Event.fireEvent(behind, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false)));

        assertThat(reached).isEmpty();
    }

    // IF the toasts sat under the scrim, THEN an error raised during blocking work would be dimmed out of sight.
    @Test
    void toasts_whileBusy_areShownAboveTheScrimAndBelowTheDialogs() {
        final StackPane shell = (StackPane) scene.getRoot();
        final int toasts = shell.getChildren().indexOf(required("shell-toast-host"));

        assertThat(toasts).isGreaterThan(shell.getChildren().indexOf(required(BusyHost.HOST_ID)));
        assertThat(toasts).isLessThan(shell.getChildren().indexOf(required("shell-modal-host")));
    }
}
