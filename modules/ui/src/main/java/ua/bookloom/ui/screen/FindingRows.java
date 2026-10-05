package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.FindingBadge;

/**
 * Draws one finding of the review panel's list: a check's or the reviewer's note with its kind badge, or an edit the
 * reviewer made as the words it removed beside the words it put there. It owns no state and logs nothing.
 */
final class FindingRows {

    private static final double SPACING = 10;

    private final Messages messages;

    FindingRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    VBox of(final QaFinding finding) {
        return AppliedEdit.from(finding).map(this::editRow).orElseGet(() -> plainRow(finding));
    }

    // An edit the reviewer made and the app verified, as the words it removed beside the words it put there.
    private VBox editRow(final AppliedEdit edit) {
        final Label heading = new Label(messages.get(
                MessageKey.REVIEW_EDIT_APPLIED,
                messages.get(MessageKey.REVIEW_EDIT_CRITERION, edit.criterion().replace('-', '_'))));
        heading.setId("review-edit-criterion");
        heading.getStyleClass().add("finding-kind");
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

    private static Label diffLine(final String id, final String style, final String text) {
        final Label line = new Label(text);
        line.setId(id);
        line.getStyleClass().add(style);
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        return line;
    }

    private Label badgeChip(final FindingBadge badge) {
        final Label chip = new Label(messages.get(badge.label()));
        chip.setId("review-finding-badge");
        chip.getStyleClass().addAll("chip", "chip-warn");
        return chip;
    }

    private VBox plainRow(final QaFinding finding) {
        final Label kind = new Label(messages.get(
                MessageKey.REVIEW_FINDING_KIND,
                finding.kind(),
                finding.severity().name().toLowerCase(Locale.ROOT)));
        kind.getStyleClass().add("finding-kind");
        final HBox head = new HBox(SPACING / 2, kind);
        head.setAlignment(Pos.CENTER_LEFT);
        FindingBadge.of(List.of(finding), null)
                .filter(badge -> badge != FindingBadge.LOW_SCORE)
                .ifPresent(badge -> head.getChildren().add(badgeChip(badge)));
        final Label note = new Label(finding.note());
        note.getStyleClass().add("finding-note");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        final Label source = new Label(messages.get(MessageKey.REVIEW_RAISED_BY, finding.raisedBy()));
        source.getStyleClass().add("muted");
        return new VBox(2, head, note, source);
    }
}
