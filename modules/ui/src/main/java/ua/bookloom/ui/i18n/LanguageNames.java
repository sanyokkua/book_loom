package ua.bookloom.ui.i18n;

import com.google.inject.Singleton;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.TextMatch;
import ua.bookloom.util.lang.Language;
import ua.bookloom.util.lang.LanguageTags;
import ua.bookloom.util.lang.Languages;

/**
 * Names languages for the interface: lists the catalogued languages by name, names any recognised tag in the
 * interface language, and turns a typed name or tag back into a tag.
 *
 * <p>The 34 catalogued languages are a convenience, never a limit: a tag the JDK can name ({@code la}) is listed by
 * {@link #nameOf} and accepted by {@link #parse} exactly like a catalogued one. Names are indexed lazily per locale in
 * an instance map.
 */
@Slf4j
@Singleton
public final class LanguageNames {

    private final Map<Locale, Map<String, String>> tagsByName = new ConcurrentHashMap<>();

    /**
     * Lists the catalogued languages.
     *
     * @param locale the language the names are sorted in
     * @return the 34 catalogue tags sorted by their names in {@code locale}; never null
     */
    public List<String> list(final Locale locale) {
        Objects.requireNonNull(locale, "locale");
        final Collator collator = Collator.getInstance(ULocale.forLocale(locale));
        final List<String> tags = Languages.all().stream()
                .map(Language::tag)
                .sorted(Comparator.comparing(tag -> nameOf(tag, locale), collator::compare))
                .toList();
        log.debug("language list built for {} with {} entries", locale, tags.size());
        return tags;
    }

    /**
     * Names a language.
     *
     * @param tag a normalized language tag, catalogued or not
     * @param locale the language the name is written in
     * @return the catalogue's English name for a catalogued tag under English, otherwise the ICU name in
     *     {@code locale}; the catalogue's English name or the tag itself when ICU has no name; never null
     */
    public String nameOf(final String tag, final Locale locale) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(locale, "locale");
        final Optional<Language> catalogued = Languages.byTag(tag);
        if (catalogued.isPresent() && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            return catalogued.get().displayName();
        }
        final String icu = ULocale.forLanguageTag(tag).getDisplayName(ULocale.forLocale(locale));
        if (!icu.isBlank() && !icu.equalsIgnoreCase(tag)) {
            return icu;
        }
        return catalogued.map(Language::displayName).orElse(tag);
    }

    /**
     * Turns a typed name or tag into a tag.
     *
     * @param text what the person typed; a name in the interface language or in English, or a tag such as {@code la}
     *     or {@code en-US}
     * @param locale the interface language
     * @return the language's tag, or empty for blank text and for text naming no recognised language
     */
    public Optional<String> parse(final @Nullable String text, final Locale locale) {
        Objects.requireNonNull(locale, "locale");
        if (text == null || text.isBlank()) {
            log.debug("language parse of blank text: empty");
            return Optional.empty();
        }
        final Optional<String> byName = Optional.ofNullable(index(locale).get(TextMatch.normalize(text.strip())));
        final Optional<String> tag = byName.or(() -> LanguageTags.normalize(text));
        log.debug("language parse in {}: {}", locale, tag.isPresent() ? "recognised " + tag.get() : "not recognised");
        log.trace("language parse text '{}'", text);
        return tag;
    }

    private Map<String, String> index(final Locale locale) {
        return tagsByName.computeIfAbsent(locale, this::buildIndex);
    }

    private Map<String, String> buildIndex(final Locale locale) {
        final Map<String, String> byName = new HashMap<>();
        for (final Locale names : List.of(locale, Locale.ENGLISH)) {
            Languages.all().forEach(language -> byName.putIfAbsent(key(nameOf(language.tag(), names)), language.tag()));
        }
        for (final Locale names : List.of(locale, Locale.ENGLISH)) {
            for (final String code : ULocale.getISOLanguages()) {
                final String tag = LanguageTags.normalize(code).orElse(code);
                byName.putIfAbsent(key(ULocale.forLanguageTag(code).getDisplayName(ULocale.forLocale(names))), tag);
            }
        }
        log.debug("language names indexed for {} with {} names", locale, byName.size());
        return Map.copyOf(byName);
    }

    private static String key(final String name) {
        return TextMatch.normalize(name);
    }
}
