package ua.bookloom.ui.control;

import java.text.NumberFormat;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.RoundTrack;
import ua.bookloom.ui.state.VisibleText;

/**
 * One row of the live panel: a locator and badges, the repair-round tracker, a source pane and a target pane, and the
 * collapsed context the draft was sent with. A row with no segment is not shown at all, and a pane whose text shows
 * nothing says why instead of standing empty.
 */
final class LiveRowView extends VBox {

    private static final double SPACING = 8;
    private static final double PANE_SPACING = 16;
    private static final int SCORE_DIGITS = 2;
    private static final double PANES_HEIGHT = 120;
    private static final String PLACEHOLDER = "live-placeholder";

    private final Messages messages;
    private final NumberFormat score;
    private final Label locator = new Label();
    private final Label judge = badge("chip-neutral");
    private final Label awaiting = badge("chip-warn");
    private final Label path = badge("chip-neutral");
    private final Label round = badge("chip-warn");
    private final Label source = text();
    private final Label target = text();
    private final ScrollPane sourceScroll = scrolling(source);
    private final ScrollPane targetScroll = scrolling(target);
    private final ContextSection context;

    LiveRowView(
            final String idPrefix,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.score = NumberFormat.getNumberInstance(messages.locale());
        score.setMinimumFractionDigits(SCORE_DIGITS);
        score.setMaximumFractionDigits(SCORE_DIGITS);
        name(idPrefix, locator, judge, awaiting, path, source, target);
        round.setId(idPrefix + "-round");
        context = new ContextSection(idPrefix + "-context", messages);
        locator.getStyleClass().add("muted");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(SPACING, locator, spacer, round, judge, awaiting, path);
        getChildren().addAll(header, panes(idPrefix, sourceName, targetName), context);
        setPadding(new Insets(SPACING));
        setId(idPrefix);
        getStyleClass().add("live-row");
        show(null);
    }

    // The panes keep one height whatever the row holds, so the context section below opens without moving them.
    private HBox panes(
            final String idPrefix, final ObservableValue<String> sourceName, final ObservableValue<String> targetName) {
        final HBox panes = new HBox(
                PANE_SPACING,
                pane(idPrefix + "-source", sourceName, sourceScroll),
                pane(idPrefix + "-target", targetName, targetScroll));
        panes.setPrefHeight(PANES_HEIGHT);
        panes.setMinHeight(PANES_HEIGHT);
        panes.setMaxHeight(PANES_HEIGHT);
        sourceScroll.setId(idPrefix + "-source-scroll");
        targetScroll.setId(idPrefix + "-target-scroll");
        targetScroll.getStyleClass().add("live-target");
        return panes;
    }

    private static void name(
            final String idPrefix,
            final Label locator,
            final Label judge,
            final Label awaiting,
            final Label path,
            final Label source,
            final Label target) {
        locator.setId(idPrefix + "-locator");
        judge.setId(idPrefix + "-judge");
        awaiting.setId(idPrefix + "-awaiting");
        path.setId(idPrefix + "-path");
        source.setId(idPrefix + "-source");
        target.setId(idPrefix + "-target");
    }

    private static Label badge(final String role) {
        final Label label = new Label();
        label.getStyleClass().addAll("chip", role);
        return label;
    }

    private static Label text() {
        final Label label = new Label();
        label.getStyleClass().add("live-body");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static ScrollPane scrolling(final Label body) {
        final ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private static VBox pane(final String id, final ObservableValue<String> heading, final ScrollPane body) {
        final Label head = new Label();
        head.setId(id + "-head");
        head.getStyleClass().add("stat-caption");
        head.textProperty().bind(heading);
        final VBox pane = new VBox(SPACING, head, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        pane.setPrefWidth(0);
        pane.setMinWidth(0);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    void show(final @Nullable LiveRow row) {
        sourceScroll.setVvalue(0);
        targetScroll.setVvalue(0);
        setVisible(row != null);
        setManaged(row != null);
        if (row == null) {
            locator.setText("");
            source.setText("");
            target.setText("");
            hide(judge, awaiting, path, round);
            context.show(null);
            return;
        }
        locator.setText(row.locator());
        showText(source, row.sourceText(), MessageKey.LIVE_EMPTY_SOURCE);
        showTarget(row);
        set(
                judge,
                row.judgeScore() == null ? null : messages.get(MessageKey.LIVE_JUDGE, score.format(row.judgeScore())));
        set(awaiting, row.awaitingJudge() ? messages.get(MessageKey.LIVE_AWAITING_JUDGE) : null);
        set(path, row.path() == null ? null : messages.get(pathKey(row.path())));
        set(round, roundText(row.round()));
        context.show(row.context());
    }

    private void showTarget(final LiveRow row) {
        if (row.awaitingDraft()) {
            showPlaceholder(target, messages.get(MessageKey.LIVE_WAITING));
            return;
        }
        showText(target, row.targetText() == null ? "" : row.targetText(), MessageKey.LIVE_NO_TARGET);
    }

    private void showText(final Label pane, final String text, final MessageKey whenBlank) {
        if (VisibleText.isBlank(text)) {
            showPlaceholder(pane, messages.get(whenBlank));
        } else {
            pane.setText(text);
            pane.getStyleClass().remove(PLACEHOLDER);
        }
    }

    private static void showPlaceholder(final Label pane, final String text) {
        pane.setText(text);
        if (!pane.getStyleClass().contains(PLACEHOLDER)) {
            pane.getStyleClass().add(PLACEHOLDER);
        }
    }

    private @Nullable String roundText(final @Nullable RoundTrack track) {
        if (track == null) {
            return null;
        }
        final Double judged = track.judgeScore();
        final String finding = track.blockingFinding();
        return messages.get(
                MessageKey.LIVE_ROUND,
                String.valueOf(track.round()),
                String.valueOf(track.rounds()),
                judged == null ? "none" : score.format(judged),
                finding == null ? "none" : finding);
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

    private static void hide(final Label... labels) {
        for (final Label label : labels) {
            set(label, null);
        }
    }

    private static void set(final Label label, final @Nullable String text) {
        label.setText(text == null ? "" : text);
        label.setVisible(text != null);
        label.setManaged(text != null);
    }
}
