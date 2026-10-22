package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.StartEstimate;

/**
 * The lines under the quality choice that say what it costs: one line with a time when the model test measured one on
 * this computer, else the relative cost of the choices. A measured line says where it comes from on hover.
 */
final class BriefEstimateLines {

    private static final double SPACING = 3;
    private static final int LINES = 2;

    private final Messages messages;
    private final List<Label> lines;
    private final VBox node;

    BriefEstimateLines(final Messages messages, final BriefEstimate estimate) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.lines = List.of(line("brief-estimate-line-1"), line("brief-estimate-line-2"));
        this.node = new VBox(SPACING, lines.get(0), lines.get(1));
        node.setId("brief-estimate");
        estimate.estimate().addListener((observed, was, now) -> show(now));
        show(estimate.estimate().get());
    }

    VBox node() {
        return node;
    }

    private static Label line(final String id) {
        final Label line = new Label();
        line.setId(id);
        line.getStyleClass().add("hint");
        line.setWrapText(true);
        return line;
    }

    private void show(final StartEstimate now) {
        final List<String> texts = now.lines(messages);
        final boolean measured = now instanceof StartEstimate.Measured;
        for (int at = 0; at < LINES; at++) {
            final Label line = lines.get(at);
            final boolean shown = at < texts.size();
            line.setText(shown ? texts.get(at) : "");
            line.setVisible(shown);
            line.setManaged(shown);
            if (measured && shown) {
                Tips.install(messages, line, MessageKey.ESTIMATE_MEASURED_TIP);
            } else {
                line.setTooltip(null);
            }
        }
    }
}
