package ua.bookloom.pipeline.heal;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.util.text.VisibleText;

/**
 * Why a segment is kept as it is instead of being sent to the model: what a reader sees in it — its text with every
 * {@code ⟦gN⟧} token removed, NFC-normalized, invisible characters dropped — is something a translation would only
 * copy back. Sending it cost a full draft call (the chapter number {@code 2}: 740 prompt tokens), and a model asked to
 * translate a lone numeral replies with an echo the echo check then holds against it.
 */
public enum VerbatimRule {

    /** Nothing visible is left: the segment is only a kept foreign run or other protected text. */
    NO_TEXT,

    /** Only a locked glossary name, with at most digits or punctuation around it — written as its locked rendering. */
    LOCKED_TERM,

    /** Only digits, punctuation and symbols — a chapter number {@code 2}, a scene break {@code ***}, {@code § 3}. */
    SYMBOLS,

    /** An upper-case Roman numeral, with at most punctuation around it — {@code XIV}, {@code IV.}. */
    ROMAN_NUMERAL,

    /** A single visible character — a section letter {@code A}. */
    SINGLE_CHARACTER,

    /**
     * An equation of one-letter symbols — {@code F = G × (m₁ × m₂) / r²}, {@code g = 9.81 m/s²}: every run of letters is
     * one letter and an equals sign holds it together. A model echoes it, which the echo check then flags.
     */
    FORMULA;

    /** A run of two or more letters: a word, which a formula holds none of. */
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");

    // Upper case only: lower-case runs of Roman letters are ordinary words too often ("mix", "vi", "di").
    private static final Pattern ROMAN = Pattern.compile("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");

    /**
     * Finds the rule a segment's shown text is kept verbatim by.
     *
     * @param shownText the text the model would be shown — the segment's masked text with protected spans behind
     *     tokens; never null
     * @param hasLockedTerm whether a locked glossary name is among the protected spans
     * @return the rule that matched, or {@code null} when the text holds something to translate
     */
    public static @Nullable VerbatimRule match(final String shownText, final boolean hasLockedTerm) {
        Objects.requireNonNull(shownText, "shownText");
        final String visible =
                VisibleText.visible(Normalizer.normalize(Tokens.replace(shownText, ""), Normalizer.Form.NFC));
        final String letters = lettersOf(visible);
        if (letters.isEmpty()) {
            return hasLockedTerm ? LOCKED_TERM : visible.isEmpty() ? NO_TEXT : SYMBOLS;
        }
        if (ROMAN.matcher(letters).matches() && hasNoDigit(visible)) {
            return ROMAN_NUMERAL;
        }
        if (visible.codePointCount(0, visible.length()) == 1) {
            return SINGLE_CHARACTER;
        }
        return visible.indexOf('=') >= 0 && !WORD.matcher(visible).find() ? FORMULA : null;
    }

    private static String lettersOf(final String text) {
        final StringBuilder letters = new StringBuilder();
        text.codePoints().filter(Character::isLetter).forEach(letters::appendCodePoint);
        return letters.toString();
    }

    private static boolean hasNoDigit(final String text) {
        return text.codePoints().noneMatch(Character::isDigit);
    }
}
