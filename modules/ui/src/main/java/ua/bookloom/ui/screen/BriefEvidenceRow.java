package ua.bookloom.ui.screen;

import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The line under one suggested Book Brief field: the book's words the model quoted for it and how many of the samples
 * agree, or an "uncertain" chip when they did not, so the person sees why a field was filled or left alone. Hidden
 * while the field holds nothing the suggestion wrote.
 */
@Slf4j
final class BriefEvidenceRow {

    private static final double SPACING = 6;

    private final Messages messages;
    private final BriefField field;
    private final Label chip;
    private final Label line = new Label();
    private final HBox node;

    BriefEvidenceRow(final Messages messages, final BriefField field) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.field = Objects.requireNonNull(field, "field");
        final String id =
                "brief-evidence-" + field.name().toLowerCase(Locale.ROOT).replace('_', '-');
        final ChipLook look = ChipLook.uncertain();
        chip = new Label(look.glyph() + " " + messages.get(look.status()));
        chip.setId(id + "-uncertain");
        chip.getStyleClass().addAll("chip", look.styleClass());
        Tips.install(messages, chip, MessageKey.BRIEF_EVIDENCE_UNCERTAIN_TIP);
        line.setId(id);
        line.getStyleClass().add("hint");
        line.setWrapText(true);
        Tips.install(messages, line, MessageKey.BRIEF_EVIDENCE_AGREED_TIP);
        node = new HBox(SPACING, chip, line);
        node.setAlignment(Pos.CENTER_LEFT);
        show(null);
    }

    Node node() {
        return node;
    }

    static void shownIf(final Node shown, final boolean isShown) {
        shown.setVisible(isShown);
        shown.setManaged(isShown);
    }

    void show(final @Nullable FieldEvidence evidence) {
        final boolean uncertain = evidence != null && !evidence.isKept();
        shownIf(node, evidence != null);
        shownIf(line, evidence != null);
        shownIf(chip, uncertain);
        if (evidence == null) {
            return;
        }
        final String text = evidence.quotes().isEmpty() || uncertain
                ? messages.get(MessageKey.BRIEF_EVIDENCE_AGREED, evidence.agreeing(), evidence.samples())
                : messages.get(
                        MessageKey.BRIEF_EVIDENCE_QUOTED,
                        evidence.quotes().getFirst(),
                        evidence.agreeing(),
                        evidence.samples());
        // The card shows the brief on every keystroke; only a new evidence line is worth a log line.
        if (!text.equals(line.getText())) {
            log.debug(
                    "brief evidence of {}: {} of {} samples agree, {} quotes",
                    field,
                    evidence.agreeing(),
                    evidence.samples(),
                    evidence.quotes().size());
            line.setText(text);
        }
    }
}
