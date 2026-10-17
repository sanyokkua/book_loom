package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The model calls window of the busy card (15j.J6): a resizable, non-modal window owned by the main window, opened at
 * 80 % of it and centred, following its size until the person resizes it, with the calls scrolling inside; the busy
 * card keeps only the progress and a button. The headless screen is 1000 by 1000, so the largest owner proved here is
 * 1000 by 800; the share is the same at any size.
 */
@SuppressWarnings("NullAway.Init")
class BusyHostCallsWindowTest extends ShellTestBase {

    private static final long APPEARS_WITHIN_SECONDS = 3;
    private static final String WINDOW_ROOT = "model-calls-window";
    private static final double PIXEL = 1.5;

    private final List<String> cancelled = new CopyOnWriteArrayList<>();
    private ActivityTracker.Handle activity;

    private ActivityTracker tracker() {
        return injector.getInstance(ActivityTracker.class);
    }

    private void startExportWithACall() throws TimeoutException {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() -> handle.set(tracker().begin(ActivityKind.EXPORT, () -> cancelled.add(ActivityKind.EXPORT.name()))));
        activity = Objects.requireNonNull(handle.get(), "handle");
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, this::isCardShown);
        onFx(() -> activity.calls(LiveCallFixtures.calls(
                LiveCallFixtures.waiting(
                        4, 3, "She had lost her mother.", LiveCallFixtures.largePrompt("Long. ".repeat(80))),
                null)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isCardShown() {
        final Node host = scene.getRoot().lookup("#" + BusyHost.HOST_ID);
        return host != null && host.isVisible() && host.getOpacity() > 0;
    }

    private void ownerSize(final double width, final double height) {
        interact(() -> {
            final Window owner = scene.getWindow();
            owner.setWidth(width);
            owner.setHeight(height);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openWindow() {
        onFx(() -> ((Button) required(BusyHost.SHOW_CALLS_ID)).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Stage callsWindow() {
        return ThemeTestSupport.onFx(() -> Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(window -> window.getScene() != null
                        && WINDOW_ROOT.equals(window.getScene().getRoot().getId()))
                .map(Stage.class::cast)
                .findFirst()
                .orElse(null));
    }

    // IF the viewer were another card on the shell, THEN the prompts would still push it past the screen.
    @Test
    void showModelCalls_pressed_opensANonModalResizableWindowOwnedByTheMainWindow() throws TimeoutException {
        startExportWithACall();

        openWindow();

        final Stage window = Objects.requireNonNull(callsWindow(), "the model calls window");
        assertThat(ThemeTestSupport.onFx(window::getOwner)).isSameAs(scene.getWindow());
        assertThat(ThemeTestSupport.onFx(window::getModality)).isEqualTo(Modality.NONE);
        assertThat(ThemeTestSupport.onFx(window::isResizable)).isTrue();
        assertThat(ThemeTestSupport.onFx(window::getTitle)).isEqualTo("Model calls");
        assertThat(ThemeTestSupport.onFx(
                        () -> ((Label) window.getScene().getRoot().lookup("#model-calls-current-label")).getText()))
                .isEqualTo("draft-batch-json");
    }

    // IF the window opened at a fixed size, THEN a small laptop screen would cut it and a large one waste the space.
    @ParameterizedTest
    @CsvSource({"1000, 800, 800, 640", "960, 640, 768, 512"})
    void window_opened_isEightyPercentOfTheOwnerAndCentredOnIt(
            final double ownerWidth, final double ownerHeight, final double width, final double height)
            throws TimeoutException {
        ownerSize(ownerWidth, ownerHeight);
        startExportWithACall();

        openWindow();

        final Stage window = Objects.requireNonNull(callsWindow(), "the model calls window");
        final Window owner = scene.getWindow();
        assertThat(ThemeTestSupport.onFx(window::getWidth)).isCloseTo(width, within(PIXEL));
        assertThat(ThemeTestSupport.onFx(window::getHeight)).isCloseTo(height, within(PIXEL));
        assertThat(ThemeTestSupport.onFx(window::getX))
                .isCloseTo(
                        ThemeTestSupport.onFx(owner::getX) + (ThemeTestSupport.onFx(owner::getWidth) - width) / 2,
                        within(PIXEL));
        assertThat(ThemeTestSupport.onFx(window::getY))
                .isCloseTo(
                        ThemeTestSupport.onFx(owner::getY) + (ThemeTestSupport.onFx(owner::getHeight) - height) / 2,
                        within(PIXEL));
    }

    // IF the window kept its first size, THEN a window moved to a smaller screen would hang off it.
    @Test
    void window_ownerResized_followsItsSizeUntilThePersonResizesTheWindow() throws TimeoutException {
        ownerSize(1000, 800);
        startExportWithACall();
        openWindow();
        final Stage window = Objects.requireNonNull(callsWindow(), "the model calls window");

        ownerSize(900, 700);
        assertThat(ThemeTestSupport.onFx(window::getWidth)).isCloseTo(720, within(PIXEL));

        interact(() -> window.setWidth(640));
        WaitForAsyncUtils.waitForFxEvents();
        ownerSize(1000, 800);

        assertThat(ThemeTestSupport.onFx(window::getWidth)).isCloseTo(640, within(PIXEL));
    }

    // IF the busy card still held the calls, THEN opened prompts would make it taller than the window behind it.
    @ParameterizedTest
    @CsvSource({"1000, 800", "960, 640"})
    void card_callsPromptsLong_neverExceedsTheOwner(final double ownerWidth, final double ownerHeight)
            throws TimeoutException {
        ownerSize(ownerWidth, ownerHeight);
        startExportWithACall();

        final Node card = required(BusyHost.CARD_ID);

        assertThat(ThemeTestSupport.onFx(() -> card.getBoundsInLocal().getWidth()))
                .isLessThanOrEqualTo(ThemeTestSupport.onFx(scene::getWidth));
        assertThat(ThemeTestSupport.onFx(() -> card.getBoundsInLocal().getHeight()))
                .isLessThanOrEqualTo(ThemeTestSupport.onFx(scene::getHeight));
    }

    // IF the calls scrolled with the page only, THEN a long prompt would hide the reply under the window's edge.
    @Test
    void window_longCalls_scrollInsideIt() throws TimeoutException {
        startExportWithACall();
        openWindow();

        final Parent root = Objects.requireNonNull(callsWindow()).getScene().getRoot();

        assertThat(ThemeTestSupport.onFx(() -> root.lookup(".scroll-pane"))).isInstanceOf(ScrollPane.class);
    }

    // IF the open window swallowed Cancel, THEN a person reading a prompt could not stop the export.
    @Test
    void cancel_pressedWhileTheWindowIsOpen_stillStopsTheWork() throws TimeoutException {
        startExportWithACall();
        openWindow();

        onFx(() -> ((Button) required(BusyHost.CANCEL_ID)).fire());

        assertThat(cancelled).containsExactly("EXPORT");
    }

    // IF the window outlived the work, THEN it would show calls of work that is over.
    @Test
    void window_workEnds_closesIt() throws TimeoutException {
        startExportWithACall();
        openWindow();

        onFx(activity::end);
        WaitForAsyncUtils.waitFor(APPEARS_WITHIN_SECONDS, TimeUnit.SECONDS, () -> callsWindow() == null);

        assertThat(callsWindow()).isNull();
    }

    // IF the window were themed once, THEN switching to dark with it open would leave it light.
    @Test
    void window_themeChanged_followsTheBlock() throws TimeoutException {
        onFx(() -> themeController.setMode(ThemeMode.DARK));
        startExportWithACall();
        openWindow();
        final Parent root = Objects.requireNonNull(callsWindow()).getScene().getRoot();
        assertThat(ThemeTestSupport.onFx(() -> root.getStyleClass())).contains(Theme.DARK_STYLE_CLASS);

        onFx(() -> themeController.setMode(ThemeMode.LIGHT));

        assertThat(ThemeTestSupport.onFx(() -> root.getStyleClass())).doesNotContain(Theme.DARK_STYLE_CLASS);
        final Scene own = Objects.requireNonNull(callsWindow()).getScene();
        assertThat(ThemeTestSupport.onFx(own::getStylesheets)).contains(Theme.stylesheet());
    }

    // IF the window painted a colour of its own, THEN it would not follow the block's palette in light and dark.
    @ParameterizedTest
    @CsvSource({"LIGHT, #f4f1ea", "DARK, #283237"})
    void window_inEachBlock_paintsThePageColourTokenAndNoInlineStyle(final ThemeMode mode, final String bg)
            throws TimeoutException {
        onFx(() -> themeController.setMode(mode));
        startExportWithACall();
        openWindow();
        final Parent root = Objects.requireNonNull(callsWindow()).getScene().getRoot();

        final Object fill = ThemeTestSupport.onFx(() -> {
            root.applyCss();
            return ((Region) root).getBackground().getFills().getFirst().getFill();
        });

        ThemeTestSupport.assertSameColour(fill, bg, "model calls window background in " + mode);
        assertThat(ThemeTestSupport.onFx(root::getStyle)).isEmpty();
    }
}
