package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The "Open another book?" card: its words and buttons, which one is the default, how each answer is delivered, what
 * it shows while a run is stopped for the answer, and how it is painted.
 */
class OpenAnotherBookDialogTest extends ShellTestBase {

    private static final String PROJECT = "Earth Gravity · EN → UK";

    private final AtomicInteger openedNew = new AtomicInteger();
    private final AtomicInteger replaced = new AtomicInteger();

    private OpenAnotherBookDialog dialog() {
        return injector.getInstance(OpenAnotherBookDialog.class);
    }

    private void ask(final RunState state) {
        onFx(() -> dialog().ask(PROJECT, "velk.fb2", state, openedNew::incrementAndGet, replaced::incrementAndGet));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + OpenAnotherBookDialog.CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    @Test
    void ask_english_showsTheTitleTheBodyAndThreeButtons() {
        ask(RunState.IDLE);

        assertThat(textsUnder(required(OpenAnotherBookDialog.CARD_ID)))
                .contains(
                        "Open another book?",
                        "Earth Gravity · EN → UK is open. Opening velk.fb2 keeps Earth Gravity · EN → UK in"
                                + " Projects, saved.",
                        "Open as a new project",
                        "Replace and delete this translation",
                        "Cancel");
    }

    // IF the destructive choice were the default, THEN a stray Enter would delete a translation.
    @Test
    void ask_anyState_makesOpenAsANewProjectTheOnlyDefaultAndReplaceDestructive() {
        ask(RunState.IDLE);

        assertThat(button(OpenAnotherBookDialog.NEW_ID).isDefaultButton()).isTrue();
        assertThat(button(OpenAnotherBookDialog.NEW_ID).getStyleClass()).contains("btn-primary");
        assertThat(button(OpenAnotherBookDialog.REPLACE_ID).isDefaultButton()).isFalse();
        assertThat(button(OpenAnotherBookDialog.REPLACE_ID).getStyleClass()).contains("btn-danger-outline");
        assertThat(button(OpenAnotherBookDialog.CANCEL_ID).isCancelButton()).isTrue();
    }

    @Test
    void newPressed_runIsPaused_deliversOnlyTheNewAnswerAndClosesTheCard() {
        ask(RunState.PAUSED);

        onFx(() -> button(OpenAnotherBookDialog.NEW_ID).fire());

        assertThat(openedNew.get()).isEqualTo(1);
        assertThat(replaced.get()).isZero();
        assertThat(isShowing()).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    @Test
    void replacePressed_runIsStopped_deliversOnlyTheReplaceAnswerAndClosesTheCard() {
        ask(RunState.STOPPED);

        onFx(() -> button(OpenAnotherBookDialog.REPLACE_ID).fire());

        assertThat(replaced.get()).isEqualTo(1);
        assertThat(openedNew.get()).isZero();
        assertThat(isShowing()).isFalse();
    }

    // IF the card closed while the run was still stopping, THEN the person could act twice on a half-finished switch.
    @ParameterizedTest
    @CsvSource({"NEW", "REPLACE"})
    void answered_runIsRunning_staysShownWithStoppingTextAndAllButtonsDisabled(final String answer) {
        ask(RunState.RUNNING);

        onFx(() -> button("open-another-" + answer.toLowerCase(Locale.ROOT)).fire());

        assertThat(openedNew.get() + replaced.get()).isEqualTo(1);
        assertThat(isShowing()).isTrue();
        assertThat(textsUnder(required(OpenAnotherBookDialog.CARD_ID))).contains("Stopping the run…");
        assertThat(button(OpenAnotherBookDialog.NEW_ID).isDisabled()).isTrue();
        assertThat(button(OpenAnotherBookDialog.REPLACE_ID).isDisabled()).isTrue();
        assertThat(button(OpenAnotherBookDialog.CANCEL_ID).isDisabled()).isTrue();
    }

    @Test
    void cancelPressed_closesTheCardAndDeliversNoAnswer() {
        ask(RunState.RUNNING);

        onFx(() -> button(OpenAnotherBookDialog.CANCEL_ID).fire());

        assertThat(isShowing()).isFalse();
        assertThat(openedNew.get() + replaced.get()).isZero();
    }

    // IF Escape ran an action, THEN backing out would open or delete something nobody chose.
    @Test
    void escape_pressed_closesTheCardAndDeliversNoAnswer() {
        ask(RunState.PAUSED);

        onFx(() -> Event.fireEvent(
                required(OpenAnotherBookDialog.CARD_ID),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(isShowing()).isFalse();
        assertThat(openedNew.get() + replaced.get()).isZero();
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

    @Test
    void ask_ukrainian_showsTheUkrainianWords() {
        useLocale(Locale.forLanguageTag("uk"));

        ask(RunState.IDLE);

        assertThat(textsUnder(required(OpenAnotherBookDialog.CARD_ID)))
                .contains(
                        "Відкрити іншу книгу?",
                        "Відкрити як новий проєкт",
                        "Замінити й видалити цей переклад",
                        "Скасувати");
    }

    @ParameterizedTest
    @CsvSource({
        "LIGHT, #ffffff, #ddd5c8",
        "DARK, #33424a, #48585f",
    })
    void card_paintsWithTheSurfaceAndBorderRolesOfTheBlockInForce(
            final ThemeMode mode, final String surface, final String border) {
        onFx(() -> themeController.setMode(mode));
        ask(RunState.IDLE);

        final Region card = (Region) required(OpenAnotherBookDialog.CARD_ID);
        final Paint fill = card.getBackground().getFills().get(0).getFill();
        final Paint stroke = card.getBorder().getStrokes().get(0).getTopStroke();

        ThemeTestSupport.assertSameColour(fill, surface, "open-another card background under " + mode);
        ThemeTestSupport.assertSameColour(stroke, border, "open-another card border under " + mode);
    }
}
