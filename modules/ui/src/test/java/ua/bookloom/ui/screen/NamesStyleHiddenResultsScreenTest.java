package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * A names pass that ends in the background opens nothing by itself: a toast says it finished and offers "Review the
 * results", and only pressing that opens the Results view, once. A pass in the foreground opens it on its own.
 */
class NamesStyleHiddenResultsScreenTest extends NamesStyleAutoScreenTestBase {

    private void endTheHiddenPass() throws Exception {
        showScreen();
        passWillSucceed();
        namesPass.hold();
        pressAuto();
        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(this::isBusyCardShown);
        onFx(() -> button("progress-background").fire());
        awaitFx(() -> !isBusyCardShown());

        namesPass.release();

        awaitFx(() -> !toastActions().isEmpty());
    }

    private List<Node> toastActions() {
        return ThemeTestSupport.onFx(
                () -> List.copyOf(required("shell-toast-host").lookupAll(".toast-action")));
    }

    private long resultsCards() {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll("#results-card").stream()
                .filter(node -> node.getScene() != null)
                .count());
    }

    // IF a hidden pass opened its dialog on its own, THEN it would jump over whatever the person is doing.
    @Test
    void hiddenPass_ends_noResultsOpenAndAToastOffersToReviewTheResults() throws Exception {
        endTheHiddenPass();

        assertThat(resultsCards()).isZero();
        assertThat(isCardShown("results-card")).isFalse();
        assertThat(((Button) toastActions().getFirst()).getText()).isEqualTo("Review the results");
    }

    // IF the toast action opened the view twice, THEN Undo all would be asked of two views on one result.
    @Test
    void toastAction_pressed_opensTheResultsOnce() throws Exception {
        endTheHiddenPass();

        onFx(() -> ((Button) toastActions().getFirst()).fire());

        awaitFx(() -> isCardShown("results-card"));
        assertThat(resultsCards()).isEqualTo(1);
        assertThat(labelText("progress-subtitle")).startsWith("2 added · 2 removed · 1 changed");
    }

    // IF the foreground pass waited for a toast, THEN the person watching it would have to hunt for the results.
    @Test
    void visiblePass_ends_opensTheResultsAtOnceAndRaisesNoReviewToast() throws Exception {
        showScreen();

        runToTheResults();

        assertThat(resultsCards()).isEqualTo(1);
        assertThat(toastActions()).isEmpty();
    }

    // IF the message waited for Names & style to be built again, THEN a pass that ended while the person worked
    // elsewhere would finish unannounced, and its results would never be offered.
    @Test
    void hiddenPass_endsWhileAnotherScreenShows_offersTheResultsAtOnceAndTheActionComesBack() throws Exception {
        showScreen();
        passWillSucceed();
        namesPass.hold();
        pressAuto();
        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(this::isBusyCardShown);
        onFx(() -> button("progress-background").fire());
        awaitFx(() -> !isBusyCardShown());
        onFx(() -> shell.activate(ViewNames.IMPORT));
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);

        namesPass.release();

        awaitFx(() -> !toastActions().isEmpty());
        assertThat(resultsCards()).isZero();
        onFx(() -> ((Button) toastActions().getFirst()).fire());
        awaitFx(() -> isCardShown("results-card"));
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.NAMES_STYLE);
        assertThat(resultsCards()).isEqualTo(1);
    }
}
