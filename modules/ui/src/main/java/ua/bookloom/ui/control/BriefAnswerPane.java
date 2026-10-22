package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.DecisionRow;
import ua.bookloom.ui.state.SampleLine;

/**
 * What each sample answered for one decided field, beside the place it was read and the quote from the book, so a
 * person can check that a value stands on the book's own words. An answer with no quote is shown and never used. Book
 * text is shown here and never logged.
 */
final class BriefAnswerPane extends VBox {

    private static final double SPACING = 8;
    private static final double GAP = 12;
    private static final double SAMPLE_SHARE = 24;
    private static final double ANSWER_SHARE = 22;
    private static final double QUOTE_SHARE = 54;
    private static final String PLACE_JOIN = " · ";

    private final Messages messages;
    private final GridPane grid = new GridPane();
    private final Label title = new Label();

    BriefAnswerPane(final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        setId("progress-answers");
        getStyleClass().add("progress-detail");
        title.getStyleClass().add("progress-heading");
        grid.setId("progress-sample-grid");
        grid.getStyleClass().add("progress-sample-grid");
        grid.setHgap(GAP);
        grid.setVgap(GAP);
        grid.getColumnConstraints().setAll(column(SAMPLE_SHARE), column(ANSWER_SHARE), column(QUOTE_SHARE));
        final Label note = new Label(messages.get(MessageKey.BRIEF_DECIDES_NOTE));
        note.setWrapText(true);
        note.getStyleClass().add("hint");
        getChildren().addAll(title, grid, note);
    }

    private static ColumnConstraints column(final double percent) {
        final ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(percent);
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    void show(final DecisionRow row) {
        title.setText(DecisionWords.nameOf(messages, row.group()));
        grid.getChildren().clear();
        header(MessageKey.BRIEF_DECIDES_COL_SAMPLE, 0);
        header(MessageKey.BRIEF_DECIDES_COL_ANSWER, 1);
        header(MessageKey.BRIEF_DECIDES_COL_QUOTE, 2);
        int at = 1;
        for (final SampleLine line : row.samplesRead()) {
            final String place = line.location().isEmpty() ? "" : PLACE_JOIN + line.location();
            grid.add(cell(line.sample() + place, false), 0, at);
            grid.add(cell(line.value(), !line.verified()), 1, at);
            grid.add(cell(quoteOf(line), !line.verified()), 2, at);
            at++;
        }
    }

    private String quoteOf(final SampleLine line) {
        return line.verified() ? line.quote() : messages.get(MessageKey.BRIEF_DECIDES_NO_QUOTE);
    }

    private void header(final MessageKey key, final int column) {
        final Label label = new Label(messages.get(key));
        label.getStyleClass().add("progress-heading");
        grid.add(label, column, 0);
    }

    private static Label cell(final String text, final boolean isNotUsed) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        if (isNotUsed) {
            label.getStyleClass().add("progress-sample-notused");
        }
        return label;
    }
}
