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
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * The busy layer: it waits before showing, describes the work from the tracker, stops it on Cancel, cannot be dismissed
 * from the keyboard, and leaves the navigation and toolbar disabled for as long as blocking work runs.
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

    private void awaitCard() throws TimeoutException {
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, this::isCardShown);
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

        assertThat(text(BusyHost.TITLE_ID)).isEqualTo("Export");
    }

    // IF the card hid the export's calls, THEN a long consistency pass would be a spinner with no way to see the model.
    @Test
    void card_workShowsACall_offersTheFoldedCallSectionWithTheCall() throws TimeoutException {
        final ActivityTracker.Handle export = begin(ActivityKind.EXPORT, true);
        awaitCard();
        assertThat(required(BusyHost.CALLS_ID).isVisible()).isFalse();

        onFx(() -> export.calls(
                LiveCallFixtures.calls(LiveCallFixtures.waiting(4, 1, "One.", LiveCallFixtures.smallPrompt()), null)));
        WaitForAsyncUtils.waitForFxEvents();

        final TitledPane calls = (TitledPane) required(BusyHost.CALLS_ID);
        assertThat(calls.isVisible()).isTrue();
        assertThat(calls.isExpanded()).isFalse();
        assertThat(calls.getText()).isEqualTo("Model calls");
        assertThat(text(BusyHost.CALL_ID + "-label")).isEqualTo("draft-batch-json");
    }

    // IF the card showed only the current call, THEN the person could not compare what the last two calls sent.
    @Test
    void card_workWithTwoCalls_showsThePreviousCallBelowTheCurrentOne() throws TimeoutException {
        final ActivityTracker.Handle scan = begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();
        final var first =
                LiveCallFixtures.answered(LiveCallFixtures.waiting(1, 1, "One.", LiveCallFixtures.smallPrompt()), "{}");
        final var second = LiveCallFixtures.waiting(2, 1, "Two.", LiveCallFixtures.smallPrompt());

        onFx(() -> scan.calls(LiveCallFixtures.calls(second, first)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(required(BusyHost.CALL_ID + "-previous").isVisible()).isTrue();
        assertThat(text(BusyHost.CALL_ID + "-previous-title")).isEqualTo("Previous call");
        assertThat(text(BusyHost.CALL_ID + "-previous-segment-1-source-text")).isEqualTo("One. #1");
        assertThat(text(BusyHost.CALL_ID + "-segment-1-source-text")).isEqualTo("Two. #1");
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

    // IF the card did not read the tracker, THEN the person would see a spinner with nothing said about the work.
    @Test
    void card_workWithProgressAndDetails_showsStepFractionDetailsAndTimes() throws TimeoutException {
        final ActivityTracker.Handle export = begin(ActivityKind.EXPORT, true);
        onFx(() -> {
            export.progress(1, 4, "Checking paragraphs");
            export.details(List.of(new Activity.Detail("Model", "gemma"), new Activity.Detail("File", "b.epub")));
        });
        awaitCard();

        assertThat(text(BusyHost.STEP_ID)).isEqualTo("Checking paragraphs");
        assertThat(((ProgressBar) required(BusyHost.BAR_ID)).getProgress()).isEqualTo(0.25);
        assertThat(textsUnder(required(BusyHost.DETAILS_ID))).contains("Model", "gemma", "File", "b.epub");
        assertThat(text(BusyHost.ELAPSED_ID)).startsWith("Elapsed ");
    }

    // IF work with no known fraction drew a determinate bar, THEN it would sit at zero and look stuck.
    @Test
    void card_workWithoutAFraction_isIndeterminate() throws TimeoutException {
        begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();

        assertThat(((ProgressBar) required(BusyHost.BAR_ID)).getProgress()).isLessThan(0);
    }

    // IF Cancel did not reach the activity, THEN the card would be a trap; once pressed it must not be pressable again
    // and the step must say what is happening.
    @Test
    void cancel_pressed_stopsTheActivityOnceAndSaysCancelling() throws TimeoutException {
        begin(ActivityKind.GLOSSARY_SCAN, true);
        awaitCard();
        final Button cancel = (Button) required(BusyHost.CANCEL_ID);

        onFx(cancel::fire);
        onFx(cancel::fire);

        assertThat(cancelled).containsExactly("GLOSSARY_SCAN");
        assertThat(cancel.isDisabled()).isTrue();
        assertThat(text(BusyHost.STEP_ID)).isEqualTo("Cancelling…");
        assertThat(TooltipProbe.tipText(cancel)).isNotBlank();
    }

    // IF a card offered Cancel for work that cannot be stopped, THEN pressing it would do nothing.
    @Test
    void card_workThatCannotBeStopped_hasNoCancelButtonAndSaysSo() throws TimeoutException {
        begin(ActivityKind.IMPORT, false);
        awaitCard();

        assertThat(required(BusyHost.CANCEL_ID).isVisible()).isFalse();
        assertThat(text(BusyHost.STEP_ID)).isEqualTo("This work cannot be stopped; it ends by itself.");
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
    }

    // IF Escape closed the card, THEN the person could uncover a window that is still working.
    @Test
    void escape_pressedOnTheCard_doesNotDismissIt() throws TimeoutException {
        begin(ActivityKind.EXPORT, true);
        awaitCard();

        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(isCardShown()).isTrue();
    }

    // IF the navigation stayed enabled behind the scrim, THEN a click in the first moments could leave the screen.
    @Test
    void blocking_work_disablesTheNavigationAndTheToolbarFromTheStart() {
        begin(ActivityKind.EXPORT, true);

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
        assertThat(MessageKey.BUSY_CANCEL_TIP).isNotNull();
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

        assertThat(isInside(scene.getFocusOwner(), required(BusyHost.CARD_ID))).isTrue();
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
