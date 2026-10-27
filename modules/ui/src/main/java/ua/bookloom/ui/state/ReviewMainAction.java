package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the review desk's one main button says and whether it can be pressed: Accept while the text is the stored one,
 * Save my edit once the person has typed (or the editor holds a refused reply only a save keeps).
 *
 * @param key {@link MessageKey#REVIEW_MAIN_ACCEPT} or {@link MessageKey#REVIEW_MAIN_SAVE}
 * @param enabled whether the button runs its action now
 */
public record ReviewMainAction(MessageKey key, boolean enabled) {

    /** Rejects a missing key. */
    public ReviewMainAction {
        Objects.requireNonNull(key, "key");
    }

    /**
     * The button for the editor and the actions as they stand. Logs nothing: it is worked out on every keystroke.
     *
     * @param changed whether the editor holds something Save my edit keeps
     * @param complete whether the text can be stored: every formatting mark in place once, and not blank
     * @param actions whether the actions on the selected segment are offered
     * @param accept whether Accept is offered
     * @return Save my edit when there is something to save, enabled only while the actions are offered and no mark is
     *     missing or doubled, so an unfinished edit never falls back to Accept; else Accept
     */
    static ReviewMainAction of(
            final boolean changed, final boolean complete, final boolean actions, final boolean accept) {
        return changed
                ? new ReviewMainAction(MessageKey.REVIEW_MAIN_SAVE, actions && complete)
                : new ReviewMainAction(MessageKey.REVIEW_MAIN_ACCEPT, accept);
    }

    /**
     * The button's word when a review pause continues the run after it: "& continue" instead of "& next".
     *
     * @param continues whether a decision on this segment resumes the run
     * @return the catalogue entry the button shows
     */
    public MessageKey wording(final boolean continues) {
        if (!continues) {
            return key;
        }
        return key == MessageKey.REVIEW_MAIN_SAVE
                ? MessageKey.REVIEW_MAIN_SAVE_CONTINUE
                : MessageKey.REVIEW_MAIN_ACCEPT_CONTINUE;
    }

    /** Whether pressing the button saves the editor's text rather than accepting the stored translation. */
    public boolean isSave() {
        return key == MessageKey.REVIEW_MAIN_SAVE;
    }
}
