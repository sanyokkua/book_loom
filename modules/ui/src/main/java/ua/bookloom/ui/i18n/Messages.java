package ua.bookloom.ui.i18n;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@link MessageKey} and its arguments to display text.
 *
 * <p>The locale is read once, at first use: a language that changed mid-run would leave half a screen in each. ICU
 * {@code MessageFormat} rather than {@link java.text.MessageFormat} because only ICU has {@code plural}, which
 * Ukrainian needs in four forms; English is the fallback bundle because it is the base language every key is first
 * written in.
 */
@Slf4j
@Singleton
public final class Messages {

    private static final String BASE_NAME = "ua.bookloom.ui.i18n.messages";

    private final LocaleProvider localeProvider;
    private final ReentrantLock decision = new ReentrantLock();
    private volatile @Nullable Resolved resolved;

    /**
     * Keeps the provider; the language is decided at the first use, not here, because the injector builds this class
     * before startup has read the stored language choice.
     *
     * @param localeProvider where the display locale comes from
     */
    @Inject
    public Messages(final LocaleProvider localeProvider) {
        this.localeProvider = Objects.requireNonNull(localeProvider, "localeProvider");
    }

    /** The language and the two catalogues, decided together once and then never changed. */
    private record Resolved(Locale locale, ResourceBundle active, ResourceBundle english) {}

    private Resolved resolved() {
        final Resolved done = resolved;
        if (done != null) {
            return done;
        }
        decision.lock();
        try {
            if (resolved == null) {
                resolved = decide();
            }
            return Objects.requireNonNull(resolved, "resolved");
        } finally {
            decision.unlock();
        }
    }

    private Resolved decide() {
        final Locale locale = localeProvider.displayLocale();
        final Resolved decided = new Resolved(
                locale,
                ResourceBundle.getBundle(BASE_NAME, locale),
                ResourceBundle.getBundle(BASE_NAME, Locale.ENGLISH));
        log.info(
                "display locale {} chosen by {}",
                locale,
                localeProvider.getClass().getSimpleName());
        return decided;
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
        final Resolved now = resolved();
        return format(now.locale(), key, patternFor(now, key), args);
    }

    /**
     * Words for a machine code the pipeline reports (a reason, a rule), from a message that selects on the code. ICU
     * select keywords cannot hold a dash, so the code is lower-cased and its dashes turned into underscores to select;
     * the code as reported is the message's second argument, which its {@code other} branch shows.
     *
     * @param key a message whose argument 0 selects on the normalised code and whose {@code other} branch shows {1}
     * @param code the code as reported, for example {@code MISSING} or {@code latin-run}
     * @return the words of the interface language, or the code itself when the message has none for it
     */
    public String code(final MessageKey key, final String code) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(code, "code");
        final String selector = code.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        return get(key, selector.isEmpty() ? "none" : selector, code);
    }

    /**
     * Returns the display locale.
     *
     * @return the locale every message is rendered for
     */
    public Locale locale() {
        return resolved().locale();
    }

    /**
     * Returns the active catalogue itself, for an FXML loader whose {@code %key} references must resolve against the
     * same entries {@link #get} renders.
     *
     * @return the bundle for the display locale, falling back to English through the standard bundle chain
     */
    public ResourceBundle bundle() {
        return resolved().active();
    }

    private String patternFor(final Resolved now, final MessageKey key) {
        try {
            return now.active().getString(key.key());
        } catch (MissingResourceException missingInActive) {
            log.debug("{} is missing for {}, resolving it for the English catalogue", key.key(), now.locale());
            return englishPatternFor(now, key);
        }
    }

    private String englishPatternFor(final Resolved now, final MessageKey key) {
        try {
            return now.english().getString(key.key());
        } catch (MissingResourceException missingEverywhere) {
            log.warn("{} is in no catalogue, displaying the raw key", key.key());
            return key.key();
        }
    }

    private String format(final Locale locale, final MessageKey key, final String pattern, final Object[] args) {
        try {
            return new MessageFormat(pattern, ULocale.forLocale(locale)).format(args);
        } catch (IllegalArgumentException e) {
            log.warn("{} is not a valid ICU pattern, displaying it unformatted", key.key(), e);
            return pattern;
        }
    }
}
