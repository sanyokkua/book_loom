package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ShellTestBase;

/** The retry-with-note card: what it says, what Retry hands back, and that Cancel hands back nothing. */
class RetryWithNoteDialogTest extends ShellTestBase {

    private final List<RetryWithNoteDialog.Choice> chosen = new ArrayList<>();

    private void ask() {
        onFx(() -> injector.getInstance(RetryWithNoteDialog.class).ask("ch5 · p12", chosen::add));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + RetryWithNoteDialog.CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    @Test
    void ask_segment_showsTitleLocatorFieldsAndBothButtons() {
        ask();

        assertThat(textsUnder(required(RetryWithNoteDialog.CARD_ID)))
                .contains(
                        "Retry with note",
                        "ch5 · p12",
                        "Guidance for the model (optional)",
                        "Lower temperature for this retry",
                        "Cancel",
                        "↻ Retry");
    }

    @Test
    void retry_notTypedAndCheckBoxOff_handsBackNoNoteAndFalse() {
        ask();

        onFx(() -> ((Button) required(RetryWithNoteDialog.RETRY_ID)).fire());

        assertThat(chosen).containsExactly(new RetryWithNoteDialog.Choice(null, false));
        assertThat(isShowing()).isFalse();
    }

    @Test
    void retry_noteTypedAndCheckBoxOn_handsBackBoth() {
        ask();
        onFx(() -> ((TextArea) required(RetryWithNoteDialog.NOTE_ID)).setText("keep it more formal"));
        onFx(() -> ((CheckBox) required(RetryWithNoteDialog.LOWER_ID)).setSelected(true));

        onFx(() -> ((Button) required(RetryWithNoteDialog.RETRY_ID)).fire());

        assertThat(chosen).containsExactly(new RetryWithNoteDialog.Choice("keep it more formal", true));
    }

    @Test
    void cancel_pressed_handsBackNothingAndHidesTheCard() {
        ask();
        onFx(() -> ((TextArea) required(RetryWithNoteDialog.NOTE_ID)).setText("ignored"));

        onFx(() -> ((Button) required(RetryWithNoteDialog.CANCEL_ID)).fire());

        assertThat(chosen).isEmpty();
        assertThat(isShowing()).isFalse();
    }
}
