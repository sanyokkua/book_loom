package ua.bookloom.ui.control;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressFigures;

/**
 * The closed fold "How progress is counted": why the bar never moves back, and one line per kind of step with how many
 * are done of those planned and the measured seconds per step. Shared by the progress dialog and the Translating
 * strip. Its lines are rebuilt only when a count changes, so a tick that moves nothing adds no node; it logs nothing.
 */
public final class HowCountedPane extends TitledPane {

    private final Messages messages;
    private final VBox kindLines = new VBox();
    private List<ProgressFigures.KindFigures> shownKinds = List.of();

    /**
     * Builds the closed fold.
     *
     * @param id the fold's node id; its lines are {@code <id>-kinds}
     * @param messages the catalogue the fold is worded from
     */
    public HowCountedPane(final String id, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        setText(messages.get(MessageKey.PROGRESS_HOW_COUNTED));
        final Label how = new Label(messages.get(MessageKey.PROGRESS_HOW_COUNTED_TEXT));
        how.setWrapText(true);
        kindLines.setId(Objects.requireNonNull(id, "id") + "-kinds");
        setContent(new VBox(how, kindLines));
        setId(id);
        setExpanded(false);
    }

    /**
     * Shows the figures of each kind of step.
     *
     * @param kinds one entry per kind of step, in the order shown
     */
    public void show(final List<ProgressFigures.KindFigures> kinds) {
        Objects.requireNonNull(kinds, "kinds");
        if (kinds.equals(shownKinds)) {
            return;
        }
        shownKinds = kinds;
        kindLines.getChildren().setAll(kinds.stream().map(this::kindLine).toList());
    }

    private Label kindLine(final ProgressFigures.KindFigures kind) {
        final String name = messages.get(kindName(kind.kind()));
        final Double seconds = kind.secondsPerStep();
        final String text = seconds == null
                ? messages.get(MessageKey.PROGRESS_HOW_COUNTED_UNTIMED, name, kind.done(), kind.planned())
                : messages.get(
                        MessageKey.PROGRESS_HOW_COUNTED_LINE,
                        name,
                        kind.done(),
                        kind.planned(),
                        String.format(Locale.ROOT, "%.1f", seconds));
        final Label line = new Label(text);
        line.getStyleClass().add("dialog-sub");
        return line;
    }

    private static MessageKey kindName(final StepKind kind) {
        return switch (kind) {
            case DRAFT -> MessageKey.PROGRESS_KIND_DRAFT;
            case CHECK -> MessageKey.PROGRESS_KIND_CHECK;
            case REPAIR -> MessageKey.PROGRESS_KIND_REPAIR;
            case RE_ASK -> MessageKey.PROGRESS_KIND_RE_ASK;
            case RESEND -> MessageKey.PROGRESS_KIND_RESEND;
            case SUMMARY -> MessageKey.PROGRESS_KIND_SUMMARY;
            case REVISION -> MessageKey.PROGRESS_KIND_REVISION;
            case STAGE -> MessageKey.PROGRESS_KIND_STAGE;
            case REQUEST -> MessageKey.PROGRESS_KIND_REQUEST;
        };
    }
}
