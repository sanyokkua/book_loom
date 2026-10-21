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
 * The window's side of the activity tracker: the title-bar chip names other model work and stops it, leaving the
 * screen of a glossary scan asks whether to stop it or let it run on, a scan or the brief's suggestion keeps the window
 * from navigating, and leaving the names pass's screen asks "Model work is running".
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

        onFx(() -> shell.activate(ViewNames.IMPORT));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.NAMES_STYLE);
    }

    // IF any running work asked on every navigation, THEN a provider check would nag on its way out of Settings.
    @Test
    void leave_onlyWorkThatDoesNotNeedItsScreen_leavesAtOnce() {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.MODEL_LISTING, null));

        onFx(() -> shell.activate(ViewNames.IMPORT));

        assertThat(isShown("leave-card")).isFalse();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
    }

    private ActivityTracker.Handle beginNamesPass() {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() -> handle.set(injector.getInstance(ActivityTracker.class)
                .begin(ActivityKind.NAMES_PASS, () -> stopped.add("names"))));
        return Objects.requireNonNull(handle.get(), "handle");
    }

    private boolean isNamesPassHidden() {
        return injector.getInstance(ActivityTracker.class).running().stream()
                .anyMatch(activity -> activity.kind() == ActivityKind.NAMES_PASS && activity.isHidden());
    }

    // IF leaving the pass's screen were silent, THEN the pass would write into a screen nobody sees.
    @Test
    void navigate_whileNamesPassShown_asksModelWorkIsRunningAndStays() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        beginNamesPass();

        onFx(() -> shell.activate(ViewNames.EXPORT));

        assertThat(isShown("work-card")).isTrue();
        assertThat(textsUnder(required("work-card")))
                .contains("Model work is running", "Stay", "Continue in background");
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.NAMES_STYLE);
        assertThat(TooltipProbe.tipText(required("work-background"))).isNotBlank();
    }

    @Test
    void stay_chosen_keepsTheScreenAndTheWorkInView() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        beginNamesPass();
        onFx(() -> shell.activate(ViewNames.EXPORT));

        onFx(() -> ((Button) required("work-stay")).fire());

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.NAMES_STYLE);
        assertThat(isNamesPassHidden()).isFalse();
        assertThat(stopped).isEmpty();
    }

    // IF Continue in background did not hide the pass, THEN the person would land on Export behind its dialog.
    @Test
    void continueInBackground_chosen_opensExportAndHidesWork() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        beginNamesPass();
        onFx(() -> shell.activate(ViewNames.EXPORT));

        onFx(() -> ((Button) required("work-background")).fire());

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.EXPORT);
        assertThat(isNamesPassHidden()).isTrue();
        assertThat(stopped).isEmpty();
        assertThat(((Label) required("shell-activity-text")).getText()).isEqualTo("Preparing names · 0 %");
    }

    // IF short work let the person leave, THEN the brief's suggestion would land on a screen nobody looks at.
    @Test
    void navigate_whileBriefRuns_isRefusedWithoutDialog() {
        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.SUGGEST_STYLE, null));

        onFx(() -> shell.activate(ViewNames.IMPORT));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(isShown("work-card")).isFalse();
        assertThat(isShown("leave-card")).isFalse();
    }
}
