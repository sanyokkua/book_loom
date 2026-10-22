package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.RunState;

/**
 * (The large size is 1440 by 900: JavaFX's headless screen is a 1000 by 1000 pixel buffer, and a 1920 by 1080 window
 * takes the toolkit down for every later test.)
 *
 * Where the read-only banner and the project name line sit at the smallest and a large window: the banner between the
 * subtitle and the columns, the name line inside the languages card, and nothing pushed past the viewport.
 */
class BookBriefReadOnlyLayoutTest extends BriefRunScreenTestBase {

    // IF the banner sat below the columns, THEN a person scrolled past every locked control before learning why.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1440, 900"})
    void banner_runAtWork_sitsBetweenTheSubtitleAndTheColumns(final double width, final double height)
            throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 10);

        showAtSceneSize(width, height);

        final Bounds banner = boundsOf("brief-readonly-banner");
        assertThat(boundsOf("brief-subtitle").getMaxY()).isLessThanOrEqualTo(banner.getMinY());
        assertThat(banner.getMaxY())
                .isLessThanOrEqualTo(boundsOf("brief-columns").getMinY());
        assertThat(banner.getWidth()).isGreaterThan(0);
    }

    // IF the name line were outside the languages card, THEN it would not read as part of the pair it names.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1440, 900"})
    void nameLine_bookOpened_sitsInsideTheLanguagesCardBelowTheTargetBox(final double width, final double height)
            throws Exception {
        openAndShowBrief();

        showAtSceneSize(width, height);

        assertThat(isInside("brief-project-line", "brief-languages-card")).isTrue();
        assertThat(boundsOf("brief-target").getMaxY())
                .isLessThanOrEqualTo(boundsOf("brief-project-line").getMinY());
        assertThat(boundsOf("brief-rename").getMaxX())
                .isLessThanOrEqualTo(boundsOf("brief-languages-card").getMaxX());
    }

    // IF the banner or the name line widened the content, THEN a horizontal scroll bar would hide the right column.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1440, 900"})
    void screen_runAtWork_neverOverflowsTheViewportWidth(final double width, final double height) throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 10);

        showAtSceneSize(width, height);

        final ScrollPane scroll = (ScrollPane) required("brief-screen");
        final double content = ThemeTestSupport.onFx(
                () -> scroll.getContent().getLayoutBounds().getWidth());
        final double viewport =
                ThemeTestSupport.onFx(() -> scroll.getViewportBounds().getWidth());
        assertThat(content).isLessThanOrEqualTo(viewport);
    }
}
