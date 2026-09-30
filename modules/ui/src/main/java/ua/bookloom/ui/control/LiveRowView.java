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

/** One row of the live panel: a locator and badges above a source pane and a target pane. */
final class LiveRowView extends VBox {

    private static final double SPACING = 8;
    private static final double PANE_SPACING = 16;
    private static final int SCORE_DIGITS = 2;
    private static final double ROW_HEIGHT = 150;

    private final Messages messages;
    private final NumberFormat score;
    private final Label locator = new Label();
    private final Label judge = badge("chip-neutral");
    private final Label awaiting = badge("chip-warn");
    private final Label path = badge("chip-neutral");
    private final Label source = text();
    private final Label target = text();
    private final ScrollPane sourceScroll = scrolling(source);
    private final ScrollPane targetScroll = scrolling(target);

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
        locator.getStyleClass().add("muted");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(SPACING, locator, spacer, judge, awaiting, path);
        final HBox panes = new HBox(
                PANE_SPACING,
                pane(idPrefix + "-source", sourceName, sourceScroll),
                pane(idPrefix + "-target", targetName, targetScroll));
        VBox.setVgrow(panes, Priority.ALWAYS);
        sourceScroll.setId(idPrefix + "-source-scroll");
        targetScroll.setId(idPrefix + "-target-scroll");
        setPrefHeight(ROW_HEIGHT);
        setMinHeight(ROW_HEIGHT);
        setMaxHeight(ROW_HEIGHT);
        getChildren().addAll(header, panes);
        setPadding(new Insets(SPACING));
        getStyleClass().add("live-row");
        show(null);
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
        if (row == null) {
            locator.setText("");
            source.setText("");
            target.setText("");
            hide(judge, awaiting, path);
            return;
        }
        locator.setText(row.locator());
        source.setText(row.sourceText());
        target.setText(targetOf(row));
        set(
                judge,
                row.judgeScore() == null ? null : messages.get(MessageKey.LIVE_JUDGE, score.format(row.judgeScore())));
        set(awaiting, row.awaitingJudge() ? messages.get(MessageKey.LIVE_AWAITING_JUDGE) : null);
        set(path, row.path() == null ? null : messages.get(pathKey(row.path())));
    }

    private String targetOf(final LiveRow row) {
        if (row.awaitingDraft()) {
            return messages.get(MessageKey.LIVE_WAITING);
        }
        return row.targetText() == null ? "" : row.targetText();
    }

    private static MessageKey pathKey(final SegmentPath path) {
        return switch (path) {
            case DRAFT -> MessageKey.LIVE_PATH_DRAFT;
            case REPAIRED -> MessageKey.LIVE_PATH_REPAIRED;
            case TM_REUSE -> MessageKey.LIVE_PATH_TM_REUSE;
            case USER -> MessageKey.LIVE_PATH_USER;
            case SOURCE_KEPT -> MessageKey.LIVE_PATH_SOURCE_KEPT;
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
