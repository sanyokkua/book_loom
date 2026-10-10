package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * The window's side of the activity tracker: the title-bar chip names other model work and stops it, and leaving the
 * screen of a glossary scan asks whether to stop it or let it run on, and a scan keeps the window from navigating.
 */
class ActivityShellTest extends ShellTestBase {

    private final List<String> stopped = new CopyOnWriteArrayList<>();

    private ActivityTracker.Handle beginScan() {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() -> handle.set(injector.getInstance(ActivityTracker.class)
                .begin(ActivityKind.GLOSSARY_SCAN, () -> stopped.add("scan"))));
        return Objects.requireNonNull(handle.get(), "handle");
    }

    private boolean isShown(final String id) {
        final Node node = scene.getRoot().lookup("#" + id);
        return node != null && node.isVisible() && node.getScene() != null;
    }

    // IF other model work were not named in the title bar, THEN a scan running behind another screen would be
    // invisible, and nothing would say why Start translation is held.
    @Test
    void chip_scanWithTwoRequests_namesItAndCountsTheRequests() {
        final ActivityTracker.Handle scan = beginScan();

        onFx(() -> scan.requests(2));

        assertThat(isShown("shell-activity")).isTrue();
        assertThat(((Label) required("shell-activity-text")).getText()).isEqualTo("Name scan · 2 requests");
        assertThat(TooltipProbe.tipText(required("shell-activity-stop"))).isNotBlank();
    }

    // IF Stop in the chip did not reach the work, THEN the person would have to find its screen to end it.
    @Test
    void chipStop_pressed_asksTheWorkToStop() {
        beginScan();

        onFx(() -> ((Button) required("shell-activity-stop")).fire());

        assertThat(stopped).containsExactly("scan");
    }

    // IF the chip showed with nothing else running, THEN it would repeat the run status beside it.
    @Test
    void chip_nothingButTheRun_isHidden() {
        final ActivityTracker.Handle scan = beginScan();

        onFx(scan::end);

        assertThat(isShown("shell-activity")).isFalse();
    }

    private boolean askLeaving(final AtomicBoolean left) {
        final AtomicBoolean admitted = new AtomicBoolean();
        onFx(() -> admitted.set(
                injector.getInstance(LeaveGuard.class).admits(ViewNames.NAMES_STYLE, () -> left.set(true))));
        return admitted.get();
    }

    // IF leaving the scan's screen were silent, THEN its result would land on a screen nobody looks at; the person is
    // asked, and Stop and leave stops the scan and then leaves. (The window refuses to navigate at all while a scan
    // runs, so this guards the one path that would reach it.)
    @Test
    void leaveGuard_whileAScanRuns_asksAndStopAndLeaveStopsItThenLeaves() {
        beginScan();
        final AtomicBoolean left = new AtomicBoolean();

        final boolean admitted = askLeaving(left);
        final boolean asked = isShown("leave-card");
        final String keepTip = TooltipProbe.tipText(required("leave-keep"));
        onFx(() -> ((Button) required("leave-stop")).fire());

        assertThat(admitted).isFalse();
        assertThat(asked).isTrue();
        assertThat(stopped).containsExactly("scan");
        assertThat(left).isTrue();
        assertThat(keepTip).isNotBlank();
    }

    // IF Leave and keep running stopped the scan anyway, THEN the person could never let it finish in the background.
    @Test
    void leaveGuard_keepRunning_leavesWithoutStopping() {
        beginScan();
        final AtomicBoolean left = new AtomicBoolean();
        askLeaving(left);

        onFx(() -> ((Button) required("leave-keep")).fire());

        assertThat(stopped).isEmpty();
        assertThat(left).isTrue();
    }

    // IF a scan did not hold the window, THEN the person could leave its screen with the result still to come.
    @Test
    void navigate_whileAScanRuns_staysOnTheScreen() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        beginScan();

        onFx(() -> shell.activate(ViewNames.STRUCTURE));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.NAMES_STYLE);
    }

    // IF any running work asked on every navigation, THEN a provider check would nag on its way out of Settings.
    @Test
    void leave_onlyWorkThatDoesNotNeedItsScreen_leavesAtOnce() {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.MODEL_LISTING, null));

        onFx(() -> shell.activate(ViewNames.STRUCTURE));

        assertThat(isShown("leave-card")).isFalse();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.STRUCTURE);
    }
}
