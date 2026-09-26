package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.StateMirror;

/**
 * The export screen's frame, which reports the file a finished run wrote and says so plainly while there is none.
 *
 * <p>The mirror keeps the report of a stopped or failed run as well as a completed one, so the screen keys on the
 * report's own end state and a written path rather than on the report merely existing. The report is observed through
 * a weak listener held by the field below, because the mirror outlives this controller; the host keeps a reference to
 * the controller in its properties, which is what lets the listener live exactly as long as the screen does. The
 * report changes once per run, so the listener may log.
 */
@Slf4j
public final class ExportController {

    private final StateMirror mirror;
    private final Messages messages;
    private final FileRevealer revealer;
    private final ChangeListener<@Nullable JobReport> onReport = (observed, was, now) -> render(now);

    @FXML
    private Label title;

    @FXML
    private Pane host;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param mirror the run's state, whose report this screen shows
     * @param messages the catalogue the built parts are worded from
     * @param revealer what shows the written file in the file manager
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ExportController(final StateMirror mirror, final Messages messages, final FileRevealer revealer) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.revealer = Objects.requireNonNull(revealer, "revealer");
    }

    @FXML
    void initialize() {
        host.getProperties().put(ExportController.class, this);
        mirror.report().addListener(new WeakChangeListener<>(onReport));
        render(mirror.report().get());
    }

    private void render(final @Nullable JobReport report) {
        final @Nullable Path written = report != null && report.end() == JobState.COMPLETED ? report.written() : null;
        final Node content;
        if (report == null || written == null) {
            log.debug("showing the empty state, the report is {}", report == null ? null : report.end());
            title.setText(messages.get(MessageKey.NAV_EXPORT));
            content = ExportView.empty(messages);
        } else {
            log.info(
                    "showing the finished book {}: {} accepted, {} flagged",
                    written,
                    report.accepted(),
                    report.flagged());
            title.setText(messages.get(MessageKey.EXPORT_TITLE));
            content = ExportView.populated(report, written, messages, () -> reveal(written));
        }
        host.getChildren().setAll(content);
    }

    private void reveal(final Path written) {
        log.debug("reveal pressed for {}", written);
        revealer.reveal(written);
    }
}
