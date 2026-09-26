package ua.bookloom.ui.notify;

import ua.bookloom.ui.i18n.MessageKey;

/**
 * The transient-message surface, one method per severity.
 *
 * <p>An interface, not the stack itself, so a screen's test can hand it a recording fake; the message is a catalogue
 * key plus arguments so no caller resolves text on its own. FX Application Thread only.
 */
public interface Toasts {

    /**
     * Raises a success-severity message.
     *
     * @param key the catalogue entry to show
     * @param args the arguments the entry's pattern names, in order
     */
    void success(MessageKey key, Object... args);

    /**
     * Raises an information-severity message.
     *
     * @param key the catalogue entry to show
     * @param args the arguments the entry's pattern names, in order
     */
    void info(MessageKey key, Object... args);

    /**
     * Raises a warning-severity message.
     *
     * @param key the catalogue entry to show
     * @param args the arguments the entry's pattern names, in order
     */
    void warning(MessageKey key, Object... args);

    /**
     * Raises an error-severity message.
     *
     * @param key the catalogue entry to show
     * @param args the arguments the entry's pattern names, in order
     */
    void error(MessageKey key, Object... args);
}
