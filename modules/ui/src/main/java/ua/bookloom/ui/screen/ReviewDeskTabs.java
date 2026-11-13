package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.control.PromptView;
import ua.bookloom.ui.control.ScrollHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.RaiserWords;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review desk's three tabs under the compare: What may be wrong (one label per finding), What the model saw (the
 * run's fixed frame the segment's call was sent with, joined from its stored copy, then the call's own sections — a
 * batch's as that request sent them, a retry's as Try again rebuilt and sent them, labelled so), and Suggestion (a backward
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
    private final Label noCall = muted("review-context-none");
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
        findings.setId("review-findings");
        setId("review-tabs");
        getStyleClass().add("review-tabs");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        suggestionTab.setContent(scroll(suggestion(viewModel)));
        getTabs()
                .setAll(
                        tab(MessageKey.REVIEW_TAB_WHY, findings),
                        tab(MessageKey.REVIEW_TAB_CONTEXT, new VBox(SPACING, intro, noCall, sent)),
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
        showContext(view);
        final String proposed = view.proposal();
        now.setText(Objects.requireNonNullElse(
                view.maskedUserTarget(), Objects.requireNonNullElse(view.maskedMachineTarget(), "")));
        proposal.setText(Objects.requireNonNullElse(proposed, ""));
        Visibility.show(proposalBox, proposed != null);
        Visibility.show(none, proposed == null);
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
            findings.getChildren().addAll(0, rows.verdict(view));
            return;
        }
        final List<QaFinding> shown = withoutRepeatedDoubts(view.findings());
        log.debug(
                "findings of segment {}: {} stored, {} shown",
                view.segmentId(),
                view.findings().size(),
                shown.size());
        final List<Node> drawn = new ArrayList<>(rows.verdict(view));
        shown.forEach(finding -> drawn.add(rows.of(finding, technical)));
        findings.getChildren().setAll(drawn);
    }

    // The audit doubts every accepted segment with the run's own checks, so a check the run already recorded comes back
    // as its audit twin; the stored twin keeps the segment under "Worth a second look", and the desk draws it once.
    private static List<QaFinding> withoutRepeatedDoubts(final List<QaFinding> stored) {
        final Set<String> recorded = stored.stream()
                .map(QaFinding::raisedBy)
                .filter(raisedBy -> raisedBy.equals(RaiserWords.checkOf(raisedBy)))
                .collect(Collectors.toSet());
        return stored.stream()
                .filter(finding -> finding.raisedBy().equals(RaiserWords.checkOf(finding.raisedBy()))
                        || !recorded.contains(RaiserWords.checkOf(finding.raisedBy())))
                .toList();
    }

    // The stored frame and the call's own sections together are the prompt as it was sent. A segment that no call was
    // made for, such as a memory reuse or a shortcut, says so instead of showing an empty prompt.
    private void showContext(final SegmentView view) {
        final ContextSnapshot snapshot = view.context();
        final List<PromptSection> own = snapshot == null ? List.of() : snapshot.sections();
        final List<PromptSection> parts = new ArrayList<>(view.frame());
        parts.addAll(own);
        final boolean called = !parts.isEmpty();
        final boolean retried = snapshot != null && snapshot.retry();
        log.debug(
                "what the model saw: segment={} frameSections={} ownSections={} retry={}",
                view.segmentId(),
                view.frame().size(),
                own.size(),
                retried);
        sent.show(List.copyOf(parts));
        sent.setExpanded(true);
        intro.setText(messages.get(retried ? MessageKey.REVIEW_CONTEXT_RETRY : MessageKey.REVIEW_CONTEXT_INTRO));
        noCall.setText(messages.get(MessageKey.REVIEW_CONTEXT_NONE));
        Visibility.show(intro, called);
        Visibility.show(noCall, !called);
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
}
