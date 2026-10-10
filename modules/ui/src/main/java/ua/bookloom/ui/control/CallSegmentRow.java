package ua.bookloom.ui.control;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
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
 *
 * <p>The row is built once and updated in place as its call is answered and its segment decided, so a person reading
 * or selecting in it keeps their place; its chips are rebuilt only when their words change.
 */
final class CallSegmentRow extends VBox {

    private static final double SPACING = 8;
    private static final double PANE_SPACING = 16;
    private static final int SCORE_DIGITS = 2;
    private static final String PLACEHOLDER = "live-placeholder";

    /** One chip as shown: its words and its role class. */
    private record Chip(String text, String role) {}

    private final Messages messages;
    private final Label locator = new Label();
    private final FlowPane chips = new FlowPane(SPACING, SPACING);
    private final Label sourceHead = new Label();
    private final Label targetHead = new Label();
    private final Label source = text();
    private final Label target = text();
    private final VBox sourcePane;
    private final VBox targetPane;
    private List<Chip> shownChips = List.of();

    CallSegmentRow(
            final String id,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        getStyleClass().add("live-row");
        locator.getStyleClass().add("muted");
        sourcePane = pane(sourceHead, sourceName, source);
        targetPane = pane(targetHead, targetName, target);
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(new HBox(SPACING, locator, spacer, chips), new HBox(PANE_SPACING, sourcePane, targetPane));
        rename(id);
    }

    /** Gives the row and its parts new ids, as a row of a block that moved to another role. */
    void rename(final String id) {
        setId(id);
        locator.setId(id + "-locator");
        chips.setId(id + "-chips");
        sourcePane.setId(id + "-source");
        sourceHead.setId(id + "-source-head");
        source.setId(id + "-source-text");
        targetPane.setId(id + "-target");
        targetHead.setId(id + "-target-head");
        target.setId(id + "-target-text");
    }

    /**
     * Shows a segment of the call.
     *
     * @param segment the segment as the call sent it
     * @param live its target and decision so far, or {@code null} when the run has none yet
     * @param notes what the call's outcome noted on this segment
     * @param state where the call stands, which words a missing target
     */
    void show(
            final CallSegment segment,
            final @Nullable SegmentLive live,
            final List<SegmentOutcomeNote> notes,
            final CallState state) {
        locator.setText(segment.locator());
        showChips(notes, live);
        show(source, segment.displaySource(), messages.get(MessageKey.LIVE_EMPTY_SOURCE));
        final String drafted = live == null ? null : live.target();
        if (drafted == null) {
            show(
                    target,
                    "",
                    messages.get(state == CallState.WAITING ? MessageKey.LIVE_WAITING : MessageKey.LIVE_NO_TARGET));
        } else {
            show(target, drafted, messages.get(MessageKey.LIVE_NO_TARGET));
        }
    }

    private void showChips(final List<SegmentOutcomeNote> notes, final @Nullable SegmentLive live) {
        final List<Chip> wanted = new ArrayList<>();
        notes.forEach(note -> wanted.add(new Chip(outcomeText(note), outcomeRole(note.kind()))));
        if (live != null) {
            addDecision(wanted, live);
        }
        if (!wanted.equals(shownChips)) {
            shownChips = List.copyOf(wanted);
            chips.getChildren().setAll(wanted.stream().map(CallSegmentRow::chip).toList());
        }
    }

    private void addDecision(final List<Chip> wanted, final SegmentLive live) {
        final Double judged = live.judgeScore();
        if (judged != null) {
            final NumberFormat score = NumberFormat.getNumberInstance(messages.locale());
            score.setMinimumFractionDigits(SCORE_DIGITS);
            score.setMaximumFractionDigits(SCORE_DIGITS);
            wanted.add(new Chip(messages.get(MessageKey.LIVE_JUDGE, score.format(judged)), "chip-neutral"));
        }
        final SegmentPath path = live.path();
        if (path != null) {
            wanted.add(new Chip(messages.get(pathKey(path)), "chip-neutral"));
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

    private static Label chip(final Chip spec) {
        final Label label = new Label(spec.text());
        label.getStyleClass().addAll("chip", spec.role());
        return label;
    }

    // The placeholder class is changed only when it must: a changed class list restyles and re-measures the label.
    private static void show(final Label label, final String text, final String whenBlank) {
        final boolean blank = VisibleText.isBlank(text);
        label.setText(blank ? whenBlank : text);
        if (blank && !label.getStyleClass().contains(PLACEHOLDER)) {
            label.getStyleClass().add(PLACEHOLDER);
        } else if (!blank) {
            label.getStyleClass().remove(PLACEHOLDER);
        }
    }

    private static Label text() {
        final Label label = new Label();
        label.getStyleClass().add("live-body");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static VBox pane(final Label head, final ObservableValue<String> heading, final Label body) {
        head.getStyleClass().add("stat-caption");
        head.textProperty().bind(heading);
        final VBox pane = new VBox(SPACING, head, body);
        pane.setPrefWidth(0);
        pane.setMinWidth(0);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
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
