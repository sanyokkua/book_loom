package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The arithmetic of a wheel glide: which events glide, how far, how notches add up, and where a pane ends up. */
class ScrollGlideTest {

    private static final double EXACT = 1e-9;

    // IF a trackpad or touch event were glided too, THEN its own momentum would be smoothed twice and lag the fingers.
    @ParameterizedTest
    @CsvSource({
        "false,false,false,0,-40,true",
        "false,false,false,0,40,true",
        "true,false,false,0,-40,false",
        "false,true,false,0,-40,false",
        "false,false,true,0,-40,false",
        "false,false,false,2,-40,false",
        "false,false,false,0,0,false"
    })
    void isDiscreteWheel_eventKind_onlyAWheelNotchGlides(
            final boolean direct,
            final boolean inertia,
            final boolean inGesture,
            final int touchCount,
            final double deltaY,
            final boolean expected) {
        assertThat(ScrollGlide.isDiscreteWheel(direct, inertia, inGesture, touchCount, deltaY))
                .isEqualTo(expected);
    }

    // IF the wheel's sign were kept, THEN turning the wheel towards you would move the view up.
    @ParameterizedTest
    @CsvSource({"-40,40", "40,-40", "-120,120"})
    void pixelsOf_wheelDelta_isTheDistanceDownTheContent(final double deltaY, final double expected) {
        assertThat(ScrollGlide.pixelsOf(deltaY)).isEqualTo(expected);
    }

    // IF a notch replaced what the running glide had left, THEN a fast spin would travel less than its notches add up
    // to; IF a reversal were added, THEN the view would first finish going the old way.
    @ParameterizedTest
    @CsvSource({"0,40,40", "25,40,65", "-25,-40,-65", "25,-40,-40", "-25,40,40"})
    void accumulate_pendingAndNewNotch_addsTheSameWayAndRestartsOnAReversal(
            final double pending, final double added, final double expected) {
        assertThat(ScrollGlide.accumulate(pending, added)).isEqualTo(expected);
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
