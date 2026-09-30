package ua.bookloom.ui.screen;

import java.time.Duration;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StageChip;

/**
 * Draws one provider-test finding: the chip, and under it the reason a person needs to read.
 *
 * <p>A stage that passed says what it measured (the round trip, the model count, the inference time) in place of the
 * word "passed", because one green tick cannot tell a 1.2-second model from a 40-second one; a failed stage says its
 * own error code.
 */
final class StageChipRows {

    private static final double DETAIL_MAX_WIDTH = 280;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final double TENTHS_PER_SECOND = 10.0;

    private final Messages messages;

    StageChipRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    VBox rowOf(final StageChip stage) {
        final ChipLook look = ChipLook.of(stage.status());
        final Label chip = new Label(textOf(stage, look));
        chip.setId("chip-" + stage.stage().name());
        chip.getStyleClass().addAll("chip", look.styleClass());
        final VBox row = new VBox(4, chip);
        row.setId("chip-row-" + stage.stage().name());
        row.setMaxWidth(DETAIL_MAX_WIDTH);
        final String reason = reasonOf(stage);
        if (reason != null) {
            row.getChildren().add(detailOf(stage, reason));
        }
        return row;
    }

    private String textOf(final StageChip stage, final ChipLook look) {
        final String measured = stage.status() == StageStatus.PASSED ? measuredOf(stage) : null;
        if (measured != null) {
            return look.glyph() + " " + measured;
        }
        return messages.get(
                MessageKey.SETTINGS_CHIP,
                look.glyph(),
                messages.get(ChipLook.nameOf(stage.stage())),
                wordOf(stage, look));
    }

    private String wordOf(final StageChip stage, final ChipLook look) {
        final AppError error = stage.error();
        return stage.status() == StageStatus.FAILED && error != null
                ? error.code().name()
                : messages.get(look.status());
    }

    private @Nullable String measuredOf(final StageChip stage) {
        final Duration elapsed = stage.elapsed();
        final Integer count = stage.count();
        return switch (stage.stage()) {
            case CONNECTION ->
                elapsed == null ? null : messages.get(MessageKey.SETTINGS_BADGE_REACHABLE, durationOf(elapsed));
            case MODELS -> count == null ? null : messages.get(MessageKey.SETTINGS_BADGE_MODELS, count);
            case INFERENCE ->
                elapsed == null ? null : messages.get(MessageKey.SETTINGS_BADGE_INFERENCE, durationOf(elapsed));
        };
    }

    private String durationOf(final Duration elapsed) {
        final long millis = elapsed.toMillis();
        return millis < MILLIS_PER_SECOND
                ? messages.get(MessageKey.SETTINGS_DURATION_MS, millis)
                : messages.get(
                        MessageKey.SETTINGS_DURATION_S,
                        Math.round(millis / (double) MILLIS_PER_SECOND * TENTHS_PER_SECOND) / TENTHS_PER_SECOND);
    }

    private static Label detailOf(final StageChip stage, final String reason) {
        final Label detail = new Label(reason);
        detail.setId("chip-detail-" + stage.stage().name());
        detail.getStyleClass().add("hint");
        detail.setWrapText(true);
        return detail;
    }

    /** What a person needs to read under a chip: the failure for a failed stage, the qualifier for a soft pass. */
    private static @Nullable String reasonOf(final StageChip stage) {
        final AppError error = stage.error();
        final String errorMessage = error == null ? null : error.message();
        return switch (stage.status()) {
            case FAILED -> errorMessage != null ? errorMessage : stage.note();
            case SOFT_PASS -> stage.note() != null ? stage.note() : errorMessage;
            case PASSED, SKIPPED -> null;
        };
    }
}
