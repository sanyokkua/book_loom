package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ShellTestBase;

/**
 * The card that asks "continue the earlier translation or start over": its wording in both languages with the plural
 * of the segment count, which button runs which action, and that closing it unanswered runs neither.
 */
class ReplaceRunEarlierWorkTest extends ShellTestBase {

    private final AtomicInteger continued = new AtomicInteger();
    private final AtomicInteger startedOver = new AtomicInteger();

    private void ask(final int decided) {
        onFx(() -> injector.getInstance(ReplaceRunDialog.class)
                .askEarlierWork("Dracula.epub", decided, continued::incrementAndGet, startedOver::incrementAndGet));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + ReplaceRunDialog.EARLIER_CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 'Dracula.epub already has 1 translated segment from an earlier session. If you start over, that work is discarded.'",
        "7, 'Dracula.epub already has 7 translated segments from an earlier session. If you start over, that work is discarded.'",
    })
    void askEarlierWork_english_showsTheTitleThePluralBodyAndBothButtons(final int decided, final String body) {
        ask(decided);

        assertThat(textsUnder(required(ReplaceRunDialog.EARLIER_CARD_ID)))
                .contains("Continue the earlier translation?", body, "Continue", "Start over");
        assertThat(button(ReplaceRunDialog.CONTINUE_ID).getStyleClass()).contains("btn-primary");
        assertThat(button(ReplaceRunDialog.START_OVER_ID).getStyleClass()).contains("btn-danger-outline");
    }

    // The Ukrainian count takes the one, few and many forms; a wrong form reads as broken grammar.
    @ParameterizedTest
    @CsvSource({
        "1, '1 перекладений сегмент'",
        "3, '3 перекладені сегменти'",
        "5, '5 перекладених сегментів'",
        "21, '21 перекладений сегмент'",
    })
    void askEarlierWork_ukrainian_usesTheRightPluralFormAndLabels(final int decided, final String counted) {
        useLocale(Locale.forLanguageTag("uk"));

        ask(decided);

        assertThat(textsUnder(required(ReplaceRunDialog.EARLIER_CARD_ID)))
                .contains(
                        "Продовжити попередній переклад?",
                        "Dracula.epub уже має " + counted
                                + " із попереднього сеансу. Якщо почати заново, ця робота буде відкинута.",
                        "Продовжити",
                        "Почати заново");
    }

    @Test
    void continuePressed_runsOnlyTheContinueActionAndClosesTheCard() {
        ask(4);

        onFx(() -> button(ReplaceRunDialog.CONTINUE_ID).fire());

        assertThat(continued.get()).isEqualTo(1);
        assertThat(startedOver.get()).isZero();
        assertThat(isShowing()).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    @Test
    void startOverPressed_runsOnlyTheStartOverActionAndClosesTheCard() {
        ask(4);

        onFx(() -> button(ReplaceRunDialog.START_OVER_ID).fire());

        assertThat(startedOver.get()).isEqualTo(1);
        assertThat(continued.get()).isZero();
        assertThat(isShowing()).isFalse();
    }

    // IF Escape ran an action, THEN backing out would either discard the work or open a book nobody chose.
    @Test
    void escape_pressed_closesTheCardAndRunsNeitherAction() {
        ask(4);

        onFx(() -> Event.fireEvent(
                required(ReplaceRunDialog.EARLIER_CARD_ID),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(isShowing()).isFalse();
        assertThat(continued.get()).isZero();
        assertThat(startedOver.get()).isZero();
    }
}
