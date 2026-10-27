package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * Scan, Review and Translate on either card run as two steps in the progress dialog: the action, worded as its
 * activity, then "Write the results". They are short work, so there is no Continue in background.
 */
class NamesStyleActionStepsScreenTest extends TranslatingScreenTestBase {

    @AfterEach
    void releaseTheModel() {
        glossary.releaseModelCalls();
    }

    private void openScreen() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of()));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> optional("names-style-scan") != null);
    }

    private boolean isDialogShown() {
        final Node host = scene.getRoot().lookup("#shell-busy-host");
        return host != null && host.isVisible() && host.getOpacity() > 0;
    }

    private String textOf(final String id, final String styleClass) {
        return ThemeTestSupport.onFx(() -> ((Label) required(id).lookup(styleClass)).getText());
    }

    // IF the action showed no steps, THEN a long scan would be a bar with nothing to read beside it.
    @Test
    void scanPress_glossary_runsTwoStepsTheActionThenWrite() throws Exception {
        openScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-scan").fire());
        awaitFx(this::isDialogShown);

        awaitFx(() -> optional("progress-step-1") != null);
        assertThat(textOf("progress-step-0", ".progress-step-name")).isEqualTo("Name scan");
        assertThat(textOf("progress-step-1", ".progress-step-name")).isEqualTo("Write the results");
        assertThat(textOf("progress-step-1", ".progress-step-result")).isEqualTo("Not started");
        assertThat(optional("progress-step-2")).isNull();
    }

    // IF short work offered the background, THEN a scan could end with nobody looking; Esc cancels it.
    @Test
    void scanPress_glossary_offersNoContinueInBackgroundButCancel() throws Exception {
        openScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-scan").fire());
        awaitFx(this::isDialogShown);

        assertThat(optional("progress-background")).isNull();
        assertThat(button("progress-cancel").isDisabled()).isFalse();
    }

    // IF Review kept the Scan's first step, THEN its dialog would name the wrong action.
    @Test
    void reviewPress_glossary_nameTheFirstStepAsTheReview() throws Exception {
        openScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(this::isDialogShown);

        awaitFx(() -> optional("progress-step-1") != null);
        assertThat(textOf("progress-step-0", ".progress-step-name")).isEqualTo("Name review");
        assertThat(textOf("progress-step-1", ".progress-step-name")).isEqualTo("Write the results");
    }
}
