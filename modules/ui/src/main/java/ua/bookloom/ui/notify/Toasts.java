package ua.bookloom.ui.notify;

import ua.bookloom.ui.i18n.MessageKey;

/**
 * The transient-message surface: a message only for a named {@link ToastOccasion}, and one footer line for a dialog.
 *
 * <p>An interface, not the stack itself, so a screen's test can hand it a recording fake; the message is a catalogue
 * key plus arguments so no caller resolves text on its own. While a dialog or visible model work is open, an occasion
 * is shown as that dialog's footer line instead of a toast, because a corner message would land on its buttons or
 * behind its scrim. FX Application Thread only.
 */
public interface Toasts {

    /**
     * Raises the message of an occasion.
     *
     * @param occasion what happened; it fixes the severity and the wording
     * @param args the arguments the occasion's pattern names, in order
     */
    void raise(ToastOccasion occasion, Object... args);

    /**
     * Raises the message of an occasion with a button that runs an action, for a message the person can act on at
     * once. Such a message stays as long as a warning. As a footer line the action is left out.
     *
     * @param occasion what happened; it fixes the severity and the wording
     * @param action the button's label and what pressing it does; the message goes away when it is pressed
     * @param args the arguments the occasion's pattern names, in order
     */
    void raiseWithAction(ToastOccasion occasion, ToastAction action, Object... args);

    /**
     * Says what happened in the open dialog as one line in its footer, and says nothing where no dialog is open, since
     * the screen then shows the result itself.
     *
     * @param severity the status role that colours the line
     * @param key the catalogue entry to show
     * @param args the arguments the entry's pattern names, in order
     */
    void feedback(Severity severity, MessageKey key, Object... args);
}
