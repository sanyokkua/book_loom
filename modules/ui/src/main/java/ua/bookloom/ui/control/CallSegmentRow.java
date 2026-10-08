package ua.bookloom.ui.control;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SegmentLive;
import ua.bookloom.ui.state.VisibleText;

/**
 * One segment a call sent: its locator and what became of it as chips, the source exactly as the call carried it, and
 * the target the run has for it so far. A pane whose text shows nothing says why instead of standing empty.
 */
final class CallSegmentRow extends VBox {

    private static final double SPACING = 8;
    private static final double PANE_SPACING = 16;
    private static final int SCORE_DIGITS = 2;
    private static final String PLACEHOLDER = "live-placeholder";

    private final Messages messages;

    CallSegmentRow(
            final String id,
            final CallSegment segment,
            final @Nullable SegmentLive live,
            final List<SegmentOutcomeNote> notes,
            final CallState state,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        setId(id);
        getStyleClass().add("live-row");
        getChildren()
                .addAll(
                        header(id, segment, live, notes),
                        panes(
                                pane(id + "-source", sourceName, source(id, segment)),
                                pane(id + "-target", targetName, target(id, live, state))));
    }

    private Node header(
            final String id,
            final CallSegment segment,
            final @Nullable SegmentLive live,
            final List<SegmentOutcomeNote> notes) {
        final Label locator = new Label(segment.locator());
        locator.setId(id + "-locator");
        locator.getStyleClass().add("muted");
        final FlowPane chips = new FlowPane(SPACING, SPACING);
        chips.setId(id + "-chips");
        notes.forEach(note -> chips.getChildren().add(chip(outcomeText(note), outcomeRole(note.kind()))));
        if (live != null) {
            addDecision(chips, live);
        }
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(SPACING, locator, spacer, chips);
    }

    private void addDecision(final FlowPane chips, final SegmentLive live) {
        final Double judged = live.judgeScore();
        if (judged != null) {
            final NumberFormat score = NumberFormat.getNumberInstance(messages.locale());
            score.setMinimumFractionDigits(SCORE_DIGITS);
            score.setMaximumFractionDigits(SCORE_DIGITS);
            chips.getChildren().add(chip(messages.get(MessageKey.LIVE_JUDGE, score.format(judged)), "chip-neutral"));
        }
        final SegmentPath path = live.path();
        if (path != null) {
            chips.getChildren().add(chip(messages.get(pathKey(path)), "chip-neutral"));
        }
    }

    private String outcomeText(final SegmentOutcomeNote note) {
        return messages.get(
                MessageKey.LIVE_CALL_OUTCOME,
                note.kind().name().toLowerCase(java.util.Locale.ROOT),
                note.detail().isEmpty() ? "none" : detailWords(note.detail()));
    }

    // The detail is the pipeline's comma-separated codes (MISSING, GATE, meaning…); each is worded on its own.
    private String detailWords(final String detail) {
        return java.util.Arrays.stream(detail.split(",", -1))
                .map(String::strip)
                .filter(code -> !code.isEmpty())
                .map(code -> messages.code(MessageKey.LIVE_CALL_DETAIL, code))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String outcomeRole(final SegmentOutcomeNote.Kind kind) {
        return switch (kind) {
            case ADOPTED, ACCEPTED -> "chip-ok";
            case FELL_BACK -> "chip-warn";
            case FLAGGED -> "chip-err";
        };
    }

    private static Label chip(final String text, final String role) {
        final Label label = new Label(text);
        label.getStyleClass().addAll("chip", role);
        return label;
    }

    private Label source(final String id, final CallSegment segment) {
        final Label label = text(id + "-source-text");
        show(label, segment.displaySource(), messages.get(MessageKey.LIVE_EMPTY_SOURCE));
        return label;
    }

    private Label target(final String id, final @Nullable SegmentLive live, final CallState state) {
        final Label label = text(id + "-target-text");
        final String target = live == null ? null : live.target();
        if (target == null) {
            show(
                    label,
                    "",
                    messages.get(state == CallState.WAITING ? MessageKey.LIVE_WAITING : MessageKey.LIVE_NO_TARGET));
        } else {
            show(label, target, messages.get(MessageKey.LIVE_NO_TARGET));
        }
        return label;
    }

    private static void show(final Label label, final String text, final String whenBlank) {
        if (VisibleText.isBlank(text)) {
            label.setText(whenBlank);
            label.getStyleClass().add(PLACEHOLDER);
        } else {
            label.setText(text);
        }
    }

    private static Label text(final String id) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add("live-body");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static VBox pane(final String id, final ObservableValue<String> heading, final Label body) {
        final Label head = new Label();
        head.setId(id + "-head");
        head.getStyleClass().add("stat-caption");
        head.textProperty().bind(heading);
        final VBox pane = new VBox(SPACING, head, body);
        pane.setPrefWidth(0);
        pane.setMinWidth(0);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private static HBox panes(final VBox source, final VBox target) {
        return new HBox(PANE_SPACING, source, target);
    }

    private static MessageKey pathKey(final SegmentPath path) {
        return switch (path) {
            case DRAFT -> MessageKey.LIVE_PATH_DRAFT;
            case REPAIRED -> MessageKey.LIVE_PATH_REPAIRED;
            case TM_REUSE -> MessageKey.LIVE_PATH_TM_REUSE;
            case USER -> MessageKey.LIVE_PATH_USER;
            case SOURCE_KEPT -> MessageKey.LIVE_PATH_SOURCE_KEPT;
            case VERBATIM -> MessageKey.LIVE_PATH_VERBATIM;
        };
    }
}
