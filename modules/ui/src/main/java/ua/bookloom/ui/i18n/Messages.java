package ua.bookloom.ui.i18n;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a {@link MessageKey} and its arguments to display text.
 *
 * <p>The locale is read once: a language that changed mid-run would leave half a screen in each. ICU
 * {@code MessageFormat} rather than {@link java.text.MessageFormat} because only ICU has {@code plural}, which
 * Ukrainian needs in four forms; English is the fallback bundle because it is the base language every key is first
 * written in.
 */
@Slf4j
@Singleton
public final class Messages {

    private static final String BASE_NAME = "ua.bookloom.ui.i18n.messages";

    private final Locale locale;
    private final ResourceBundle active;
    private final ResourceBundle english;

    /**
     * Resolves the display locale and loads its catalogue and the English one.
     *
     * @param localeProvider where the display locale comes from
     */
    @Inject
    public Messages(final LocaleProvider localeProvider) {
        Objects.requireNonNull(localeProvider, "localeProvider");
        this.locale = localeProvider.displayLocale();
        this.active = ResourceBundle.getBundle(BASE_NAME, locale);
        this.english = ResourceBundle.getBundle(BASE_NAME, Locale.ENGLISH);
        log.info(
                "display locale {} chosen by {}",
                locale,
                localeProvider.getClass().getSimpleName());
    }

    /**
     * Renders one catalogue entry.
     *
     * @param key the entry to render
     * @param args positional ICU arguments; numbers stay numbers so {@code plural} and grouping follow the display
     *     language, and a text such as a segment id or a failure's own message is passed as data, never joined
     * @return the text for the display locale; the English text when this language lacks the key, or the raw key
     *     when no catalogue has it
     */
    public String get(final MessageKey key, final Object... args) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(args, "args");
        // No line per resolution: the activity list resolves every visible row on every event, and two lines each
        // drowned the log; the two branches worth a line are the missing key and the invalid pattern below.
        return format(key, patternFor(key), args);
    }

    /**
     * Returns the display locale.
     *
     * @return the locale every message is rendered for
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns the active catalogue itself, for an FXML loader whose {@code %key} references must resolve against the
     * same entries {@link #get} renders.
     *
     * @return the bundle for the display locale, falling back to English through the standard bundle chain
     */
    public ResourceBundle bundle() {
        return active;
    }

    private String patternFor(final MessageKey key) {
        try {
            return active.getString(key.key());
        } catch (MissingResourceException missingInActive) {
            log.debug("{} is missing for {}, resolving it for the English catalogue", key.key(), locale);
            return englishPatternFor(key);
        }
    }

    private String englishPatternFor(final MessageKey key) {
        try {
            return english.getString(key.key());
        } catch (MissingResourceException missingEverywhere) {
            log.warn("{} is in no catalogue, displaying the raw key", key.key());
            return key.key();
        }
    }

    private String format(final MessageKey key, final String pattern, final Object[] args) {
        try {
            return new MessageFormat(pattern, ULocale.forLocale(locale)).format(args);
        } catch (IllegalArgumentException e) {
            log.warn("{} is not a valid ICU pattern, displaying it unformatted", key.key(), e);
            return pattern;
        }
    }
}
