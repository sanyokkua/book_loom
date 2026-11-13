package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.control.ProofreadWords;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.RaiserWords;
import ua.bookloom.ui.state.FindingBadge;
import ua.bookloom.ui.state.MarkStatus;

/**
 * Draws one finding of the review desk's "What may be wrong": one label naming its kind, severity and who raised it,
 * then in plain words what may be wrong, or an applied edit an older record keeps as the words it removed beside the
 * words it put there; and the Proofread stage's verdict on the segment. It owns no state and logs nothing.
 */
final class FindingRows {

    private static final String FINDING_KIND = "finding-kind";

    private final Messages messages;

    FindingRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * The row of one finding.
     *
     * @param finding the finding
     * @param technical whether the technical-details switch is on, which adds the rule's kind and own words
     * @return a new row
     */
    VBox of(final QaFinding finding, final boolean technical) {
        return AppliedEdit.from(finding).map(this::editRow).orElseGet(() -> plainRow(finding, technical));
    }

    // An edit an older record keeps (AppliedEdit), as the words it removed beside the words it put there.
    private VBox editRow(final AppliedEdit edit) {
        final Label heading = new Label(messages.get(
                MessageKey.REVIEW_EDIT_APPLIED,
                messages.get(MessageKey.REVIEW_EDIT_CRITERION, edit.criterion().replace('-', '_'))));
        heading.setId("review-edit-criterion");
        heading.getStyleClass().add(FINDING_KIND);
        final Label removed = diffLine("review-edit-removed", "diff-removed", "− " + edit.quote());
        final Label added = diffLine(
                "review-edit-added",
                "diff-added",
                "+ "
                        + (edit.replacement().isEmpty()
                                ? messages.get(MessageKey.REVIEW_EDIT_DELETED)
                                : edit.replacement()));
        final VBox row = new VBox(2, heading, removed, added);
        Tips.install(messages, removed, MessageKey.REVIEW_EDIT_TIP);
        Tips.install(messages, added, MessageKey.REVIEW_EDIT_TIP);
        return row;
    }

    /**
     * The Proofread stage's verdict on a segment: its line in words and, for a kept rewrite, the text it replaced beside
     * the text it put there, headed "Edit applied".
     *
     * @param view the segment shown
     * @return the rows; empty when no proofread window read the segment
     */
    List<VBox> verdict(final SegmentView view) {
        final @Nullable ProofreadVerdict verdict = view.proofread();
        if (verdict == null) {
            return List.of();
        }
        final Label line = new Label(messages.get(
                MessageKey.REVIEW_PROOFREAD_VERDICT,
                ProofreadWords.verdict(messages, verdict.outcome(), verdict.reason(), List.of())));
        line.setId("review-proofread-verdict");
        line.getStyleClass().add(FINDING_KIND);
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        // Both sides in the masked form, marks numbered as the editor shows them, so only the proofread's change
        // differs.
        final @Nullable String maskedBefore = verdict.maskedBefore();
        final @Nullable String before = maskedBefore != null ? maskedBefore : verdict.before();
        final @Nullable String now = view.maskedMachineTarget();
        if (verdict.outcome() != ProofreadOutcome.KEPT || before == null || now == null) {
            return List.of(new VBox(2, line));
        }
        final Label heading = new Label(messages.get(MessageKey.REVIEW_PROOFREAD_APPLIED));
        heading.setId("review-proofread-applied");
        heading.getStyleClass().add(FINDING_KIND);
        final Label removed = diffLine("review-proofread-removed", "diff-removed", "− " + MarkStatus.show(before));
        final Label added = diffLine("review-proofread-added", "diff-added", "+ " + MarkStatus.show(now));
        return List.of(new VBox(2, line), new VBox(2, heading, removed, added));
    }

    private static Label diffLine(final String id, final String style, final String text) {
        final Label line = new Label(text);
        line.setId(id);
        line.getStyleClass().add(style);
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        return line;
    }

    // One label (kind word · severity · who raised it), the plain why, and the rule's own words only on request.
    private VBox plainRow(final QaFinding finding, final boolean technical) {
        final FindingBadge badge = FindingBadge.of(List.of(finding), null).orElse(FindingBadge.LOW_SCORE);
        final Label label = new Label(messages.get(
                MessageKey.REVIEW_FINDING_LABEL,
                messages.get(badge.label()),
                finding.severity().name().toLowerCase(Locale.ROOT),
                RaiserWords.raiser(messages, RaiserWords.checkOf(finding.raisedBy()))));
        label.getStyleClass().add(FINDING_KIND);
        final Label why = wrapped(messages.get(MessageKey.REVIEW_WHY_LINE, messages.get(badge.why())), "finding-note");
        final VBox row = new VBox(2, label, why);
        if (technical) {
            row.getChildren().add(wrapped(finding.kind() + " · " + finding.note(), "muted"));
        }
        return row;
    }

    private static Label wrapped(final String text, final String style) {
        final Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
