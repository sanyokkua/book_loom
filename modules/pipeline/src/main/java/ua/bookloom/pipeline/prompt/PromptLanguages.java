package ua.bookloom.pipeline.prompt;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The one place a BCP-47 language tag becomes the English phrase a prompt names it by, so the draft call and the
 * reviewer call never describe the same language pair two different ways.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptLanguages {

    private static final String UNKNOWN_LANGUAGE = "the language of this segment (infer it from its text)";

    /**
     * Describes a language tag the way every prompt names it.
     *
     * @param languageTag the BCP-47 tag, or null when the source language is unknown
     * @return the English display name plus the tag (e.g. {@code "English (en)"}), a quoted-tag fallback for an
     *     unregistered tag, or the fixed inference instruction when {@code languageTag} is null
     */
    public static String describe(@Nullable final String languageTag) {
        if (languageTag == null) {
            return UNKNOWN_LANGUAGE;
        }
        final String displayName = Locale.forLanguageTag(languageTag).getDisplayName(Locale.ENGLISH);
        if (displayName.isBlank() || displayName.equalsIgnoreCase(languageTag)) {
            return "language tag \"" + languageTag + "\"";
        }
        return displayName + " (" + languageTag + ")";
    }
}
