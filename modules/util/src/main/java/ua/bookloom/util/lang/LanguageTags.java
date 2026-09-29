package ua.bookloom.util.lang;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One normalizer for a raw language tag, so import, the Book Brief, the token estimator and the script check all
 * answer "which language is this code" the same way — a book spells one language as {@code EN}, {@code en-GB},
 * {@code en_GB} or the retired {@code ua} (backlog decision debt D17).
 *
 * <p>Languages are open: the {@link Language} catalogue is a convenience list, and any well-formed tag the JDK can
 * name in English ({@code la}, {@code haw}, {@code ar}) is a recognized language too.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LanguageTags {

    private static final String RETIRED_UKRAINIAN_TAG = "ua";
    private static final String CHINESE_PRIMARY = "zh";
    private static final String CHINESE_SIMPLIFIED_TAG = "zh-Hans";
    private static final String CHINESE_TRADITIONAL_TAG = "zh-Hant";
    // Codes the JDK names, but which say "no particular language" rather than name one.
    private static final Set<String> GENERIC_NON_LANGUAGES = Set.of("und", "mul", "zxx", "mis");

    /**
     * Normalizes a raw declared or configured language tag to one canonical tag.
     *
     * <p>Trims the input, treats {@code _} as {@code -}, lower-cases the primary subtag, maps the retired
     * {@code ua} to {@code uk}, folds every Chinese regional/script variant to {@code zh-Hans} or {@code zh-Hant},
     * and otherwise drops every subtag after the primary one. A tag outside the catalogue is accepted when it is
     * well-formed and the JDK names its language in English; the answer is then the JDK's lower-case language code.
     *
     * @param raw the raw tag, possibly {@code null}, blank, ill-formed, or naming no language ({@code "xx"})
     * @return the catalogued tag, or the JDK's language code for another language it can name, or empty if
     *     {@code raw} is {@code null}/blank, ill-formed, or names no language
     */
    public static Optional<String> normalize(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        final String hyphenated = trimmed.replace('_', '-');
        final String[] subtags = hyphenated.split("-");
        final String primary = subtags[0].toLowerCase(Locale.ROOT);
        final String candidate = candidateTag(primary, subtags);
        if (Languages.byTag(candidate).isPresent()) {
            return Optional.of(candidate);
        }
        return jdkLanguage(hyphenated);
    }

    private static Optional<String> jdkLanguage(String hyphenated) {
        final Locale locale;
        try {
            locale = new Locale.Builder().setLanguageTag(hyphenated).build();
        } catch (IllformedLocaleException e) {
            return Optional.empty();
        }
        final String code = locale.getLanguage();
        final String name = locale.getDisplayLanguage(Locale.ENGLISH);
        final boolean named = !name.isBlank() && !name.equalsIgnoreCase(code) && !GENERIC_NON_LANGUAGES.contains(code);
        return named ? Optional.of(code) : Optional.empty();
    }

    private static String candidateTag(String primary, String[] subtags) {
        if (primary.equals(CHINESE_PRIMARY)) {
            return chineseTag(subtags);
        }
        if (primary.equals(RETIRED_UKRAINIAN_TAG)) {
            return "uk";
        }
        return primary;
    }

    private static String chineseTag(String[] subtags) {
        final String region = subtags.length > 1 ? subtags[1].toLowerCase(Locale.ROOT) : "";
        final boolean simplified =
                region.isEmpty() || region.equals("cn") || region.equals("sg") || region.startsWith("hans");
        if (simplified) {
            return CHINESE_SIMPLIFIED_TAG;
        }
        final boolean traditional =
                region.equals("tw") || region.equals("hk") || region.equals("mo") || region.startsWith("hant");
        return traditional ? CHINESE_TRADITIONAL_TAG : CHINESE_PRIMARY;
    }
}
