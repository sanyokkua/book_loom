package ua.bookloom.util.text;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Removes the stress marks a model or a dictionary writes over a vowel ({@code Зо\u0301тов}), which match no text in a
 * book. The text is decomposed first so a precomposed grave ({@code \u0450}) goes too, and recomposed afterwards so the
 * marks that belong to a letter ({@code й}, {@code ї}) come back whole; NFC-first stripping would miss the grave forms
 * (design D4). Callers decide whether the target language asks for it; this class holds no language data.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StressMarks {

    /** The combining acute and grave accents, and the soft hyphen, which no stored spelling holds. */
    private static final Pattern COMBINING = Pattern.compile("[\u0301\u0300\u00AD]");

    /** A spacing acute is a stress mark only right after a Cyrillic vowel; elsewhere it may be an apostrophe. */
    private static final Pattern SPACING_ACUTE = Pattern.compile("(?<=[аеєиіїоуюяыэёАЕЄИІЇОУЮЯЫЭЁ])\u00B4");

    /**
     * The text without its stress marks.
     *
     * @param text the text as it came in; never null
     * @return the text with every acute and grave combining accent, every soft hyphen and every spacing acute after a
     *     Cyrillic vowel removed, in NFC; the text in NFC and otherwise unchanged when it held none
     */
    public static String strip(final String text) {
        Objects.requireNonNull(text, "text");
        final String composed = Normalizer.normalize(text, Normalizer.Form.NFC);
        final String noSpacing = SPACING_ACUTE.matcher(composed).replaceAll("");
        final String decomposed = Normalizer.normalize(noSpacing, Normalizer.Form.NFD);
        final String stripped = COMBINING.matcher(decomposed).replaceAll("");
        return Normalizer.normalize(stripped, Normalizer.Form.NFC);
    }

    /**
     * How many marks {@link #strip(String)} would remove, for a caller's log line.
     *
     * @param text the text as it came in; never null
     * @return the number of marks; zero when the text holds none
     */
    public static int count(final String text) {
        Objects.requireNonNull(text, "text");
        final String composed = Normalizer.normalize(text, Normalizer.Form.NFC);
        final int spacing = (int) SPACING_ACUTE.matcher(composed).results().count();
        final String decomposed =
                Normalizer.normalize(SPACING_ACUTE.matcher(composed).replaceAll(""), Normalizer.Form.NFD);
        return spacing + (int) COMBINING.matcher(decomposed).results().count();
    }
}
