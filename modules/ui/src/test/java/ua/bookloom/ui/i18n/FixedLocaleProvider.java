package ua.bookloom.ui.i18n;

import java.util.Locale;
import java.util.Objects;

/**
 * Hand-written stand-in for "the operating system's language is X", so a message test names the display locale
 * instead of depending on the machine it runs on.
 */
final class FixedLocaleProvider implements LocaleProvider {

    private final Locale locale;

    FixedLocaleProvider(final Locale locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    @Override
    public Locale displayLocale() {
        return locale;
    }
}
