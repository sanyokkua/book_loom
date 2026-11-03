package ua.bookloom.llm.knowledge;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Folds a language tag to the group a vendor's list is compared in. A vendor writes {@code no} for what a book tags
 * {@code nb} or {@code nn}, and {@code iw} is the old code of {@code he}, so a plain tag compare would report a gap
 * that is not there.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LanguageGroups {

    private static final String NORWEGIAN = "no";

    /**
     * The group of a tag.
     *
     * @param tag any BCP-47 or underscore-separated tag; letter case and region or script are ignored
     * @return the lower-case primary subtag, with {@code iw} read as {@code he} and {@code nb}, {@code nn} as {@code no}
     */
    static String groupOf(final String tag) {
        final String primary = tag.strip().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return switch (primary) {
            case "iw" -> "he";
            case "nb", "nn" -> NORWEGIAN;
            default -> primary;
        };
    }
}
