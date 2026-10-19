package ua.bookloom.ui.notify;

import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The button a toast may carry.
 *
 * @param label the catalogue entry naming the button
 * @param tip the catalogue entry explaining it on hover
 * @param run what pressing it does, on the FX Application Thread
 */
public record ToastAction(MessageKey label, MessageKey tip, Runnable run) {

    /** Both parts are required. */
    public ToastAction {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(tip, "tip");
        Objects.requireNonNull(run, "run");
    }
}
