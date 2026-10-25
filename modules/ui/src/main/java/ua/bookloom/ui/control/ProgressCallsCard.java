package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressDialogModel;

/**
 * The Calls view of the progress dialog: the model's calls, newest first, beside the selected call's detail, with
 * Close as its one button. It reads the same {@link ProgressDialogModel} as the work view, one request step per call, so
 * a call opens as it does there. Redraws are not logged: the list may change up to ten times a second. FX thread only.
 */
@Slf4j
final class ProgressCallsCard extends ModalCard {

    private static final double DIVIDER = 0.36;

    private final ProgressDialogModel model;
    private final Messages messages;

    ProgressCallsCard(final ProgressDialogModel model, final Messages messages, final Runnable onClose) {
        super(Size.XL);
        this.model = Objects.requireNonNull(model, "model");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onClose, "onClose");
        setId("calls-card");
        getStyleClass().addAll("dialog-card", "elevation-lg");
        setHeader(header());
        setContent(body());
        final ButtonType close =
                new ButtonType(messages.get(MessageKey.COMMON_CLOSE), ButtonBar.ButtonData.CANCEL_CLOSE);
        getButtonTypes().setAll(close);
        final Button button = (Button) lookupButton(close);
        button.setId("calls-close");
        button.getStyleClass().add("btn-primary");
        Tips.install(messages, button, MessageKey.COMMON_CLOSE_TIP);
        button.setOnAction(event -> {
            log.info("calls: close pressed");
            onClose.run();
        });
        setOnEscape(onClose);
        log.debug("calls view built over {} calls", model.rows().size());
    }

    private Node header() {
        final Label title = new Label(messages.get(MessageKey.BUSY_CALLS));
        title.setId("progress-title");
        title.getStyleClass().add("dialog-title");
        final Label purpose = new Label(messages.get(MessageKey.PROGRESS_CALLS_PURPOSE));
        purpose.setId("progress-purpose");
        purpose.getStyleClass().add("dialog-sub");
        purpose.setWrapText(true);
        final VBox header = new VBox(title, purpose);
        header.getStyleClass().addAll("dialog-h", "progress-header");
        return header;
    }

    private Node body() {
        final ProgressStepList calls = new ProgressStepList(model, messages, true);
        VBox.setVgrow(calls, Priority.ALWAYS);
        final ScrollPane detail = new ScrollHost(new ProgressStepDetail(model, messages));
        detail.getStyleClass().add("progress-detail-scroll");
        final SplitPane split = new SplitPane(calls, detail);
        split.setDividerPositions(DIVIDER);
        split.getStyleClass().add("progress-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        final VBox body = new VBox(split);
        body.getStyleClass().add("progress-body");
        return body;
    }
}
