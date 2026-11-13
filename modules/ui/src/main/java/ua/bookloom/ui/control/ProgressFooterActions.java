package ua.bookloom.ui.control;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.ActivityKind;

/**
 * The progress dialog's footer actions beyond Cancel and Continue in background: the button that takes a held answer,
 * named for what it takes.
 */
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
}
