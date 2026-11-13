package ua.bookloom.ui.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TestDetail;

/**
 * The top of the Advanced features card: what the test is for, then one block per check saying what was asked, what
 * came back and the verdict, with each call's time and speed under it. Hidden for every other test.
 */
@Slf4j
final class AdvancedChecksPane {

    private static final double SPACING = 10;
    private static final double ROW_SPACING = 2;
    private static final double KEY_GAP = 8;

    private final Messages messages;
    private final VBox box = new VBox(SPACING);
    private final VBox rows = new VBox(SPACING);
    private List<TestDetail.CheckRow> shown = List.of();

    AdvancedChecksPane(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        box.setId("test-checks");
        box.getStyleClass().add("test-checks");
        final Label intro = Wrapped.wholeLines(new Label(messages.get(MessageKey.SETTINGS_TEST_ADVANCED_INTRO)));
        intro.setId("test-advanced-intro");
        intro.getStyleClass().add("hint");
        box.getChildren().addAll(intro, rows);
    }

    Node node() {
        return box;
    }

    /**
     * Shows the pane for the Advanced features test and its rows, or hides it for any other test.
     *
     * @param advanced whether the card shows the Advanced features test
     * @param checks the rows; empty while the test runs or when it ran no check
     */
    void show(final boolean advanced, final List<TestDetail.CheckRow> checks) {
        box.setVisible(advanced);
        box.setManaged(advanced);
        if (checks.equals(shown)) {
            return;
        }
        shown = List.copyOf(checks);
        log.debug("advanced check rows redrawn: {}", checks.size());
        final List<Node> blocks = new ArrayList<>();
        checks.forEach(row -> blocks.add(block(row)));
        rows.getChildren().setAll(blocks);
    }

    private Node block(final TestDetail.CheckRow row) {
        final Label name = new Label(row.name());
        name.getStyleClass().add("card-title");
        final VBox block = new VBox(
                ROW_SPACING,
                name,
                line(MessageKey.SETTINGS_TEST_ADVANCED_ASKED_LABEL, row.asked()),
                line(MessageKey.SETTINGS_TEST_ADVANCED_ANSWERED_LABEL, row.answered()),
                line(MessageKey.SETTINGS_TEST_ADVANCED_VERDICT_LABEL, row.verdict()));
        block.setId("test-check-" + row.token());
        block.getStyleClass().add("test-check");
        row.calls().forEach(call -> {
            final Label figure = new Label(call);
            figure.getStyleClass().add("muted");
            block.getChildren().add(figure);
        });
        return block;
    }

    private Node line(final MessageKey key, final String value) {
        final Label label = new Label(messages.get(key));
        label.getStyleClass().add("kv-key");
        label.setMinWidth(Region.USE_PREF_SIZE);
        final Label text = Wrapped.wholeLines(new Label(value));
        text.getStyleClass().add("kv-value");
        final HBox line = new HBox(KEY_GAP, label, text);
        line.getStyleClass().add("kv");
        return line;
    }
}
