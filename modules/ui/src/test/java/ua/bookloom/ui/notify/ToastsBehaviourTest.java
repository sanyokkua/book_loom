package ua.bookloom.ui.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Where a toast sits, how it folds, pauses, carries an action and is dismissed. */
class ToastsBehaviourTest extends ToastsTestBase {

    // IF the toast sat bottom-centre, THEN it would cover the toolbar's and the dialogs' buttons; in the corner it is
    // 360 wide, 16 from the right and bottom edges of the window, and clear of the navigation column.
    @Test
    void toast_raised_sitsInTheBottomRightCornerSixteenPixelsIn() {
        onFx(() -> toasts().raise(ToastOccasion.BOOK_OPENED, "A.epub"));
        awaitToasts(1);
        final Node toast = shownToasts().get(0);
        awaitAnimation(toast);

        final javafx.geometry.Bounds toastBox =
                ThemeTestSupport.onFx(() -> toast.localToScene(toast.getLayoutBounds()));
        final javafx.geometry.Bounds navBox = ThemeTestSupport.onFx(() -> {
            final Node nav = scene.getRoot().lookup("#shell-nav");
            return nav.localToScene(nav.getBoundsInLocal());
        });
        final double sceneWidth = ThemeTestSupport.onFx(scene::getWidth);
        final double sceneHeight = ThemeTestSupport.onFx(scene::getHeight);
        assertThat(toastBox.getWidth()).isCloseTo(360, within(1.0));
        assertThat(toastBox.getMaxX()).isCloseTo(sceneWidth - 16, within(1.0));
        assertThat(toastBox.getMaxY()).isCloseTo(sceneHeight - 16, within(1.0));
        assertThat(toastBox.getMinX()).isGreaterThanOrEqualTo(navBox.getMaxX());
    }

    // IF the lifetimes drifted from the spec, THEN a warning would vanish before it can be read.
    @Test
    void lifetimes_standard_areFiveSecondsAndTwelveSeconds() {
        assertThat(ToastStack.GLANCE_LIFETIME).isEqualTo(Duration.ofSeconds(5));
        assertThat(ToastStack.READ_LIFETIME).isEqualTo(Duration.ofSeconds(12));
    }

    // IF a fifth message stacked, THEN a burst would cover the content; the oldest makes way and the newest is last.
    @Test
    void toast_fifthRaised_dropsTheOldestAndKeepsTheNewestAtTheBottom() {
        raiseBooks(4);
        awaitToasts(4);

        raiseBooks(5);

        final List<String> texts = ThemeTestSupport.onFx(
                () -> shownToasts().stream().map(ToastsBehaviourTest::textOf).toList());
        assertThat(texts)
                .containsExactly(
                        "Opened Book2.epub.", "Opened Book3.epub.", "Opened Book4.epub.", "Opened Book5.epub.");
    }

    // IF the newest toast were drawn above the older ones, THEN a burst would read backwards; the newest is the lowest.
    @Test
    void toast_twoRaised_theNewestIsLowest() {
        raiseBooks(2);

        final List<Double> tops = ThemeTestSupport.onFx(() -> shownToasts().stream()
                .map(node -> node.localToScene(node.getBoundsInLocal()).getMinY())
                .toList());
        assertThat(tops.get(1)).isGreaterThan(tops.get(0));
    }

    // IF a person could not dismiss a warning, THEN it would sit over the content for twelve seconds; the close button
    // removes it, and it has a hover explanation.
    @Test
    void closeButton_pressed_removesTheToast() {
        onFx(() -> toasts().raise(ToastOccasion.MODEL_LIST_UNREADABLE));
        final javafx.scene.control.Button close =
                (javafx.scene.control.Button) shownToasts().get(0).lookup(".toast-close");

        onFx(close::fire);
        awaitToasts(0);

        assertThat(close.getTooltip()).isNotNull();
        assertThat(close.getTooltip().getText()).startsWith("Dismisses this message");
    }

    // IF the same message raised five times piled up, THEN the stack would fill with copies; one toast counts them.
    @Test
    void toast_sameMessageRaisedThreeTimes_foldsIntoOneWithACounter() {
        onFx(() -> {
            toasts().raise(ToastOccasion.BOOK_OPENED, "A.epub");
            toasts().raise(ToastOccasion.BOOK_OPENED, "A.epub");
            toasts().raise(ToastOccasion.BOOK_OPENED, "A.epub");
            toasts().raise(ToastOccasion.BOOK_OPENED, "B.epub");
        });

        assertThat(shownToasts()).hasSize(2);
        assertThat(((Label) shownToasts().get(0).lookup(".toast-count")).getText())
                .isEqualTo("×3");
        assertThat(shownToasts().get(1).lookup(".toast-count").isVisible()).isFalse();
    }

    // IF hover did not pause the timer, THEN a message being read would vanish under the pointer.
    @Test
    void toast_hovered_staysPastItsLifetimeAndGoesOnceThePointerLeaves() throws TimeoutException {
        final ToastStack stack = new ToastStack(
                injector.getInstance(Messages.class),
                new DialogFeedback(),
                Duration.ofMillis(300),
                Duration.ofMillis(300));
        final Pane view = (Pane) stack.view();
        ThemeTestSupport.onFx(() -> {
            stack.raise(ToastOccasion.BOOK_OPENED, "A.epub");
            view.getChildren().get(0).fireEvent(mouse(MouseEvent.MOUSE_ENTERED));
            return null;
        });

        sleep(900);

        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().size())).isEqualTo(1);
        ThemeTestSupport.onFx(() -> {
            view.getChildren().get(0).fireEvent(mouse(MouseEvent.MOUSE_EXITED));
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> view.getChildren().isEmpty()));
    }

    // IF a warning left as soon as a success does, THEN a person away from the screen for ten seconds would miss it.
    @Test
    void toast_warningAndSuccess_withShortGlanceLifetime_onlyTheSuccessLeavesFirst() throws TimeoutException {
        final ToastStack stack = new ToastStack(
                injector.getInstance(Messages.class),
                new DialogFeedback(),
                Duration.ofMillis(100),
                Duration.ofSeconds(60));
        final Pane view = (Pane) stack.view();
        ThemeTestSupport.onFx(() -> {
            stack.raise(ToastOccasion.BOOK_OPENED, "A.epub");
            stack.raise(ToastOccasion.MODEL_LIST_UNREADABLE);
            return null;
        });

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> view.getChildren().size() == 1));
        sleep(300);

        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().size())).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().get(0).getStyleClass()))
                .contains("toast-warn");
    }

    // IF an information message that carries an action left after five seconds, THEN a person reading it would lose
    // the chance to press it; an action keeps any severity for the long lifetime, and shows exactly one action.
    @Test
    void actionToast_withShortGlanceLifetime_staysWhileAPlainSuccessLeaves() throws TimeoutException {
        final ToastStack stack = new ToastStack(
                injector.getInstance(Messages.class),
                new DialogFeedback(),
                Duration.ofMillis(100),
                Duration.ofSeconds(60));
        final Pane view = (Pane) stack.view();
        ThemeTestSupport.onFx(() -> {
            stack.raise(ToastOccasion.BOOK_OPENED, "A.epub");
            stack.raiseWithAction(ToastOccasion.HIDDEN_WORK_FINISHED, results(), "Names pass");
            return null;
        });

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> view.getChildren().size() == 1));
        sleep(300);

        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().size())).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(() ->
                        view.getChildren().get(0).lookupAll(".toast-action").size()))
                .isEqualTo(1);
    }

    private static ToastAction results() {
        return new ToastAction(MessageKey.WORK_HIDDEN_RESULTS, MessageKey.WORK_HIDDEN_RESULTS_TIP, () -> {});
    }

    // IF the action button did nothing, or the body click closed a toast that offers an action, THEN the person could
    // lose the chance to act; the button runs the action and removes the toast, a body click leaves it.
    @Test
    void actionToast_bodyClickedThenButtonPressed_runsTheActionOnceAndThenGoes() {
        final int[] runs = {0};
        onFx(() -> toasts().raiseWithAction(
                        ToastOccasion.HIDDEN_WORK_REFUSED,
                        new ToastAction(MessageKey.COMMON_CLOSE, MessageKey.COMMON_CLOSE_TIP, () -> runs[0]++),
                        "Names pass"));
        final Node toast = shownToasts().get(0);

        onFx(() -> toast.fireEvent(click()));
        sleep(400);
        assertThat(shownToasts()).hasSize(1);
        assertThat(runs[0]).isZero();

        onFx(((javafx.scene.control.Button) toast.lookup(".toast-action"))::fire);
        awaitToasts(0);

        assertThat(runs[0]).isEqualTo(1);
    }

    private static MouseEvent mouse(final javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(
                type,
                4,
                4,
                4,
                4,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}
