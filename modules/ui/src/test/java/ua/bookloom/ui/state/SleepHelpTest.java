package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;

/** The sleep help line: closable once for each project, and worded by whether keep-awake is on. */
class SleepHelpTest extends FxTestBase {

    private final MemorySettings settings = new MemorySettings();
    private final CurrentProject project = new CurrentProject();
    private final KeepAwakeGate gate = new KeepAwakeGate();
    private SleepHelp help;

    @BeforeEach
    void build() {
        help = new SleepHelp(settings, Runnable::run, Runnable::run, project, gate);
    }

    private void open(final String projectId) {
        onFx(() -> {
            OpenBookForTest.open(project, projectId, "en", "uk");
            return null;
        });
    }

    // IF a first-time project did not show the line, THEN nobody would learn that a closed lid still sleeps.
    @Test
    void shown_projectNotClosedBefore_isTrueAndKeepAwakeFollowsItsDefault() {
        open("p1");

        assertThat(onFx(() -> help.shown().get())).isTrue();
        assertThat(onFx(() -> help.keepAwake().get())).isTrue();
    }

    // IF closing were not stored for the project, THEN the line would come back at every start of the app.
    @Test
    void close_pressed_hidesTheLineAndStoresItForThatProject() {
        open("p1");

        onFx(() -> {
            help.close();
            return null;
        });

        assertThat(onFx(() -> help.shown().get())).isFalse();
        assertThat(settings.get(AppSetting.sleepLineClosed("p1")).data()).contains(true);
    }

    // IF closing hid the line for every project, THEN a new book would never hear about sleep.
    @Test
    void shown_anotherProjectAfterClosing_isTrueAgain() {
        open("p1");
        onFx(() -> {
            help.close();
            return null;
        });

        open("p2");

        assertThat(onFx(() -> help.shown().get())).isTrue();
    }

    // IF the off variant were not chosen from the person's choice, THEN the line would promise an awake computer it
    // won't keep.
    @Test
    void keepAwake_gateTurnedOff_lineSaysComputerMaySleep() {
        open("p1");

        onFx(() -> {
            gate.set(false);
            return null;
        });

        assertThat(onFx(() -> help.keepAwake().get())).isFalse();
    }

    @Test
    void keepAwake_gateSeededOffBeforeBuilding_isFalse() {
        gate.seed(false);
        help = new SleepHelp(settings, Runnable::run, Runnable::run, project, gate);

        assertThat(onFx(() -> help.keepAwake().get())).isFalse();
    }

    // IF the line read the choice only when built, THEN a seed that arrives later (startup reads the store after the
    // window's objects exist) would leave the line promising an awake computer.
    @Test
    void keepAwake_gateSeededOffAfterBuilding_nextProjectLineIsFalse() {
        gate.seed(false);

        open("p1");

        assertThat(onFx(() -> help.keepAwake().get())).isFalse();
    }
}
