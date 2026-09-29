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
    // pending pause, a second stop, or a start beside a run that can still be resumed.
    @ParameterizedTest(name = "{0} (preparing {1}, pending remain {2})")
    @CsvSource({
        "IDLE,      false, false, ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
        "IDLE,      true,  false, DISABLED, HIDDEN,   HIDDEN,   HIDDEN",
        "RUNNING,   false, false, HIDDEN,   ENABLED,  HIDDEN,   ENABLED",
        "PAUSING,   false, false, HIDDEN,   DISABLED, HIDDEN,   ENABLED",
        "PAUSED,    false, false, HIDDEN,   HIDDEN,   ENABLED,  ENABLED",
        "PAUSED,    false, true,  HIDDEN,   HIDDEN,   ENABLED,  ENABLED",
        "STOPPING,  false, false, HIDDEN,   HIDDEN,   HIDDEN,   DISABLED",
        "STOPPED,   false, false, HIDDEN,   HIDDEN,   ENABLED,  HIDDEN",
        "STOPPED,   true,  false, HIDDEN,   HIDDEN,   DISABLED, HIDDEN",
        "COMPLETED, false, false, HIDDEN,   HIDDEN,   HIDDEN,   HIDDEN",
        "COMPLETED, false, true,  ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
        "COMPLETED, true,  true,  DISABLED, HIDDEN,   HIDDEN,   HIDDEN",
        "FAILED,    false, false, ENABLED,  HIDDEN,   HIDDEN,   HIDDEN",
    })
    void of_runStateAndPreparing_givesTheStatedControls(
            final RunState state,
            final boolean preparing,
            final boolean pendingRemain,
            final ControlState start,
            final ControlState pause,
            final ControlState resume,
            final ControlState stop) {
        assertThat(Controls.of(state, preparing, pendingRemain)).isEqualTo(new Controls(start, pause, resume, stop));
    }

    // IF preparing changed a control of a run already under way, THEN pause and stop would flicker while the run
    // that has just begun is still being set up.
    @ParameterizedTest
    @CsvSource({"RUNNING", "PAUSING", "PAUSED", "STOPPING"})
    void of_activeRun_ignoresPreparing(final RunState state) {
        assertThat(Controls.of(state, true, false)).isEqualTo(Controls.of(state, false, false));
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
