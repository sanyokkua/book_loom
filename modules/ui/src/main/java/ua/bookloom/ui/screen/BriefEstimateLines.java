package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.StartEstimate;

/**
 * The line under the quality choice that says what it costs: a time measured on this model, a range marked as an
 * estimate with where its speed was measured, or nothing when nothing was measured. The line says how it was worked
 * out on hover.
 */
final class BriefEstimateLines {

    private final Messages messages;
    private final Label line;
    private final VBox node;

    BriefEstimateLines(final Messages messages, final BriefEstimate estimate) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.line = Wrapped.wholeLines(new Label());
        line.setId("brief-estimate-line-1");
        line.getStyleClass().add("hint");
        this.node = new VBox(line);
        node.setId("brief-estimate");
        estimate.estimate().addListener((observed, was, now) -> show(now));
        show(estimate.estimate().get());
    }

    VBox node() {
        return node;
    }

    private void show(final StartEstimate now) {
        final List<String> texts = now.lines(messages);
        final boolean shown = !texts.isEmpty();
        line.setText(shown ? texts.getFirst() : "");
        line.setVisible(shown);
        line.setManaged(shown);
        final Optional<MessageKey> tip = now.tip();
        if (shown && tip.isPresent()) {
            Tips.install(messages, line, tip.get());
        } else {
            line.setTooltip(null);
        }
    }
}
