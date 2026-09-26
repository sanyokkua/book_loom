package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

/**
 * The modal host's contract for a dialog other than About: one that must be answered through its own controls is not
 * dismissed by a click on the dimmed area, while Escape still closes it.
 */
class ModalHostTest extends ShellTestBase {

    private ModalHost host() {
        return injector.getInstance(ModalHost.class);
    }

    private static Node answerOnlyCard() {
        final StackPane card = new StackPane();
        card.setId("answer-only-card");
        card.setPrefSize(120, 60);
        return card;
    }

    private void clickScrim() {
        final Node scrim = required("shell-scrim");
        final MouseEvent click = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
        scrim.fireEvent(click);
    }

    @Test
    void show_withoutOutsideDismissal_ignoresAClickOnTheScrim() {
        // IF the flag were ignored, THEN a dialog that demands an answer could be lost by a stray click.
        onFx(() -> host().show(answerOnlyCard(), false));

        onFx(this::clickScrim);

        assertThat(scene.getRoot().lookup("#answer-only-card")).isNotNull();
        assertThat(required("shell-scrim").isVisible()).isTrue();
    }

    @Test
    void show_withoutOutsideDismissal_stillClosesOnEscape() {
        // IF Escape were tied to the flag, THEN a person could not back out of the dialog from the keyboard.
        onFx(() -> host().show(answerOnlyCard(), false));

        onFx(() -> Event.fireEvent(
                required("answer-only-card"),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(scene.getRoot().lookup("#answer-only-card")).isNull();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    @Test
    void show_aSecondCardWhileOneIsShown_replacesIt() {
        // IF a second card stacked on the first, THEN two dialogs would sit over one scrim.
        onFx(() -> host().show(answerOnlyCard(), true));
        onFx(() -> {
            final Node second = answerOnlyCard();
            second.setId("second-card");
            host().show(second, true);
        });

        assertThat(scene.getRoot().lookup("#answer-only-card")).isNull();
        assertThat(scene.getRoot().lookup("#second-card")).isNotNull();
    }
}
