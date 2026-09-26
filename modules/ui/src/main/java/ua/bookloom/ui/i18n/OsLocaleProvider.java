package ua.bookloom.ui.i18n;

import com.google.inject.Inject;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Chooses Ukrainian for a Ukrainian operating system and English for every other one, because a catalogue exists
 * for no third language and a guess at one would show raw keys.
 */
@Slf4j
public final class OsLocaleProvider implements LocaleProvider {

    private static final String UKRAINIAN_LANGUAGE = "uk";

    private final Supplier<Locale> osLocale;

    /** Guice constructs this one; it reads the JVM default locale, which the runtime takes from the OS. */
    @Inject
    public OsLocaleProvider() {
        this(Locale::getDefault);
    }

    OsLocaleProvider(final Supplier<Locale> osLocale) {
        this.osLocale = Objects.requireNonNull(osLocale, "osLocale");
    }

    @Override
    public Locale displayLocale() {
        final Locale os = osLocale.get();
        if (UKRAINIAN_LANGUAGE.equals(os.getLanguage())) {
            log.info("OS locale {} is Ukrainian, displaying uk", os);
            return Locale.forLanguageTag(UKRAINIAN_LANGUAGE);
        }
        log.info("OS locale {} has no catalogue, displaying en", os);
        return Locale.ENGLISH;
    }
}
