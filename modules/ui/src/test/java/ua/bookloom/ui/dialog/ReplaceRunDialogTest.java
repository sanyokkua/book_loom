package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.theme.ThemeMode;

/** The replace-run card: what it says for an active and a stopped run, how it is answered, and how it is painted. */
class ReplaceRunDialogTest extends ShellTestBase {

    private final AtomicInteger confirmed = new AtomicInteger();

    private ReplaceRunDialog dialog() {
        return injector.getInstance(ReplaceRunDialog.class);
    }

    private void ask(final RunState state) {
        onFx(() -> dialog().ask("Frankenstein.epub", "Dracula.epub", state, confirmed::incrementAndGet));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + ReplaceRunDialog.CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    @ParameterizedTest
    @CsvSource({
        "RUNNING, Frankenstein.epub is still being translated. Importing Dracula.epub stops that run and discards its progress.",
        "PAUSED, Frankenstein.epub is still being translated. Importing Dracula.epub stops that run and discards its progress.",
        "STOPPING, Frankenstein.epub is still being translated. Importing Dracula.epub stops that run and discards its progress.",
        "STOPPED, Frankenstein.epub was stopped and can be resumed. Importing Dracula.epub discards its progress.",
    })
    void ask_runState_showsTheMatchingTextAndBothButtons(final RunState state, final String expected) {
        ask(state);

        assertThat(textsUnder(required(ReplaceRunDialog.CARD_ID)))
                .contains("Replace the current translation?", expected, "Keep translation", "Discard and import");
        assertThat(button(ReplaceRunDialog.KEEP_ID).getStyleClass()).contains("btn-secondary");
        assertThat(button(ReplaceRunDialog.CONFIRM_ID).getStyleClass()).contains("btn-danger-outline");
    }

    @Test
    void keep_pressed_hidesTheHostAndNeverRunsTheConfirmAction() {
        ask(RunState.RUNNING);

        onFx(() -> button(ReplaceRunDialog.KEEP_ID).fire());

        assertThat(isShowing()).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
        assertThat(confirmed.get()).isZero();
    }

    @Test
    void confirm_activeRun_runsTheActionOnceAndShowsStoppingWithBothButtonsDisabled() {
        ask(RunState.RUNNING);

        onFx(() -> button(ReplaceRunDialog.CONFIRM_ID).fire());

        assertThat(confirmed.get()).isEqualTo(1);
        assertThat(isShowing()).isTrue();
        assertThat(textsUnder(required(ReplaceRunDialog.CARD_ID))).contains("Stopping the run…");
        assertThat(button(ReplaceRunDialog.KEEP_ID).isDisabled()).isTrue();
        assertThat(button(ReplaceRunDialog.CONFIRM_ID).isDisabled()).isTrue();
    }

    @Test
    void confirm_stoppedRun_runsTheActionOnceWithoutAStoppingLine() {
        ask(RunState.STOPPED);

        onFx(() -> button(ReplaceRunDialog.CONFIRM_ID).fire());

        assertThat(confirmed.get()).isEqualTo(1);
        assertThat(scene.getRoot().lookup("#" + ReplaceRunDialog.STOPPING_ID)).isNull();
    }

    @Test
    void dismiss_cardShown_hidesTheHost() {
        ask(RunState.RUNNING);

        onFx(() -> dialog().dismiss());

        assertThat(isShowing()).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    @Test
    void dismiss_nothingShown_isIgnored() {
        onFx(() -> dialog().dismiss());

        assertThat(isShowing()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "LIGHT, #ffffff, #ddd5c8",
        "DARK, #33424a, #48585f",
    })
    void card_paintsWithTheSurfaceAndBorderRolesOfTheBlockInForce(
            final ThemeMode mode, final String surface, final String border) {
        onFx(() -> themeController.setMode(mode));
        ask(RunState.RUNNING);

        final Region card = (Region) required(ReplaceRunDialog.CARD_ID);
        final Paint fill = card.getBackground().getFills().get(0).getFill();
        final Paint stroke = card.getBorder().getStrokes().get(0).getTopStroke();

        ThemeTestSupport.assertSameColour(fill, surface, "replace-run card background under " + mode);
        ThemeTestSupport.assertSameColour(stroke, border, "replace-run card border under " + mode);
    }

    // IF a button were clipped at the narrowest content width, THEN its label would end in an ellipsis.
    @Test
    void card_ukrainianAtTheMinimumWidth_hasNoClippedButtonLabel() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        ask(RunState.RUNNING);

        assertThat(button(ReplaceRunDialog.KEEP_ID).getText()).isEqualTo("Залишити переклад");
        assertThat(button(ReplaceRunDialog.CONFIRM_ID).getText()).isEqualTo("Відкинути й імпортувати");
        assertThat(shownTexts(ReplaceRunDialog.KEEP_ID)).noneMatch(text -> text.endsWith("…"));
        assertThat(shownTexts(ReplaceRunDialog.CONFIRM_ID)).noneMatch(text -> text.endsWith("…"));
    }

    private List<String> shownTexts(final String buttonId) {
        return required(buttonId).lookupAll(".text").stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .toList();
    }
}
