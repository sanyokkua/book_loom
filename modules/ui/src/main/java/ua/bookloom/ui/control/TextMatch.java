package ua.bookloom.ui.control;

import java.text.Normalizer;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** One way of comparing typed text with a name, shared by the searchable list and the language parser. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextMatch {

    /**
     * Normalises text for matching.
     *
     * @param text any text
     * @return the text decomposed, stripped of combining marks and lower-cased with the root locale, so
     *     {@code Bokmal} matches {@code Bokmål}
     */
    public static String normalize(final String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
