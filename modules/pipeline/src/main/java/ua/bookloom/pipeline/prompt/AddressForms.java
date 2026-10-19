package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The forms of the informal and the formal "you" that a language's file lists in its {@code informalAddress} and
 * {@code formalAddress} keys, so the address-mix check is chosen by data and never by a hard-coded tag.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AddressForms {

    /**
     * The informal forms of a language.
     *
     * @param languageTag the non-null language tag
     * @return the forms as written (lower case); never null, empty when the language lists none
     */
    public static Set<String> informal(final String languageTag) {
        return Set.copyOf(
                LanguageRules.bundled().wordsOf(Objects.requireNonNull(languageTag, "languageTag"), "informalAddress"));
    }

    /**
     * The formal forms of a language, listed capitalised because a lower-case form is as often a plural.
     *
     * @param languageTag the non-null language tag
     * @return the forms as written (capitalised); never null, empty when the language lists none
     */
    public static Set<String> formal(final String languageTag) {
        return Set.copyOf(
                LanguageRules.bundled().wordsOf(Objects.requireNonNull(languageTag, "languageTag"), "formalAddress"));
    }
}
