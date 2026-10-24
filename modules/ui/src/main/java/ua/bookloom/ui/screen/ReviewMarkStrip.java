package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.MarkStatus;
import ua.bookloom.ui.state.ReviewEditor;

/**
 * The line under the translation editor that says what the formatting marks are for and, while one is missing or
 * doubled, which one and what to do; while the text is blank it says Save waits for words. It follows the editor's status and holds no state of its own; nothing is logged
 * because it is rewritten on every keystroke.
 */
final class ReviewMarkStrip extends Label {

    private static final String PROBLEM = "mark-strip-problem";

    // The narrowest editor pane at the smallest window; a width the parents probe with (none, or near zero) wraps here.
    private static final double NOMINAL_WIDTH = 240;

    private final Messages messages;

    ReviewMarkStrip(final ReviewEditor editor, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(editor, "editor");
        setId("review-mark-strip");
        getStyleClass().add("mark-strip");
        setWrapText(true);
        setMaxWidth(Double.MAX_VALUE);
        textProperty()
                .bind(Bindings.createStringBinding(
                        () -> editor.blank().getValue()
                                ? messages.get(MessageKey.REVIEW_EDIT_BLANK)
                                : wording(
                                        editor.sourceMarks().getValue(),
                                        editor.marks().getValue()),
                        editor.sourceMarks(),
                        editor.marks(),
                        editor.blank()));
        editor.marks()
                .addListener(
                        (observed, was, now) -> showProblem(now, editor.blank().getValue()));
        editor.blank()
                .addListener((observed, was, now) -> showProblem(editor.marks().getValue(), now));
        showProblem(editor.marks().getValue(), editor.blank().getValue());
    }

    private void showProblem(final MarkStatus status, final boolean isBlank) {
        if (status.isComplete() && !isBlank) {
            getStyleClass().remove(PROBLEM);
        } else if (!getStyleClass().contains(PROBLEM)) {
            getStyleClass().add(PROBLEM);
        }
    }

    private String wording(final List<String> source, final MarkStatus status) {
        if (source.isEmpty()) {
            return messages.get(MessageKey.REVIEW_MARKS_NONE);
        }
        if (status.isComplete()) {
            return messages.get(MessageKey.REVIEW_MARKS_OK, String.join(" ", source));
        }
        final StringBuilder text = new StringBuilder();
        if (!status.missing().isEmpty()) {
            text.append(messages.get(MessageKey.REVIEW_MARKS_MISSING, String.join(" ", status.missing())));
        }
        if (!status.doubled().isEmpty()) {
            text.append(text.isEmpty() ? "" : " ")
                    .append(messages.get(MessageKey.REVIEW_MARKS_DOUBLED, String.join(" ", status.doubled())));
        }
        return text.toString();
    }

    // A wrapped label asked for its height without a width answers one line and is then cut with an ellipsis; asked
    // at a near-zero width it answers a column of single words. Both are answered at a real width instead.
    @Override
    protected double computePrefHeight(final double width) {
        return super.computePrefHeight(Math.max(width, NOMINAL_WIDTH));
    }

    @Override
    protected double computeMinHeight(final double width) {
        return computePrefHeight(width);
    }
}
