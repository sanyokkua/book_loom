package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;

/**
 * The real shell at and below its minimum window size the window limit, and the navigation column and
 * content area that scroll instead of clipping. Split from {@link AppShellViewTest} to keep both under the file-length
 * limit.
 */
class AppShellViewSizeTest extends ShellTestBase {

    private Bounds sceneBounds(final String id) {
        final Node node = required(id);
        return node.localToScene(node.getLayoutBounds());
    }

    // IF the shell were centred and its parts could not shrink, THEN a window this small would push the title bar
    // above the top edge, where nothing could reach it; the sizes run from the minimum down to well below it.
    @ParameterizedTest
    @CsvSource({CONTENT_AT_MINIMUM_WIDTH + ", " + CONTENT_AT_MINIMUM_HEIGHT, "960, 400", "960, 320"})
    void scene_atMinimumAndBelow_titleBarAndFirstNavEntryAreNotClipped(final double width, final double height) {
        resizeScene(width, height);

        final Bounds titleBar = sceneBounds("shell-title-bar");
        assertThat(titleBar.getMinX()).isZero();
        assertThat(titleBar.getMinY()).isZero();
        assertThat(sceneBounds("nav-projects").getMinY()).isGreaterThanOrEqualTo(titleBar.getMaxY());
        final Node brand = scene.getRoot().lookup(".nav-brand");
        assertThat(brand.localToScene(brand.getLayoutBounds()).getMinY()).isGreaterThanOrEqualTo(titleBar.getMaxY());
    }

    // IF the run status could not shrink, THEN a long book name would push the theme toggle and About out of the
    // title bar; the file name gives way first.
    @Test
    void titleBar_longestRunStatusUnderUkrainian_keepsThemeToggleAndAboutInside() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("a-very-long-book-title-".repeat(3).substring(0, 60) + ".epub", null);
        mirror.publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
        mirror.live().publishThroughput(new Throughput(null, false, Duration.ofMinutes(80), Duration.ofMinutes(62)));
        mirror.publishRunState(RunState.PAUSED);
        mirror.review()
                .publishProviderError(AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening."));
        onFx(() -> {});
        WaitForAsyncUtils.waitForFxEvents();

        final Bounds titleBar = sceneBounds("shell-title-bar");
        assertThat(sceneBounds("shell-run-status").getMaxX())
                .isLessThanOrEqualTo(sceneBounds("shell-theme-toggle").getMinX());
        assertThat(sceneBounds("shell-theme-toggle").getMinX()).isGreaterThanOrEqualTo(titleBar.getMinX());
        assertThat(sceneBounds("shell-about").getMaxX()).isLessThanOrEqualTo(titleBar.getMaxX());
        assertThat(titleBar.getWidth()).isEqualTo(CONTENT_AT_MINIMUM_WIDTH);
    }

    // IF the navigation column were taller than a short window with no way to scroll it, THEN its lower entries
    // (Settings) would be unreachable; the column must be a scroll pane that fits its width and never scrolls sideways.
    @Test
    void navigation_columnTallerThanTheWindow_scrollsVerticallyToTheLastEntry() {
        resizeScene(960, 320);
        final ScrollPane nav = (ScrollPane) required("shell-nav");

        assertThat(nav.getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.NEVER);
        assertThat(nav.isFitToWidth()).isTrue();
        assertThat(nav.getContent().getLayoutBounds().getHeight())
                .isGreaterThan(nav.getViewportBounds().getHeight());
        onFx(() -> nav.setVvalue(nav.getVmax()));
        final Bounds settings = sceneBounds("nav-settings");
        final Bounds viewport = nav.localToScene(nav.getLayoutBounds());
        assertThat(settings.getMaxY()).isLessThanOrEqualTo(viewport.getMaxY());
    }

    // IF the content host were not a scroll pane, THEN a screen taller than the window could not be reached; the pane
    // fits both dimensions so that a list which grows still fills the viewport.
    @Test
    void content_host_sitsInAScrollPaneThatFitsBothDimensions() {
        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");

        assertThat(pane.getContent()).isSameAs(required("shell-content"));
        assertThat(pane.isFitToWidth()).isTrue();
        assertThat(pane.isFitToHeight()).isTrue();
        assertThat(pane.getVbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        assertThat(pane.getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    }

    // IF the two published numbers drifted from the specified minimum, THEN the composition root would apply a window
    // limit the specification does not state.
    @Test
    void windowMinimum_publishedConstants_areNineSixtyBySixForty() {
        assertThat(AppShellView.MIN_WIDTH).isEqualTo(960);
        assertThat(AppShellView.MIN_HEIGHT).isEqualTo(640);
    }

    // IF applying the limits touched only the constants, THEN the window a person drags would still shrink freely. A
    // stage of its own is used because the test framework shares one shown stage between tests, and a minimum left on
    // it would stop later tests resizing it.
    @Test
    void applyWindowLimits_aStage_setsItsOuterMinimum() {
        final Stage[] stage = new Stage[1];

        interact(() -> {
            stage[0] = new Stage();
            shell.applyWindowLimits(stage[0]);
        });

        assertThat(stage[0].getMinWidth()).isEqualTo(960);
        assertThat(stage[0].getMinHeight()).isEqualTo(640);
    }
}
