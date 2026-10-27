package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The two things the review desk asks inside itself, so no dialog ever replaces it (the window shows one card at a
 * time): the note for a retry, with "Lower temperature for this retry", and a yes/no bar used by Undo my edit and by
 * typing that is not saved. FX thread only.
 */
@Slf4j
final class ReviewInlinePanels {

    private static final double SPACING = 8;
    private static final int NOTE_ROWS = 3;

    /** What the person asked for in the note panel. */
    interface NoteAnswer {
        /**
         * Retries with the note.
         *
         * @param note the note, or {@code null} when the field was left empty
         * @param lowerTemperature whether the retry samples lower
         */
        void retry(@Nullable String note, boolean lowerTemperature);
    }

    /**
     * The words of one yes/no question.
     *
     * @param text the question
     * @param confirm the button that goes ahead
     * @param confirmTip its hover explanation
     * @param keep the button that leaves things as they are
     * @param keepTip its hover explanation
     */
    record Question(String text, MessageKey confirm, MessageKey confirmTip, MessageKey keep, MessageKey keepTip) {}

    private final Messages messages;
    private final VBox note = new VBox(SPACING);
    private final TextArea noteText = new TextArea();
    private final CheckBox lower;
    private final Button noteConfirm;
    private final HBox question = new HBox(SPACING);
    private final Label questionText = new Label();
    private final Button questionConfirm = new Button();
    private final Button questionKeep = new Button();
    private @Nullable Runnable onConfirm;
    private @Nullable ObservableBooleanValue askedWhile;
    private final ChangeListener<Boolean> stillHolds = (observed, was, holds) -> {
        if (!holds) {
            log.debug("the question no longer applies: closed");
            closeQuestion();
        }
    };

    ReviewInlinePanels(final Messages messages, final NoteAnswer answer) {
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(answer, "answer");
        lower = Tips.install(
                messages, new CheckBox(messages.get(MessageKey.REVIEW_NOTE_LOWER)), MessageKey.REVIEW_NOTE_LOWER_TIP);
        noteConfirm = button("review-note-confirm", MessageKey.REVIEW_NOTE_CONFIRM, MessageKey.REVIEW_NOTE_CONFIRM_TIP);
        buildNote(answer);
        buildQuestion();
    }

    /** The note panel, hidden until {@link #openNote()}. */
    VBox note() {
        return note;
    }

    /** The yes/no bar, hidden until {@link #ask}. */
    HBox question() {
        return question;
    }

    /** Whether the note panel is open, so the card makes its button the one Ctrl/⌘+Enter runs. */
    ReadOnlyBooleanProperty noteOpen() {
        return note.visibleProperty();
    }

    /** The note panel's confirming button. */
    Button noteConfirm() {
        return noteConfirm;
    }

    void openNote() {
        log.debug("note panel opened");
        noteText.clear();
        lower.setSelected(false);
        Visibility.show(note, true);
        noteText.requestFocus();
    }

    void closeNote() {
        if (note.isVisible()) {
            log.debug("note panel closed");
        }
        Visibility.show(note, false);
    }

    /**
     * Asks a question in the bar, replacing any question asked before.
     *
     * @param asked the words
     * @param confirmed what the confirming button does; the keeping one only closes the bar
     */
    void ask(final Question asked, final Runnable confirmed) {
        log.debug("question asked in the desk: confirm {}", asked.confirm());
        stopWatching();
        questionText.setText(asked.text());
        word(questionConfirm, asked.confirm(), asked.confirmTip());
        word(questionKeep, asked.keep(), asked.keepTip());
        onConfirm = confirmed;
        Visibility.show(question, true);
        questionKeep.requestFocus();
    }

    /**
     * Asks a question that only stands while {@code condition} holds — "your typing is not saved" goes away by itself
     * once the typing is saved or typed back, so it never stands beside a footer that says Saved.
     */
    void askWhile(final Question asked, final Runnable confirmed, final ObservableBooleanValue condition) {
        ask(asked, confirmed);
        askedWhile = condition;
        condition.addListener(stillHolds);
    }

    /** Whether a question is waiting for its answer. */
    boolean isAsking() {
        return question.isVisible();
    }

    void closeQuestion() {
        stopWatching();
        onConfirm = null;
        Visibility.show(question, false);
    }

    private void stopWatching() {
        if (askedWhile != null) {
            askedWhile.removeListener(stillHolds);
            askedWhile = null;
        }
    }

    private void buildNote(final NoteAnswer answer) {
        final Label label = new Label(messages.get(MessageKey.REVIEW_NOTE_LABEL));
        label.getStyleClass().add("stat-caption");
        noteText.setId("review-note-text");
        noteText.setPromptText(messages.get(MessageKey.REVIEW_NOTE_PROMPT));
        noteText.setPrefRowCount(NOTE_ROWS);
        noteText.setWrapText(true);
        Tips.install(messages, noteText, MessageKey.REVIEW_NOTE_PROMPT_TIP);
        lower.setId("review-note-lower");
        final Button cancel =
                button("review-note-cancel", MessageKey.REVIEW_NOTE_CANCEL, MessageKey.REVIEW_NOTE_CANCEL_TIP);
        cancel.getStyleClass().add("btn-ghost");
        cancel.setOnAction(event -> closeNote());
        noteConfirm.getStyleClass().add("btn-secondary");
        noteConfirm.setOnAction(event -> send(answer));
        final HBox buttons = new HBox(SPACING, cancel, noteConfirm);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        note.setId("review-note-panel");
        note.getStyleClass().add("inline-panel");
        note.getChildren().setAll(label, noteText, lower, buttons);
        // The field reads as shown or not with its panel, so focus and lookups never reach a hidden field.
        noteText.visibleProperty().bind(note.visibleProperty());
        noteText.managedProperty().bind(note.visibleProperty());
        Visibility.show(note, false);
    }

    // The note's text is book-adjacent wording the person typed, so only its length is logged above TRACE.
    private void send(final NoteAnswer answer) {
        final String text = noteText.getText() == null ? "" : noteText.getText().strip();
        log.debug("retry with a note of {} characters, lower temperature {}", text.length(), lower.isSelected());
        log.trace("retry note: {}", text);
        closeNote();
        answer.retry(text.isEmpty() ? null : text, lower.isSelected());
    }

    private void buildQuestion() {
        questionText.setWrapText(true);
        questionText.setMinHeight(Region.USE_PREF_SIZE);
        questionText.getStyleClass().add("banner-text");
        HBox.setHgrow(questionText, Priority.ALWAYS);
        questionText.setMaxWidth(Double.MAX_VALUE);
        word(questionConfirm, MessageKey.REVIEW_UNSAVED_DISCARD, MessageKey.REVIEW_UNSAVED_DISCARD_TIP);
        word(questionKeep, MessageKey.REVIEW_UNSAVED_KEEP, MessageKey.REVIEW_UNSAVED_KEEP_TIP);
        questionConfirm.setId("review-question-confirm");
        questionConfirm.getStyleClass().add("btn-secondary");
        questionConfirm.setOnAction(event -> {
            final Runnable confirmed = onConfirm;
            log.debug("question answered: go ahead");
            closeQuestion();
            if (confirmed != null) {
                confirmed.run();
            }
        });
        questionKeep.setId("review-question-keep");
        questionKeep.getStyleClass().add("btn-ghost");
        questionKeep.setOnAction(event -> {
            log.debug("question answered: keep");
            closeQuestion();
        });
        question.setId("review-question");
        question.getStyleClass().add("inline-panel");
        question.setAlignment(Pos.CENTER_LEFT);
        question.getChildren().setAll(questionText, questionKeep, questionConfirm);
        Visibility.show(question, false);
    }

    private Button button(final String id, final MessageKey label, final MessageKey tip) {
        final Button button = Tips.install(messages, new Button(messages.get(label)), tip);
        button.setId(id);
        return button;
    }

    private void word(final Button button, final MessageKey label, final MessageKey tip) {
        button.setText(messages.get(label));
        Tips.install(messages, button, tip);
    }
}
