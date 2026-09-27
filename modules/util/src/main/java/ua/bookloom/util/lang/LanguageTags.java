package ua.bookloom.util.lang;

import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One normalizer for a raw language tag, so import, the Book Brief, the token estimator and the script check all
 * answer "which language is this code" the same way — a book spells one language as {@code EN}, {@code en-GB},
 * {@code en_GB} or the retired {@code ua} (backlog decision debt D17).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LanguageTags {

    private static final String RETIRED_UKRAINIAN_TAG = "ua";
    private static final String CHINESE_PRIMARY = "zh";
    private static final String CHINESE_SIMPLIFIED_TAG = "zh-Hans";
    private static final String CHINESE_TRADITIONAL_TAG = "zh-Hant";

    /**
     * Normalizes a raw declared or configured language tag to the exact tag of a catalogued {@link Language}.
     *
     * <p>Trims the input, treats {@code _} as {@code -}, lower-cases the primary subtag, maps the retired
     * {@code ua} to {@code uk}, folds every Chinese regional/script variant to {@code zh-Hans} or {@code zh-Hant},
     * and otherwise drops every subtag after the primary one.
     *
     * @param raw the raw tag, possibly {@code null}, blank, or naming no catalogued language (e.g. {@code "ar"})
     * @return the matching catalogued tag, or empty if {@code raw} is {@code null}/blank or names no
     *     {@link Language} in the catalogue
     */
    public static Optional<String> normalize(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        final String[] subtags = trimmed.replace('_', '-').split("-");
        final String primary = subtags[0].toLowerCase(Locale.ROOT);
        final String candidate = candidateTag(primary, subtags);
        return Languages.byTag(candidate).isPresent() ? Optional.of(candidate) : Optional.empty();
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
