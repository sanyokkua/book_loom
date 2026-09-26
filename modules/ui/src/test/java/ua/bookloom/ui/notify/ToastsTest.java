package ua.bookloom.ui.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The transient-message surface: four severities each drawn in its status role under both value blocks, the five
 * occasions this build raises, the cap on how many show at once, and how a message goes away. Every check raises the
 * message through the real stack and reads what the scene shows.
 */
class ToastsTest extends ShellTestBase {

    private static final long WAIT_SECONDS = 5;
    private static final int MOST_VISIBLE = 4;

    /** A severity, the style class marking it, how to raise it, and the role value hand-copied from the catalogue. */
    private enum Level {
        SUCCESS("toast-ok", "#5f8a6b", "#7faa8a") {
            @Override
            void raise(final Toasts toasts, final MessageKey key, final Object... args) {
                toasts.success(key, args);
            }
        },
        INFO("toast-info", "#4d6b78", "#7a9dab") {
            @Override
            void raise(final Toasts toasts, final MessageKey key, final Object... args) {
                toasts.info(key, args);
            }
        },
        WARNING("toast-warn", "#bd863a", "#d0a25a") {
            @Override
            void raise(final Toasts toasts, final MessageKey key, final Object... args) {
                toasts.warning(key, args);
            }
        },
        ERROR("toast-err", "#b0574c", "#cc7a6f") {
            @Override
            void raise(final Toasts toasts, final MessageKey key, final Object... args) {
                toasts.error(key, args);
            }
        };

        private final String styleClass;
        private final String lightHex;
        private final String darkHex;

        Level(final String styleClass, final String lightHex, final String darkHex) {
            this.styleClass = styleClass;
            this.lightHex = lightHex;
            this.darkHex = darkHex;
        }

        abstract void raise(Toasts toasts, MessageKey key, Object... args);

        String expected(final ThemeMode block) {
            return block == ThemeMode.DARK ? darkHex : lightHex;
        }
    }

    static Stream<Arguments> everySeverityInEachBlock() {
        return Stream.of(Level.values())
                .flatMap(level -> Stream.of(ThemeMode.LIGHT, ThemeMode.DARK)
                        .map(block -> Arguments.of(Named.of(level.name(), level), block)));
    }

    private Toasts toasts() {
        return injector.getInstance(Toasts.class);
    }

    private List<Node> shownToasts() {
        return List.copyOf(scene.getRoot().lookupAll(".toast"));
    }

    private static String textOf(final Node toast) {
        return ((Label) toast.lookup(".toast-text")).getText();
    }

    private static MouseEvent click() {
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

    private void raiseBooks(final int count) {
        onFx(() -> IntStream.rangeClosed(1, count)
                .forEach(number -> toasts().success(MessageKey.TOAST_BOOK_OPENED, "Book" + number + ".epub")));
    }

    // IF a severity took a colour of its own instead of its status role, THEN the same colour would mean different
    // things on a chip and on a message; the icon and the left edge both carry the role.
    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("everySeverityInEachBlock")
    void toast_bySeverity_underEachBlock_isDrawnInItsStatusRole(final Level level, final ThemeMode block) {
        onFx(() -> themeController.setMode(block));

        onFx(() -> level.raise(toasts(), MessageKey.COMMON_CLOSE));

        final Node toast = required("shell-toast-host").lookup("." + level.styleClass);
        assertThat(toast).as("a %s toast must be shown", level).isNotNull();
        final FontIcon icon = (FontIcon) toast.lookup(".toast-icon");
        assertThat(icon).as("the toast must carry an icon").isNotNull();
        ThemeTestSupport.assertSameColour(icon.getIconColor(), level.expected(block), level + " icon under " + block);
        assertThat(((Region) toast).getBorder())
                .as("the toast must draw a border")
                .isNotNull();
        ThemeTestSupport.assertSameColour(
                ((Region) toast).getBorder().getStrokes().get(0).getLeftStroke(),
                level.expected(block),
                level + " left edge under " + block);
    }

    // IF the message were not resolved from the catalogue, THEN a toast could show words no language file owns.
    @Test
    void success_bookOpened_raisesOneSuccessToastNamingTheBook() {
        onFx(() -> toasts().success(MessageKey.TOAST_BOOK_OPENED, "Frankenstein.epub"));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass()).contains("toast", "toast-ok");
        assertThat(textOf(shownToasts().get(0))).isEqualTo("Opened Frankenstein.epub.");
    }

    // IF a passed provider check raised anything but success, THEN a good result would look like a problem.
    @Test
    void success_providerPassed_raisesOneSuccessToast() {
        onFx(() -> toasts().success(MessageKey.TOAST_PROVIDER_PASSED));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-ok");
        assertThat(textOf(shownToasts().get(0))).isEqualTo("The provider passed its check.");
    }

    // IF a clean finish raised anything but success, THEN a good result would look like a problem.
    @Test
    void success_runFinished_raisesOneSuccessToastWithTheAcceptedCount() {
        onFx(() -> toasts().success(MessageKey.TOAST_RUN_FINISHED, 5));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-ok");
        assertThat(textOf(shownToasts().get(0))).isEqualTo("Translation finished: 5 accepted.");
    }

    // IF a run with flagged segments were congratulated, THEN the person would not know segments need review.
    @Test
    void warning_runFinishedFlagged_raisesOneWarningToastAndNoSuccessToast() {
        onFx(() -> toasts().warning(MessageKey.TOAST_RUN_FINISHED_FLAGGED, 1237, 3));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-warn").doesNotContain("toast-ok");
        assertThat(textOf(shownToasts().get(0)))
                .isEqualTo("Translation finished with flagged segments: 1,237 accepted, 3 flagged.");
    }

    // IF an unreadable model list raised anything but a warning, THEN a soft degradation would look like a failure.
    @Test
    void warning_modelListUnreadable_raisesOneWarningToast() {
        onFx(() -> toasts().warning(MessageKey.TOAST_MODEL_LIST_UNREADABLE));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-warn");
        assertThat(textOf(shownToasts().get(0)))
                .isEqualTo("The model list could not be read. You can still type a model name.");
    }

    // IF toasts piled up without a cap, THEN a burst of events would cover the window.
    @Test
    void toast_moreThanFourRaised_keepsTheNewestFourAndDropsTheOldest() {
        raiseBooks(MOST_VISIBLE + 2);

        assertThat(shownToasts()).hasSize(4);
        assertThat(shownToasts().stream().map(ToastsTest::textOf))
                .containsExactlyInAnyOrder(
                        "Opened Book3.epub.", "Opened Book4.epub.", "Opened Book5.epub.", "Opened Book6.epub.");
    }

    // IF a click did not remove the toast, THEN a person could not clear a message that covers something.
    @Test
    void toast_clicked_isRemoved() {
        raiseBooks(2);
        assertThat(shownToasts()).hasSize(2);
        final Node first = shownToasts().get(0);

        onFx(() -> first.fireEvent(click()));

        assertThat(shownToasts()).hasSize(1).doesNotContain(first);
    }

    // IF a toast stayed forever, THEN messages would accumulate until the person cleared each by hand.
    @Test
    void toast_leftAlone_disappearsByItself() throws TimeoutException {
        final ToastStack stack = new ToastStack(injector.getInstance(Messages.class), Duration.ofMillis(50));
        final Pane view = (Pane) stack.view();

        ThemeTestSupport.onFx(() -> {
            stack.success(MessageKey.TOAST_PROVIDER_PASSED);
            return null;
        });
        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().size())).isEqualTo(1);

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> view.getChildren().isEmpty()));

        assertThat(ThemeTestSupport.onFx(() -> view.getChildren().size())).isZero();
    }

    // IF the toast text ignored the display language, THEN a Ukrainian session would show English messages.
    @Test
    void success_underUkrainian_drawsTheTextFromTheUkrainianCatalogue() {
        useLocale(Locale.of("uk"));

        onFx(() -> toasts().success(MessageKey.TOAST_PROVIDER_PASSED));

        assertThat(shownToasts()).hasSize(1);
        assertThat(textOf(shownToasts().get(0))).isEqualTo("Постачальник пройшов перевірку.");
    }
}
