package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.state.WorkVisibility;

/** Enter, Escape and the queue of dialogs nobody asked for, as the host applies them to every card. */
class ModalHostKeyQueueTest extends ShellTestBase {

    private final AtomicInteger confirmed = new AtomicInteger();

    private ModalHost host() {
        return injector.getInstance(ModalHost.class);
    }

    private ModalCard card(final String id, final Node body) {
        final ModalCard card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(id);
        card.setContent(body);
        final ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(ok);
        final Button button = (Button) card.lookupButton(ok);
        button.setDefaultButton(true);
        button.setOnAction(event -> confirmed.incrementAndGet());
        return card;
    }

    private void press(final Node target, final KeyCode code, final boolean ctrl) {
        onFx(() ->
                Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, false, false)));
    }

    @Test
    void enter_onAQuestionCard_runsTheDefaultButton() {
        final ModalCard question = card("question", new Button("Other"));
        onFx(() -> host().show(question, false));

        press(question, KeyCode.ENTER, false);

        assertThat(confirmed.get()).isEqualTo(1);
    }

    @Test
    void enter_inATextArea_addsNoActivationButCtrlEnterRunsTheDefault() {
        final TextArea note = new TextArea();
        final ModalCard form = card("form", note);
        onFx(() -> {
            host().show(form, false);
            note.requestFocus();
        });

        press(note, KeyCode.ENTER, false);
        final int afterPlainEnter = confirmed.get();
        press(note, KeyCode.ENTER, true);

        assertThat(afterPlainEnter).isEqualTo(0);
        assertThat(confirmed.get()).isEqualTo(1);
    }

    @Test
    void escape_onAQuestionCard_closesIt() {
        final ModalCard question = card("question", new Button("Other"));
        onFx(() -> host().show(question, false));

        press(question, KeyCode.ESCAPE, false);

        assertThat(host().isShowing()).isFalse();
    }

    @Test
    void escape_onACardWithAnEscapeAction_runsThatActionInsteadOfClosing() {
        final AtomicInteger hidden = new AtomicInteger();
        final ModalCard progress = card("progress", new Button("Other"));
        progress.setOnEscape(() -> {
            hidden.incrementAndGet();
            host().hide();
        });
        onFx(() -> host().show(progress, false, false));

        press(progress, KeyCode.ESCAPE, false);

        assertThat(hidden.get()).isEqualTo(1);
        assertThat(host().isShowing()).isFalse();
    }

    @Test
    void escape_onACardThatMustBeAnswered_isIgnored() {
        final ModalCard must = card("must", new Button("Other"));
        onFx(() -> host().show(must, false, false));

        press(must, KeyCode.ESCAPE, false);

        assertThat(host().isShowing()).isTrue();
    }

    @Test
    void showWhenFree_nothingOpenAndNoWork_opensAtOnce() {
        onFx(() -> host().showWhenFree(card("late", new Button("Other"))));

        assertThat(scene.getRoot().lookup("#late")).isNotNull();
    }

    @Test
    void showWhenFree_whileACardIsOpen_opensOnlyAfterItCloses() {
        final ModalCard open = card("open", new Button("Other"));
        final ModalCard late = card("late", new Button("Other"));
        onFx(() -> {
            host().show(open, false);
            host().showWhenFree(late);
        });
        final boolean shownEarly = scene.getRoot().lookup("#late") != null;

        onFx(() -> host().hide());
        onFx(() -> {});

        assertThat(shownEarly).isFalse();
        assertThat(scene.getRoot().lookup("#late")).isNotNull();
    }

    @Test
    void showWhenFree_whileModelWorkIsVisible_opensWhenTheWorkLeaves() {
        final WorkVisibility work = injector.getInstance(WorkVisibility.class);
        final ModalCard late = card("late", new Button("Other"));
        onFx(() -> {
            work.visibleProperty().set(true);
            host().showWhenFree(late);
        });
        final boolean shownEarly = scene.getRoot().lookup("#late") != null;

        onFx(() -> work.visibleProperty().set(false));
        onFx(() -> {});

        assertThat(shownEarly).isFalse();
        assertThat(scene.getRoot().lookup("#late")).isNotNull();
    }
}
