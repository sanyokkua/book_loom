package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.ActivityKind;

/**
 * The progress dialog's footer actions beyond Cancel and Continue in background: the button that takes a held answer,
 * named for what it takes, and the export's "Write the file now".
 */
@Slf4j
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProgressFooterActions {

    /**
     * The label of the button that takes a held answer.
     *
     * @param kind the work
     * @return the label, or null for work whose answer needs no decision
     */
    static @Nullable MessageKey applyLabel(final ActivityKind kind) {
        return switch (kind) {
            case SUGGEST_STYLE -> MessageKey.BRIEF_APPLY;
            case SUGGEST_NAME -> MessageKey.EXPORT_SUGGEST_NAME_USE;
            default -> null;
        };
    }

    /**
     * The hover explanation of that button.
     *
     * @param kind the work
     * @return the explanation; the brief's for any work but the file name
     */
    static MessageKey applyTip(final ActivityKind kind) {
        return kind == ActivityKind.SUGGEST_NAME ? MessageKey.EXPORT_SUGGEST_NAME_USE_TIP : MessageKey.BRIEF_APPLY_TIP;
    }

    /**
     * Adds "Write the file now" to the export dialog's footer.
     *
     * @param dialog the dialog
     * @param messages the catalogue its words come from
     * @param onWriteNow what the button does: ask first, then end the last check
     */
    static void addWriteNow(
            final ProgressDialog dialog, final ua.bookloom.ui.i18n.Messages messages, final Runnable onWriteNow) {
        Objects.requireNonNull(onWriteNow, "onWriteNow");
        final ButtonType type = new ButtonType(messages.get(MessageKey.EXPORT_WRITE_NOW), ButtonBar.ButtonData.OK_DONE);
        dialog.getButtonTypes().add(type);
        final Button button = (Button) dialog.lookupButton(type);
        button.setId("progress-write-now");
        button.getStyleClass().add("btn-primary");
        Tips.install(messages, button, MessageKey.EXPORT_WRITE_NOW_TIP);
        button.setOnAction(event -> {
            log.info("progress dialog: write the file now pressed");
            onWriteNow.run();
        });
    }
}
