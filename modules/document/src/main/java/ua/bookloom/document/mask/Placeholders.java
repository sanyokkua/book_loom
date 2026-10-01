package ua.bookloom.document.mask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The placeholder token grammar: {@code ⟦gN⟧} (U+27E6, the letter {@code g}, one or more ASCII digits, U+27E7).
 *
 * <p>Every consumer of this grammar — the mask-time invariant, the multiset gate, and restore — matches the whole
 * pattern with one {@link Matcher} pass rather than iterating the placeholder map's keys and calling
 * {@link String#replace}: a per-key substitution loop is exactly what mis-matches {@code ⟦g1⟧} as a prefix of
 * {@code ⟦g12⟧} and re-expands a token that a restored fragment happens to contain (ADR-0031, design.md D5).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("⟦g(\\d+)⟧");

    /** The token's opening bracket, U+27E6. */
    static final char OPEN_GLYPH = '⟦';

    /** The token's closing bracket, U+27E7. */
    static final char CLOSE_GLYPH = '⟧';

    /**
     * Spells the token for {@code index}.
     *
     * @param index the placeholder's dense, first-appearance index; never negative
     * @return the bracketed token, e.g. {@code "⟦g0⟧"}; never null
     */
    public static String token(int index) {
        return "⟦g" + index + "⟧";
    }

    /**
     * Spells the placeholder map's key form for {@code index} — the bare index with no brackets, per the
     * requirement <em>Spell a placeholder token as a bracketed g and digits</em>.
     *
     * @param index the placeholder's dense, first-appearance index; never negative
     * @return the bare key, e.g. {@code "g0"}; never null
     */
    public static String key(int index) {
        return "g" + index;
    }

    /**
     * Strips a token's brackets to its bare key form, the inverse of {@link #token(int)} — e.g. {@code "⟦g3⟧"} to
     * {@code "g3"}. The single owner of this conversion, so a second site (a private {@code keyOf} on the writer, a
     * lookup on the restorer) never has to re-derive {@code substring(1, len - 1)} against the grammar this class
     * already owns.
     *
     * @param token a whole-grammar token, brackets included; never null
     * @return the token's bare key form; never null
     */
    public static String keyOf(String token) {
        Objects.requireNonNull(token, "token");
        return token.substring(1, token.length() - 1);
    }

    /**
     * Every whole-grammar token match in {@code text}, in order of appearance.
     *
     * @param text the text to scan; never null
     * @return the tokens found, in appearance order; never null, empty if none
     */
    public static List<String> tokensOf(String text) {
        Objects.requireNonNull(text, "text");
        final List<String> tokens = new ArrayList<>();
        final Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * The multiset of whole-grammar tokens in {@code text}, as a token-to-occurrence-count map.
     *
     * @param text the text to scan; never null
     * @return the token multiset, insertion-ordered by first appearance; never null, empty if none
     */
    public static Map<String, Integer> multisetOf(String text) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String token : tokensOf(text)) {
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Whether {@code text} holds a U+27E6 or U+27E7 glyph that is not part of a whole-grammar token. A masked form
     * never does — {@link MaskWriter} turns every literal bracket of a source into its own token — so in a target such
     * a glyph is a token the model broke: split by a space, misspelt, or left over from one it moved.
     *
     * @param text the text to scan; never null
     * @return {@code true} if a bracket glyph stands outside every token, {@code false} otherwise
     */
    public static boolean hasStrayBracket(String text) {
        final String outside = matcher(text).replaceAll("");
        return outside.indexOf(OPEN_GLYPH) >= 0 || outside.indexOf(CLOSE_GLYPH) >= 0;
    }

    /**
     * A fresh {@link Matcher} over the whole-grammar pattern, positioned to scan {@code text} — the single
     * mechanism a caller needing more than {@link #tokensOf(String)}'s list (e.g. a single restore pass that also
     * needs the non-token spans between matches) builds on.
     *
     * @param text the text to scan; never null
     * @return a matcher over {@code text}; never null
     */
    public static Matcher matcher(String text) {
        Objects.requireNonNull(text, "text");
        return TOKEN.matcher(text);
    }
}
