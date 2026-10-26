package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * The progress dialog's footer answers a real mouse: a press at the centre of each footer button reaches that button,
 * the left-hand Continue in background included — the footer's feedback line once lay over it and took every click.
 */
class ProgressDialogFooterTest extends ProgressWorkTestBase {

    private ActivityTracker tracker() {
        return injector.getInstance(ActivityTracker.class);
    }

    // IF a node lay over a footer button, THEN the mouse would press that node and the button would never act.
    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = ActivityKind.class,
            names = {"NAMES_PASS", "SUGGEST_STYLE", "EXPORT", "GLOSSARY_SCAN"})
    void footerButton_pressedAtItsCentre_isWhatTheMouseHits(final ActivityKind kind) throws TimeoutException {
        final ActivityTracker.Handle work = begin(kind);
        onFx(() -> work.steps(new StepsPlanned(StepKind.REQUEST, 4, false)));
        onFx(() -> work.steps(new StepChanged(StepKind.REQUEST, "sample", 0, StepState.STARTED, null, null)));
        await(this::isDialogShown);
        final List<Button> buttons = ThemeTestSupport.onFx(() -> FooterHits.footerButtons(required("busy-card")));

        final List<String> missed = FooterHits.missed(this, scene, buttons);

        assertThat(buttons).as("the footer has buttons").isNotEmpty();
        assertThat(missed).as("footer buttons the mouse cannot reach").isEmpty();
    }

    // IF work that cannot be stopped said what Cancel writes, THEN the footer would promise a button that is not there
    // while the line above says the work cannot be stopped.
    @Test
    void footnote_workThatCannotBeStopped_isNotShown() throws TimeoutException {
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.IMPORT, null));
        await(this::isDialogShown);

        assertThat(ThemeTestSupport.onFx(() -> required("progress-footnote").isVisible())).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> required("progress-cancel").isVisible())).isFalse();
    }

    // IF Continue in background took no click, THEN the names pass could only be hidden with the keyboard.
    @Test
    void continueInBackground_clickedWithTheMouse_hidesTheDialog() throws TimeoutException {
        begin(ActivityKind.NAMES_PASS);
        await(this::isDialogShown);

        clickOn(required("progress-background"));

        await(() -> !isDialogShown());
        assertThat(cancelled).isEmpty();
        assertThat(tracker().running().stream().anyMatch(activity -> activity.kind() == ActivityKind.NAMES_PASS))
                .isTrue();
    }
}
