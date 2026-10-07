package ua.bookloom.ui.notify;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.MessageKey;

/** What the toast tests share: raising through the real stack and reading what the scene shows. */
abstract class ToastsTestBase extends ShellTestBase {

    protected static final long WAIT_SECONDS = 5;

    protected Toasts toasts() {
        return injector.getInstance(Toasts.class);
    }

    protected List<Node> shownToasts() {
        return List.copyOf(scene.getRoot().lookupAll(".toast"));
    }

    protected static String textOf(final Node toast) {
        return ((Label) toast.lookup(".toast-text")).getText();
    }

    protected static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }

    protected void awaitToasts(final int count) {
        try {
            WaitForAsyncUtils.waitFor(
                    WAIT_SECONDS,
                    TimeUnit.SECONDS,
                    () -> ThemeTestSupport.onFx(() -> shownToasts().size() == count));
        } catch (TimeoutException e) {
            throw new AssertionError("the number of toasts never became " + count, e);
        }
    }

    protected void awaitAnimation(final Node toast) {
        try {
            WaitForAsyncUtils.waitFor(
                    WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(() -> toast.getTranslateY() == 0));
        } catch (TimeoutException e) {
            throw new AssertionError("the toast never finished sliding in", e);
        }
    }

    protected void raiseBooks(final int count) {
        onFx(() -> IntStream.rangeClosed(1, count)
                .forEach(number -> toasts().success(MessageKey.TOAST_BOOK_OPENED, "Book" + number + ".epub")));
    }
}
