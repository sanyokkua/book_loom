package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CheckSentences;
import ua.bookloom.ui.state.StageChip;
import ua.bookloom.ui.state.TestDetails;

/**
 * Draws one provider-test finding: the result sentence, and under it the reason a person needs to read.
 *
 * <p>A stage that passed says what it measured (the round trip, the model count, the inference time) in a sentence in
 * place of the word "passed", because one green tick cannot tell a 1.2-second model from a 40-second one; a failed
 * connection says who did not answer, and any other failed stage says its own error code.
 */
final class StageChipRows {

    private final Messages messages;

    StageChipRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Draws a finding into its result label and, under a failed or qualified one, its reason; returns the cell. */
    VBox fill(
            final Label result,
            final StageChip stage,
            final String providerName,
            final boolean localProgram,
            final VBox cell) {
        final ChipLook look = ChipLook.of(stage.status());
        result.setText(textOf(stage, look, providerName));
        result.getStyleClass().setAll("label", "check-result", "chip", look.styleClass());
        cell.getChildren().setAll(result);
        final String reason = reasonOf(stage, providerName, localProgram);
        if (reason != null) {
            cell.getChildren().add(detailOf(stage, reason));
        }
        return cell;
    }

    /** Draws the result label of a stage with no finding: plain words in the muted role. */
    void plain(final Label result, final String words, final VBox cell) {
        result.setText(words);
        result.getStyleClass().setAll("label", "check-result", "muted");
        cell.getChildren().setAll(result);
    }

    private String textOf(final StageChip stage, final ChipLook look, final String providerName) {
        return CheckSentences.of(messages, stage, providerName)
                .map(sentence -> look.glyph() + " " + sentence)
                .orElseGet(() -> messages.get(
                        MessageKey.SETTINGS_CHIP,
                        look.glyph(),
                        messages.get(ChipLook.nameOf(stage.stage())),
                        wordOf(stage, look)));
    }

    private String wordOf(final StageChip stage, final ChipLook look) {
        final AppError error = stage.error();
        return stage.status() == StageStatus.FAILED && error != null
                ? error.code().name()
                : messages.get(look.status());
    }

    private static Label detailOf(final StageChip stage, final String reason) {
        final Label detail = new Label(reason);
        detail.setId("chip-detail-" + stage.stage().name());
        detail.getStyleClass().add("hint");
        detail.setWrapText(true);
        return detail;
    }

    /** What a person needs to read under a chip: the failure for a failed stage, the qualifier for a soft pass. */
    private @Nullable String reasonOf(final StageChip stage, final String providerName, final boolean localProgram) {
        final AppError error = stage.error();
        final String errorMessage =
                error == null ? null : TestDetails.failureOf(messages, error, providerName, localProgram);
        return switch (stage.status()) {
            case FAILED -> errorMessage != null ? errorMessage : stage.note();
            case SOFT_PASS -> stage.note() != null ? stage.note() : errorMessage;
            case PASSED, SKIPPED -> null;
        };
    }
}
