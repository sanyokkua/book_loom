package ua.bookloom.ui.i18n;

import java.util.Locale;

/**
 * The language the interface is displayed in, behind a seam so a test names it instead of depending on the machine
 * it runs on. Only the operating system chooses it in this build.
 */
public interface LocaleProvider {

    /**
     * Reads the display language at the moment of the call.
     *
     * @return {@code uk} or {@code en}, the only two languages a catalogue exists for
     */
    Locale displayLocale();
}
