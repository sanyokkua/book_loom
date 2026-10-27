package ua.bookloom.ui.i18n;

import java.util.Locale;

/** The operating system's language, behind a seam so a test names it instead of depending on the machine. */
@FunctionalInterface
public interface SystemLanguage {

    /**
     * Reads the language now.
     *
     * @return the locale the runtime took from the operating system
     */
    Locale current();
}
