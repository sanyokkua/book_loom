package ua.bookloom.ui.i18n;

import com.google.inject.Inject;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Chooses the display language: the stored choice when there is one, else Ukrainian for a Ukrainian operating system
 * and English for every other one, because a catalogue exists for no third language and a guess at one would show raw
 * keys.
 */
@Slf4j
public final class OsLocaleProvider implements LocaleProvider {

    private static final String UKRAINIAN_LANGUAGE = "uk";

    private final Supplier<Optional<String>> stored;
    private final BooleanSupplier storedRead;
    private final Supplier<Locale> osLocale;

    /**
     * Guice constructs this one.
     *
     * @param stored the early copy of the stored language choice
     * @param system the operating system's language
     */
    @Inject
    public OsLocaleProvider(final StoredLanguage stored, final SystemLanguage system) {
        this(
                Objects.requireNonNull(stored, "stored")::get,
                stored::isSeeded,
                Objects.requireNonNull(system, "system")::current);
    }

    OsLocaleProvider(final Supplier<Optional<String>> stored, final Supplier<Locale> osLocale) {
        this(stored, () -> true, osLocale);
    }

    private OsLocaleProvider(
            final Supplier<Optional<String>> stored,
            final BooleanSupplier storedRead,
            final Supplier<Locale> osLocale) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.storedRead = Objects.requireNonNull(storedRead, "storedRead");
        this.osLocale = Objects.requireNonNull(osLocale, "osLocale");
    }

    @Override
    public Locale displayLocale() {
        final Locale os = osLocale.get();
        final LanguageChoice choice = LanguageChoice.of(stored.get());
        if (choice != LanguageChoice.SYSTEM) {
            log.info(
                    "display language {} comes from the stored choice",
                    choice.tag().orElse(""));
            return choice.resolve(os);
        }
        if (!storedRead.getAsBoolean()) {
            log.info("display language decided before the stored choice was read; it applies from the next launch");
        }
        final Locale chosen = forOs(os);
        log.info("display language {} comes from the operating system locale {}", chosen.getLanguage(), os);
        return chosen;
    }

    /**
     * The language an operating system's locale chooses.
     *
     * @param os the operating system's locale; never null
     * @return Ukrainian for a Ukrainian one, English otherwise
     */
    public static Locale forOs(final Locale os) {
        if (UKRAINIAN_LANGUAGE.equals(Objects.requireNonNull(os, "os").getLanguage())) {
            return Locale.forLanguageTag(UKRAINIAN_LANGUAGE);
        }
        return Locale.ENGLISH;
    }
}
