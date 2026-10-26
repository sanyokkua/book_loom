package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.control.ComparePanes;
import ua.bookloom.ui.control.ProtectedMarks;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.FindingBadge;
import ua.bookloom.ui.state.MarkStatus;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.VisibleText;

/**
 * The review desk's right side: the selected segment's locator and worst finding, the source and target side by side
 * with the readable view under them, the inline note panel, and the tabs of what may be wrong, what the model saw and
 * the suggestion.
 *
 * <p>It owns no state. The editor text is bound both ways to the view model's, the target is read-only while the
 * actions are not offered, and the rest is rewritten when the selected segment changes. The desk lives as long as the
 * application, like its view model, so it observes it directly. No line is logged while the person types.
 */
@Slf4j
final class ReviewDeskDetail extends VBox {

    private static final PseudoClass EMPTY_SOURCE = PseudoClass.getPseudoClass("placeholder");
    private static final double SPACING = 10;
    private static final double TABS_HEIGHT = 240;

    private final ReviewViewModel viewModel;
    private final Messages messages;
    private final BooleanProperty technical;
    private final ComparePanes panes;
    private final Label locator = new Label();
    private final Label worst = new Label();
    private final ReadableView readable;
    private final ReviewDeskTabs tabs;

    ReviewDeskDetail(
            final ReviewViewModel viewModel,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final BooleanProperty technical,
            final Messages messages,
            final ReviewInlinePanels inline) {
        super(SPACING);
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.technical = Objects.requireNonNull(technical, "technical");
        panes = new ComparePanes(sourceName, targetName, messages);
        readable = new ReadableView(messages);
        tabs = new ReviewDeskTabs(viewModel, messages);
        shareHeight();
        panes.targetFooter(new ReviewMarkStrip(viewModel.editor(), messages));
        bindTarget();
        getStyleClass().add("review-desk-detail");
        getChildren()
                .addAll(
                        header(),
                        banner("review-target-note", viewModel.editor().targetNote()),
                        panes,
                        note("review-problem", viewModel.problem()),
                        readable,
                        inline.note(),
                        banner("review-accept-note", viewModel.acceptNote()),
                        tabs);
        ReviewFocusKeeper.install(this, viewModel.retryInFlight());
        viewModel.editorText().addListener((observed, was, now) -> refreshReadable());
        viewModel.selected().addListener((observed, was, now) -> show(now));
        technical.addListener((observed, was, now) -> show(viewModel.selected().get()));
        show(viewModel.selected().get());
    }

    // The editors keep their five rows at the smallest window and share the extra height with the tabs.
    private void shareHeight() {
        tabs.setMinHeight(TABS_HEIGHT);
        tabs.setPrefHeight(TABS_HEIGHT);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        panes.source().setMinHeight(Region.USE_PREF_SIZE);
        panes.target().setMinHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(panes, Priority.ALWAYS);
    }

    private void bindTarget() {
        Tips.install(messages, panes.target(), MessageKey.REVIEW_EDITABLE_TIP);
        ProtectedMarks.install(panes.target(), viewModel.editor().sourceDisplay());
        panes.target().textProperty().bindBidirectional(viewModel.editorText());
        // Read-only while the actions are not offered, so typing never meets a button that silently stays off.
        panes.target().editableProperty().bind(viewModel.actionsAvailable());
    }

    private HBox header() {
        locator.setId("review-locator");
        locator.getStyleClass().add("card-title");
        worst.setId("review-worst");
        worst.getStyleClass().addAll("chip", "chip-warn");
        final ToggleSwitch details = new ToggleSwitch(messages.get(MessageKey.REVIEW_TECHNICAL));
        details.setId("review-technical");
        details.selectedProperty().bindBidirectional(technical);
        Tips.install(messages, details, MessageKey.REVIEW_TECHNICAL_TIP);
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(SPACING, locator, worst, spacer, details);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static Label note(final String id, final ObservableValue<String> text) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add("finding-note");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.textProperty().bind(text);
        // A bound value may still be null before the desk has a segment; that is no text, not a failed binding.
        Visibility.bind(
                label,
                Bindings.createBooleanBinding(
                        () -> label.getText() != null && !label.getText().isEmpty(), label.textProperty()));
        return label;
    }

    private Label banner(final String id, final ObservableValue<@Nullable MessageKey> key) {
        final Label label = note(id, key.map(messages::get).orElse(""));
        label.getStyleClass().setAll("banner", "banner-info", "banner-text");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    // The target text the person is typing is what the readable view must show, so it follows every change.
    private void refreshReadable() {
        final SegmentView view = viewModel.selected().get();
        if (view == null) {
            readable.setVisible(false);
        } else {
            readable.show(view, viewModel.editor().storedText());
        }
    }

    private void show(final @Nullable SegmentView view) {
        if (view == null) {
            log.debug("review detail shows no segment");
            return;
        }
        log.debug("review detail shows segment {}", view.segmentId());
        locator.setText(view.locator());
        final FindingBadge badge = view.status() == SegmentStatus.FLAGGED
                ? FindingBadge.of(view.findings(), view.judgeScore()).orElse(null)
                : null;
        worst.setText(badge == null ? "" : messages.get(badge.why()));
        worst.setVisible(badge != null);
        worst.setManaged(badge != null);
        tabs.show(view, technical.get());
        showSource(view);
        refreshReadable();
    }

    // A source with nothing visible in it still says so, rather than standing as an empty box.
    private void showSource(final SegmentView view) {
        final String shown = MarkStatus.show(view.maskedSource());
        final boolean blank = VisibleText.isBlank(view.maskedSource());
        if (blank) {
            log.debug("segment {} has no visible source text", view.segmentId());
        }
        panes.source().setText(blank ? messages.get(MessageKey.LIVE_EMPTY_SOURCE) : shown);
        panes.source().pseudoClassStateChanged(EMPTY_SOURCE, blank);
    }
}
