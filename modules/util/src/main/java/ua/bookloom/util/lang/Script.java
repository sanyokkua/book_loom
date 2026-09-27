package ua.bookloom.util.lang;

import java.util.Set;

/**
 * The writing systems the language catalogue distinguishes, each carrying the characters-per-token figure the token
 * estimator (task 7.3) reads and the {@link Character.UnicodeScript} values that count as one of its letters.
 *
 * <p>There is no Arabic or Hebrew constant, because neither language is in the {@link Language} catalogue.
 */
// Each constant's Set.of(...) is genuinely immutable at runtime; Error Prone's ImmutableEnumChecker only
// inspects the declared field type, which it cannot prove immutable for the general java.util.Set interface.
@SuppressWarnings("ImmutableEnumChecker")
public enum Script {

    /** The Latin alphabet — the widest-coverage script in the catalogue. */
    LATIN(4.0, Set.of(Character.UnicodeScript.LATIN)),

    /** The Cyrillic alphabet. */
    CYRILLIC(3.0, Set.of(Character.UnicodeScript.CYRILLIC)),

    /** The Greek alphabet. */
    GREEK(3.5, Set.of(Character.UnicodeScript.GREEK)),

    /** Han characters, as used untranslated for Chinese. */
    HAN(1.5, Set.of(Character.UnicodeScript.HAN)),

    /** Japanese, which mixes Han characters with Hiragana and Katakana. */
    JAPANESE(
            1.5,
            Set.of(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)),

    /** The Hangul syllabary used for Korean. */
    HANGUL(1.5, Set.of(Character.UnicodeScript.HANGUL)),

    /** Fallback for a null or unrecognized language tag — no letter scripts of its own. */
    UNKNOWN(3.0, Set.of());

    private final double charsPerToken;
    private final Set<Character.UnicodeScript> letterScripts;

    Script(double charsPerToken, Set<Character.UnicodeScript> letterScripts) {
        this.charsPerToken = charsPerToken;
        this.letterScripts = letterScripts;
    }

    /**
     * The characters-per-token figure.
     *
     * @return the average number of characters this script packs into one model token
     */
    public double charsPerToken() {
        return charsPerToken;
    }

    /**
     * The Unicode scripts counted as this script's letters.
     *
     * @return the {@link Character.UnicodeScript} values that count as one of this script's letters; empty for
     *     {@link #UNKNOWN}
     */
    public Set<Character.UnicodeScript> letterScripts() {
        return letterScripts;
    }
}
