/**
 * The message catalogue: every visible word is a {@link ua.bookloom.ui.i18n.MessageKey} resolved through ICU
 * {@code MessageFormat} for the one display language chosen from the operating system at startup.
 *
 * <p>This package builds no control that changes the language; the switch is a deferred feature. It touches no scene
 * graph, so it is safe to call from any thread.
 */
@NullMarked
package ua.bookloom.ui.i18n;

import org.jspecify.annotations.NullMarked;
