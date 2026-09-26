package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The control table of the translating dashboard, one row per run state. It is written out here by hand from the
 * requirements, never derived from the code under test.
 */
class ControlsTest {

    // IF a state offered the wrong control, THEN a person could press one that the run cannot honour: resume beside a
    // pending pause, a second stop, or a start beside a run that is still going.
    @ParameterizedTest(name = "{0} (preparing {1})")
    @CsvSource({
        "IDLE,      false, ENABLED,  HIDDEN,   HIDDEN,   HIDDEN,   HIDDEN",
        "IDLE,      true,  DISABLED, HIDDEN,   HIDDEN,   HIDDEN,   HIDDEN",
        "RUNNING,   false, HIDDEN,   HIDDEN,   ENABLED,  HIDDEN,   ENABLED",
        "PAUSING,   false, HIDDEN,   HIDDEN,   DISABLED, HIDDEN,   ENABLED",
        "PAUSED,    false, HIDDEN,   HIDDEN,   HIDDEN,   ENABLED,  ENABLED",
        "STOPPING,  false, HIDDEN,   HIDDEN,   HIDDEN,   HIDDEN,   DISABLED",
        "STOPPED,   false, HIDDEN,   ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
        "STOPPED,   true,  HIDDEN,   DISABLED, HIDDEN,   HIDDEN,   HIDDEN",
        "COMPLETED, false, HIDDEN,   ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
        "COMPLETED, true,  HIDDEN,   DISABLED, HIDDEN,   HIDDEN,   HIDDEN",
        "FAILED,    false, HIDDEN,   ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
    })
    void of_runStateAndPreparing_givesTheStatedControls(
            final RunState state,
            final boolean preparing,
            final ControlState start,
            final ControlState newRun,
            final ControlState pause,
            final ControlState resume,
            final ControlState stop) {
        assertThat(Controls.of(state, preparing)).isEqualTo(new Controls(start, newRun, pause, resume, stop));
    }

    // IF preparing changed a control of a run already under way, THEN pause and stop would flicker while the run
    // that has just begun is still being set up.
    @ParameterizedTest
    @CsvSource({"RUNNING", "PAUSING", "PAUSED", "STOPPING"})
    void of_activeRun_ignoresPreparing(final RunState state) {
        assertThat(Controls.of(state, true)).isEqualTo(Controls.of(state, false));
    }

    // IF a hidden control counted as available, THEN a screen that only hides a button would still let it be pressed.
    @ParameterizedTest
    @CsvSource({"HIDDEN, false, false", "DISABLED, true, false", "ENABLED, true, true"})
    void controlState_eachValue_reportsShownAndEnabled(
            final ControlState state, final boolean shown, final boolean enabled) {
        assertThat(state.isShown()).isEqualTo(shown);
        assertThat(state.isEnabled()).isEqualTo(enabled);
    }
}
