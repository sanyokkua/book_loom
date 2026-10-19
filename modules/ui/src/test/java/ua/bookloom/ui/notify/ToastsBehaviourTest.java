package ua.bookloom.ui.notify;

import static org.assertj.core.api.Assertions.assertThat;

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

    // IF the host covered the whole window, THEN a toast could hide the title bar or a toolbar button; it lives in the
    // content area, bottom centre, and the toolbar above it stays uncovered.
    @Test
    void toast_raised_sitsAtTheBottomCentreOfTheContentAreaBelowTheToolbar() {
        onFx(() -> toasts().success(MessageKey.TOAST_PROVIDER_PASSED));
        awaitToasts(1);
        final Node toast = shownToasts().get(0);
        awaitAnimation(toast);

        final Node content = required("shell-content-scroll");
        final javafx.geometry.Bounds toastBox =
                ThemeTestSupport.onFx(() -> toast.localToScene(toast.getBoundsInLocal()));
        final javafx.geometry.Bounds contentBox =
                ThemeTestSupport.onFx(() -> content.localToScene(content.getBoundsInLocal()));
        final javafx.geometry.Bounds toolbarBox = ThemeTestSupport.onFx(() -> {
            final Node toolbar = scene.getRoot().lookup(".shell-toolbar");
            return toolbar.localToScene(toolbar.getBoundsInLocal());
        });
        assertThat(toastBox.getMinY()).isGreaterThanOrEqualTo(toolbarBox.getMaxY());
        assertThat(toastBox.getMaxY()).isLessThanOrEqualTo(contentBox.getMaxY());
        assertThat((toastBox.getMinX() + toastBox.getMaxX()) / 2)
                .isCloseTo(
                        (contentBox.getMinX() + contentBox.getMaxX()) / 2, org.assertj.core.api.Assertions.within(1.0));
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
        onFx(() -> toasts().warning(MessageKey.TOAST_MODEL_LIST_UNREADABLE));
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
            toasts().success(MessageKey.TOAST_BOOK_OPENED, "A.epub");
            toasts().success(MessageKey.TOAST_BOOK_OPENED, "A.epub");
            toasts().success(MessageKey.TOAST_BOOK_OPENED, "A.epub");
            toasts().success(MessageKey.TOAST_BOOK_OPENED, "B.epub");
        });

        assertThat(shownToasts()).hasSize(2);
        assertThat(((Label) shownToasts().get(0).lookup(".toast-count")).getText())
                .isEqualTo("×3");
        assertThat(shownToasts().get(1).lookup(".toast-count").isVisible()).isFalse();
    }

    // IF hover did not pause the timer, THEN a message being read would vanish under the pointer.
    @Test
    void toast_hovered_staysPastItsLifetimeAndGoesOnceThePointerLeaves() throws TimeoutException {
        final ToastStack stack =
                new ToastStack(injector.getInstance(Messages.class), Duration.ofMillis(300), Duration.ofMillis(300));
        final Pane view = (Pane) stack.view();
        ThemeTestSupport.onFx(() -> {
            stack.success(MessageKey.TOAST_PROVIDER_PASSED);
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
        final ToastStack stack =
                new ToastStack(injector.getInstance(Messages.class), Duration.ofMillis(100), Duration.ofSeconds(60));
        final Pane view = (Pane) stack.view();
        ThemeTestSupport.onFx(() -> {
            stack.success(MessageKey.TOAST_PROVIDER_PASSED);
            stack.warning(MessageKey.TOAST_MODEL_LIST_UNREADABLE);
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

    // IF the action button did nothing, or the body click closed a toast that offers an action, THEN the person could
    // lose the chance to act; the button runs the action and removes the toast, a body click leaves it.
    @Test
    void actionToast_bodyClickedThenButtonPressed_runsTheActionOnceAndThenGoes() {
        final int[] runs = {0};
        onFx(() -> toasts().raise(
                        ua.bookloom.ui.notify.Severity.WARNING,
                        MessageKey.TOAST_MODEL_LIST_UNREADABLE,
                        new ToastAction(MessageKey.COMMON_CLOSE, MessageKey.COMMON_CLOSE_TIP, () -> runs[0]++)));
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
