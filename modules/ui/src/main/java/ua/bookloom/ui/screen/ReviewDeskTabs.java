package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.ui.control.ContextSection;
import ua.bookloom.ui.control.PromptView;
import ua.bookloom.ui.control.ScrollHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review desk's three tabs under the compare: What may be wrong (one label per finding), What the model saw (the
 * sections a batch request really sent, or the context of a segment drafted alone), and Suggestion (a backward
 * revision's proposal, applied only on "Use the suggestion"). It owns no state; the detail calls {@link #show}.
 */
@Slf4j
final class ReviewDeskTabs extends TabPane {

    private static final double SPACING = 8;

    private final Messages messages;
    private final FindingRows rows;
    private final VBox findings = new VBox(SPACING);
    private final Label intro = muted("review-context-intro");
    private final PromptView sent;
    private final ContextSection context;
    private final Label now = new Label();
    private final Label proposal = new Label();
    private final VBox proposalBox = new VBox(SPACING / 2);
    private final Label none = muted("review-suggestion-none");
    private final Tab suggestionTab = new Tab();

    ReviewDeskTabs(final ReviewViewModel viewModel, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(viewModel, "viewModel");
        rows = new FindingRows(messages);
        sent = new PromptView("review-sent", messages);
        context = new ContextSection("review-context", messages);
        findings.setId("review-findings");
        setId("review-tabs");
        getStyleClass().add("review-tabs");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        suggestionTab.setContent(scroll(suggestion(viewModel)));
        getTabs()
                .setAll(
                        tab(MessageKey.REVIEW_TAB_WHY, findings),
                        tab(MessageKey.REVIEW_TAB_CONTEXT, new VBox(SPACING, intro, sent, context)),
                        suggestionTab);
    }

    /**
     * Shows a segment.
     *
     * @param view the segment selected
     * @param technical whether the technical-details switch is on
     */
    void show(final SegmentView view, final boolean technical) {
        showFindings(view, technical);
        showContext(view.context());
        final String proposed = view.proposal();
        now.setText(Objects.requireNonNullElse(
                view.maskedUserTarget(), Objects.requireNonNullElse(view.maskedMachineTarget(), "")));
        proposal.setText(Objects.requireNonNullElse(proposed, ""));
        shown(proposalBox, proposed != null);
        shown(none, proposed == null);
        suggestionTab.setText(messages.get(MessageKey.REVIEW_TAB_SUGGESTION, proposed == null ? 0 : 1));
    }

    // A flagged segment whose record holds no finding still says why it is listed, rather than an empty tab.
    private void showFindings(final SegmentView view, final boolean technical) {
        if (view.findings().isEmpty() && view.status() == SegmentStatus.FLAGGED) {
            final Label empty = new Label(messages.get(MessageKey.REVIEW_FINDINGS_NONE));
            empty.setId("review-findings-none");
            empty.getStyleClass().add("finding-note");
            empty.setWrapText(true);
            empty.setMinHeight(Region.USE_PREF_SIZE);
            findings.getChildren().setAll(empty);
            return;
        }
        findings.getChildren()
                .setAll(view.findings().stream()
                        .map(finding -> rows.of(finding, technical))
                        .toList());
    }

    // A batch draft shows what its one request sent; a draft made alone shows the context it was given.
    private void showContext(final @Nullable ContextSnapshot snapshot) {
        final List<PromptSection> sections = snapshot == null ? null : snapshot.sentSections();
        final List<PromptSection> batch = sections == null ? List.of() : sections;
        final boolean inBatch = !batch.isEmpty();
        log.debug("what the model saw: {}", inBatch ? "the batch request's sections" : "the segment's own context");
        sent.show(batch);
        sent.setExpanded(true);
        context.show(inBatch ? null : snapshot);
        context.setExpanded(true);
        intro.setText(messages.get(inBatch ? MessageKey.REVIEW_CONTEXT_BATCH : MessageKey.REVIEW_CONTEXT_INTRO));
    }

    private Node suggestion(final ReviewViewModel viewModel) {
        now.setId("review-suggestion-now");
        proposal.setId("review-proposal-text");
        for (final Label text : List.of(now, proposal)) {
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            text.getStyleClass().add("finding-note");
        }
        final Button use = useButton(viewModel);
        proposalBox.setId("review-proposal");
        proposalBox
                .getChildren()
                .setAll(
                        caption(MessageKey.REVIEW_SUGGESTION_NOW),
                        now,
                        caption(MessageKey.REVIEW_SUGGESTION_PROPOSED),
                        proposal,
                        use);
        none.setText(messages.get(MessageKey.REVIEW_SUGGESTION_NONE));
        final VBox box = new VBox(SPACING, proposalBox, none);
        box.setId("review-suggestion");
        return box;
    }

    // The suggestion is applied only by this button, never by itself.
    private Button useButton(final ReviewViewModel viewModel) {
        final Button use = Tips.install(
                messages,
                new Button(messages.get(MessageKey.REVIEW_SUGGESTION_USE)),
                MessageKey.REVIEW_SUGGESTION_USE_TIP);
        use.setId("review-accept-proposal");
        use.getStyleClass().add("btn-secondary");
        use.disableProperty().bind(viewModel.actionsAvailable().not());
        use.setOnAction(event -> {
            log.debug("use the suggestion pressed");
            viewModel.acceptProposal();
        });
        return use;
    }

    private Label caption(final MessageKey key) {
        final Label caption = new Label(messages.get(key));
        caption.getStyleClass().add("stat-caption");
        return caption;
    }

    private Tab tab(final MessageKey title, final Node content) {
        final Tab tab = new Tab(messages.get(title), scroll(content));
        tab.setClosable(false);
        return tab;
    }

    private static ScrollPane scroll(final Node content) {
        final ScrollPane scroll = new ScrollHost(content);
        scroll.getStyleClass().add("review-tab-scroll");
        return scroll;
    }

    private static Label muted(final String id) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static void shown(final Node node, final boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
