package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunStateView.Text;

/**
 * Why a model action waits for other model work, in the words the README gives the two common cases: a translation
 * at work holds every other model action ("Pause the translation first — BookLoom sends one model request at a time"),
 * and the names pass holds Start translation ("Wait until the names pass ends"). Any other conflict names the work.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockedWords {

    /**
     * The reason, as a text.
     *
     * @param blocker the work that holds the action
     * @param wanted the work the action would start
     * @return the reason; never null
     */
    public static Text why(final ActivityKind blocker, final ActivityKind wanted) {
        Objects.requireNonNull(blocker, "blocker");
        Objects.requireNonNull(wanted, "wanted");
        if (blocker == ActivityKind.TRANSLATION) {
            return Text.of(MessageKey.MODEL_BLOCKED_PAUSE_FIRST);
        }
        if (blocker == ActivityKind.NAMES_PASS && wanted == ActivityKind.TRANSLATION) {
            return Text.of(MessageKey.START_BLOCKED_NAMES_PASS);
        }
        return Text.of(MessageKey.ACTIVITY_BLOCKED, blocker.label());
    }

    /**
     * The reason, worded.
     *
     * @param messages the catalogue
     * @param blocker the work that holds the action
     * @param wanted the work the action would start
     * @return the worded reason; never null
     */
    public static String in(final Messages messages, final ActivityKind blocker, final ActivityKind wanted) {
        return why(blocker, wanted).in(messages);
    }
}
