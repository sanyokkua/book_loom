package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.scene.input.ScrollEvent.VerticalTextScrollUnits;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The arithmetic of a wheel glide: which events glide, how far, how notches add up, and how a frame follows. */
class ScrollGlideTest {

    private static final double EXACT = 1e-9;

    // IF a trackpad, touch or momentum event were glided too, THEN its own smoothing would be applied twice and lag the
    // fingers; only a wheel reports LINES with no fingers, no touch screen and no momentum.
    @ParameterizedTest
    @CsvSource({
        "LINES,0,false,false,true",
        "NONE,0,false,false,false",
        "PAGES,0,false,false,false",
        "LINES,2,false,false,false",
        "LINES,0,true,false,false",
        "LINES,0,false,true,false"
    })
    void isDiscreteWheel_eventKind_onlyAWheelNotchGlides(
            final VerticalTextScrollUnits units,
            final int touchCount,
            final boolean direct,
            final boolean inertia,
            final boolean expected) {
        assertThat(ScrollGlide.isDiscreteWheel(units, touchCount, direct, inertia))
                .isEqualTo(expected);
    }

    // IF a line were measured otherwise than JavaFX measures it, THEN a list would scroll a different distance with the
    // glide on than off: the cell height, but at most an eighth of the viewport.
    @ParameterizedTest
    @CsvSource({"20,400,20", "34,200,25", "0,400,50", "-1,160,20"})
    void lineSize_cellAndViewport_isTheCellCappedAtAnEighthOfTheViewport(
            final double cellSize, final double viewport, final double expected) {
        assertThat(ScrollGlide.lineSize(cellSize, viewport)).isEqualTo(expected);
    }

    // IF the wheel's sign were kept, THEN turning the wheel towards you would move the view up.
    @ParameterizedTest
    @CsvSource({"-3,20,60", "3,20,-60", "-1,34,34"})
    void flowPixels_lines_moveDownTheContentByWholeLines(
            final double textDeltaY, final double lineSize, final double expected) {
        assertThat(ScrollGlide.flowPixels(textDeltaY, lineSize)).isEqualTo(expected);
    }

    // IF a pane scrolled by lines, THEN it would move a different distance than JavaFX moves it by pixels.
    @ParameterizedTest
    @CsvSource({"-40,40", "40,-40", "-120,120"})
    void panePixels_wheelDelta_isTheDistanceDownTheContent(final double deltaY, final double expected) {
        assertThat(ScrollGlide.panePixels(deltaY)).isEqualTo(expected);
    }

    // IF a notch replaced what the glide had left, THEN a fast spin would travel less than its notches; IF a reversal
    // were added, THEN the view would first finish going the old way; IF nothing capped it, THEN a long spin would sail
    // on for seconds after the hand stopped.
    @ParameterizedTest
    @CsvSource({
        "0,40,600,40",
        "25,40,600,65",
        "-25,-40,600,-65",
        "25,-40,600,-40",
        "-25,40,600,40",
        "590,60,600,600",
        "-590,-60,600,-600"
    })
    void retarget_pendingAndNewNotch_addsTheSameWayRestartsOnAReversalAndCaps(
            final double pending, final double added, final double limit, final double expected) {
        assertThat(ScrollGlide.retarget(pending, added, limit)).isEqualTo(expected);
    }

    // IF a frame could step past the target, THEN the view would overshoot and come back; a frame covers part of what
    // is left (an exponential follow), and the last half pixel all at once.
    @ParameterizedTest
    @CsvSource({"100,0.06,63.212055882855765", "-100,0.06,-63.212055882855765", "0.4,0.016,0.4", "-0.3,0.016,-0.3"})
    void followStep_remainingAndFrameTime_coversAShareAndLandsOnTheTarget(
            final double remaining, final double seconds, final double expected) {
        assertThat(ScrollGlide.followStep(remaining, seconds)).isCloseTo(expected, within(EXACT));
    }

    // IF the pixel distance were not scaled by the overflow, THEN a long page would crawl and a short one would jump.
    @ParameterizedTest
    @CsvSource({
        "0,0,1,40,800,0.05",
        "0.5,0,1,-80,800,0.4",
        "0.98,0,1,40,800,1",
        "0.01,0,1,-40,800,0",
        "0.3,0,1,40,0,0.3",
        "0.3,0,1,40,-10,0.3"
    })
    void paneValue_distanceAndOverflow_movesProportionallyWithinTheRange(
            final double value,
            final double min,
            final double max,
            final double pixels,
            final double overflow,
            final double expected) {
        assertThat(ScrollGlide.paneValue(value, min, max, pixels, overflow)).isCloseTo(expected, within(EXACT));
    }

    // IF a view at its end claimed a notch, THEN the view around it could never take over the scroll.
    @ParameterizedTest
    @CsvSource({"0,40,true", "0,-40,false", "1,40,false", "1,-40,true", "0.5,0,false"})
    void hasRoom_positionAndDirection_isFalseOnlyAtTheEdgeItMovesTowards(
            final double position, final double pixels, final boolean expected) {
        assertThat(ScrollGlide.hasRoom(position, 0, 1, pixels)).isEqualTo(expected);
    }
}
