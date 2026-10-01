package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.control.ComparePanes;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ContextLine;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.VisibleText;

/**
 * The review panel's right side: the selected segment's two panes, what the model knew, why it was flagged, and the
 * actions on it.
 *
 * <p>It owns no state. The editor text is bound both ways to the view model's, the buttons follow its availability,
 * and the rest is rewritten when its selected segment changes. The view model outlives this node, so that change is
 * observed weakly through a listener the field below keeps alive. No line is logged while the person types.
 */
@Slf4j
final class ReviewComparePane extends VBox {

    private static final PseudoClass EMPTY_SOURCE = PseudoClass.getPseudoClass("placeholder");

    private static final double SPACING = 10;
    private static final int SCORE_DIGITS = 2;

    private final ReviewViewModel viewModel;
    private final Messages messages;
    private final RetryWithNoteDialog retryDialog;
    private final ObservableBooleanValue acceptContinues;
    private final NumberFormat score;
    private final ComparePanes panes;
    private final Label locator = new Label();
    private final Label judge = new Label();
    private final Label context = new Label();
    private final Label contextLine = new Label();
    private final HBox contextRow = new HBox(SPACING);
    private final VBox findings = new VBox(SPACING / 2);
    private final VBox proposalBox = new VBox(SPACING / 2);
    private final Label proposal = new Label();
    private final ChangeListener<@Nullable SegmentView> onSelected = (observed, was, now) -> show(now);

    ReviewComparePane(
            final ReviewViewModel viewModel,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final RetryWithNoteDialog retryDialog,
            final ObservableBooleanValue acceptContinues) {
        super(SPACING);
        this.acceptContinues = Objects.requireNonNull(acceptContinues, "acceptContinues");
        this.retryDialog = Objects.requireNonNull(retryDialog, "retryDialog");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.score = NumberFormat.getNumberInstance(messages.locale());
        score.setMinimumFractionDigits(SCORE_DIGITS);
        score.setMaximumFractionDigits(SCORE_DIGITS);
        panes = new ComparePanes(sourceName, targetName, messages);
        Tips.install(messages, panes.target(), MessageKey.REVIEW_EDITABLE_TIP);
        panes.target().textProperty().bindBidirectional(viewModel.editorText());
        getChildren()
                .addAll(
                        header(),
                        contextRow(),
                        panes,
                        proposalBox(),
                        note("review-hint", viewModel.hint().map(this::wording)),
                        note("review-problem", viewModel.problem()),
                        findingsBox(),
                        actions());
        viewModel.selected().addListener(new WeakChangeListener<>(onSelected));
        show(viewModel.selected().get());
    }

    private String wording(final @Nullable MessageKey key) {
        return key == null ? "" : messages.get(key);
    }

    private HBox header() {
        locator.setId("review-locator");
        locator.getStyleClass().add("card-title");
        judge.setId("review-judge");
        judge.getStyleClass().addAll("chip", "chip-neutral");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(SPACING, locator, spacer, judge);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox contextRow() {
        context.setText(messages.get(MessageKey.REVIEW_CONTEXT));
        context.getStyleClass().add("stat-caption");
        contextLine.setId("review-context-line");
        contextLine.getStyleClass().add("muted");
        contextRow.setId("review-context");
        contextRow.getChildren().addAll(context, contextLine);
        return contextRow;
    }

    private static Label note(final String id, final ObservableValue<String> text) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add("finding-note");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.textProperty().bind(text);
        label.visibleProperty()
                .bind(Bindings.createBooleanBinding(() -> !label.getText().isEmpty(), label.textProperty()));
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private VBox findingsBox() {
        final Label heading = new Label(messages.get(MessageKey.REVIEW_FINDINGS));
        heading.getStyleClass().add("stat-caption");
        findings.setId("review-findings");
        return new VBox(SPACING / 2, heading, findings);
    }

    private VBox proposalBox() {
        final Label caption = new Label(messages.get(MessageKey.REVIEW_PROPOSAL));
        caption.getStyleClass().add("stat-caption");
        proposal.setId("review-proposal-text");
        proposal.getStyleClass().add("finding-note");
        proposal.setWrapText(true);
        proposal.setMinHeight(Region.USE_PREF_SIZE);
        final Button accept = action(
                "review-accept-proposal",
                MessageKey.REVIEW_ACCEPT_PROPOSAL,
                MessageKey.REVIEW_ACCEPT_PROPOSAL_TIP,
                "btn-secondary",
                viewModel::acceptProposal,
                viewModel.actionsAvailable());
        proposalBox.setId("review-proposal");
        proposalBox.getChildren().addAll(caption, proposal, accept);
        return proposalBox;
    }

    private void askForNote() {
        final SegmentView view = viewModel.selected().get();
        if (view != null) {
            log.debug("asking for a retry note on segment {}", view.segmentId());
            retryDialog.ask(view.locator(), choice -> viewModel.retry(choice.note(), choice.lowerTemperature()));
        }
    }

    private Button acceptButton() {
        final Button accept = action(
                "review-accept",
                MessageKey.REVIEW_ACCEPT,
                MessageKey.REVIEW_ACCEPT_TIP,
                "btn-primary",
                viewModel::accept,
                viewModel.acceptAvailable());
        accept.textProperty()
                .bind(Bindings.when(acceptContinues)
                        .then(messages.get(MessageKey.REVIEW_ACCEPT_CONTINUE))
                        .otherwise(messages.get(MessageKey.REVIEW_ACCEPT)));
        return accept;
    }

    private FlowPane actions() {
        final FlowPane row = new FlowPane(SPACING, SPACING / 2);
        row.getChildren().addAll(decisions());
        row.getChildren().addAll(retries());
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private List<Button> decisions() {
        return List.of(
                acceptButton(),
                action(
                        "review-save",
                        MessageKey.REVIEW_SAVE,
                        MessageKey.REVIEW_SAVE_TIP,
                        "btn-secondary",
                        viewModel::saveEdit,
                        Bindings.and(viewModel.actionsAvailable(), viewModel.dirty())),
                available(
                        "review-revert",
                        MessageKey.REVIEW_REVERT,
                        MessageKey.REVIEW_REVERT_TIP,
                        "btn-secondary",
                        viewModel::revert),
                available(
                        "review-skip",
                        MessageKey.REVIEW_SKIP,
                        MessageKey.REVIEW_SKIP_TIP,
                        "btn-ghost",
                        viewModel::skip));
    }

    private List<Button> retries() {
        return List.of(
                available(
                        "review-retry",
                        MessageKey.REVIEW_RETRY,
                        MessageKey.REVIEW_RETRY_TIP,
                        "btn-secondary",
                        () -> viewModel.retry(null, false)),
                available(
                        "review-retry-note",
                        MessageKey.REVIEW_RETRY_NOTE,
                        MessageKey.REVIEW_RETRY_NOTE_TIP,
                        "btn-ghost",
                        this::askForNote));
    }

    private Button available(
            final String id, final MessageKey label, final MessageKey tip, final String style, final Runnable run) {
        return action(id, label, tip, style, run, viewModel.actionsAvailable());
    }

    private Button action(
            final String id,
            final MessageKey label,
            final MessageKey tip,
            final String style,
            final Runnable run,
            final ObservableBooleanValue available) {
        final Button button = Tips.install(messages, new Button(messages.get(label)), tip);
        button.setId(id);
        button.getStyleClass().add(style);
        button.disableProperty().bind(Bindings.not(available));
        button.setOnAction(event -> {
            log.debug("review button {} pressed", id);
            run.run();
        });
        return button;
    }

    private void show(final @Nullable SegmentView view) {
        if (view == null) {
            log.debug("review compare shows no segment");
            return;
        }
        log.debug("review compare shows segment {}", view.segmentId());
        locator.setText(view.locator());
        judge.setText(
                view.judgeScore() == null ? "" : messages.get(MessageKey.LIVE_JUDGE, score.format(view.judgeScore())));
        judge.setVisible(view.judgeScore() != null);
        judge.setManaged(view.judgeScore() != null);
        final String line = view.context() == null ? "" : ContextLine.of(view.context());
        contextLine.setText(line);
        findingsFor(view);
        proposal.setText(view.proposal() == null ? "" : view.proposal());
        proposalBox.setVisible(view.proposal() != null);
        proposalBox.setManaged(view.proposal() != null);
        showSource(view);
        setRowShown(line);
    }

    // A source with nothing visible in it still says so, rather than standing as an empty box.
    private void showSource(final SegmentView view) {
        final boolean blank = VisibleText.isBlank(view.maskedSource());
        if (blank) {
            log.debug("segment {} has no visible source text", view.segmentId());
        }
        panes.source().setText(blank ? messages.get(MessageKey.LIVE_EMPTY_SOURCE) : view.maskedSource());
        panes.source().pseudoClassStateChanged(EMPTY_SOURCE, blank);
    }

    private void setRowShown(final String line) {
        final boolean shown = !line.isEmpty();
        contextRow.setVisible(shown);
        contextRow.setManaged(shown);
    }

    private void findingsFor(final SegmentView view) {
        findings.getChildren()
                .setAll(view.findings().stream().map(this::findingRow).toList());
    }

    private VBox findingRow(final QaFinding finding) {
        final Label kind = new Label(messages.get(
                MessageKey.REVIEW_FINDING_KIND,
                finding.kind(),
                finding.severity().name().toLowerCase(Locale.ROOT)));
        kind.getStyleClass().add("finding-kind");
        final Label note = new Label(finding.note());
        note.getStyleClass().add("finding-note");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        final Label source = new Label(messages.get(MessageKey.REVIEW_RAISED_BY, finding.raisedBy()));
        source.getStyleClass().add("muted");
        return new VBox(2, kind, note, source);
    }
}
