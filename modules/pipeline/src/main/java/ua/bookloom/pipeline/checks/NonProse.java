package ua.bookloom.pipeline.checks;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a line is only numbers and locators — digits, an identifier ({@link Identifiers}) or a DOI — which no language owns,
 * so the script, echo and language checks must not hold it against a translation (a copyright line was flagged
 * "wrong language" for being left as it was).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NonProse {

    private static final Pattern LABEL = Pattern.compile("(?i)\\bdoi:?\\s*\\S+|\\bisbn(-1[03])?\\b");

    /**
     * Whether {@code text} holds no word once its locators are taken out.
     *
     * @param text a display text
     * @return {@code true} when nothing but digits, locators and punctuation is left; {@code false} when a word is
     */
    public static boolean isLocatorOnly(final String text) {
        Objects.requireNonNull(text, "text");
        return withoutLocators(text).codePoints().noneMatch(Character::isLetter);
    }

    /**
     * The text with its URLs, e-mail addresses and ISBN labels blanked, for a check that counts words.
     *
     * @param text a display text
     * @return {@code text} with each locator replaced by a space
     */
    static String withoutLocators(final String text) {
        return LABEL.matcher(Identifiers.ANY.matcher(text).replaceAll(" ")).replaceAll(" ");
    }
}
