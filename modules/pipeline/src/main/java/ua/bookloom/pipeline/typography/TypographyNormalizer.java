package ua.bookloom.pipeline.typography;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The deterministic typography pass for a translation's masked text: straight apostrophes inside words, {@code ...} as
 * one ellipsis character, the target language's quote marks for straight quotes, and no space before the marks the
 * language never spaces. A dash dialogue stays a dash dialogue. It works on text with {@code ⟦gN⟧} tokens in place, so
 * markup, code spans, kept foreign runs and locked names — all behind tokens at that stage — are never read, and the
 * characters touching a token are never changed.
 *
 * <p>It is applied to a target, never to a source: a no-op reassembly must give the source back untouched. Footnote
 * markers are tokens like any other and the text cannot say which token is one, so the space before a marker is not
 * touched. A normalised text normalises to itself.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TypographyNormalizer {

    private static final Pattern STRAIGHT_APOSTROPHE = Pattern.compile("(?<=\\p{L})'(?=\\p{L})");
    private static final Pattern THREE_DOTS = Pattern.compile("\\.{3}");

    /**
     * Normalises one target text.
     *
     * @param maskedText a target with its {@code ⟦gN⟧} tokens in place
     * @param targetLanguage the BCP 47 tag of the language the text is written in; a language with no line in the
     *     typography and quote tables gets the neutral treatment, which leaves quote marks alone
     * @return the text and what was changed in it; the same text with nothing counted when it was already right
     */
    public static Normalisation normalise(final String maskedText, final String targetLanguage) {
        return normalise("", maskedText, targetLanguage);
    }

    /**
     * Normalises one target text, with its source at hand for the speech rewrites that compare the two.
     *
     * @param maskedSource the segment's masked source; empty when unknown, which turns off the rewrites that need it
     * @param maskedText a target with its {@code ⟦gN⟧} tokens in place
     * @param targetLanguage the BCP 47 tag of the language the text is written in
     * @return the text and what was changed in it
     */
    public static Normalisation normalise(
            final String maskedSource, final String maskedText, final String targetLanguage) {
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedText, "maskedText");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final TypographyRules rules = TypographyRules.forLanguage(targetLanguage);
        final Edit quotes = QuoteStyler.apply(maskedText, rules.quotes());
        final Edit spaces = OutsideTokens.replaceAll(quotes.text(), spacePattern(rules), "");
        final Edit ellipses = OutsideTokens.replaceAll(spaces.text(), THREE_DOTS, "…");
        final Edit apostrophes =
                OutsideTokens.replaceAll(ellipses.text(), STRAIGHT_APOSTROPHE, String.valueOf(rules.apostrophe()));
        final SpeechRepairs.Result speech =
                SpeechRepairs.apply(maskedSource, apostrophes.text(), targetLanguage, rules);
        final Normalisation result = new Normalisation(
                speech.text(), apostrophes.count(), ellipses.count(), quotes.count(), spaces.count(), speech.tallies());
        log.debug(
                "Typography language={} apostrophes={} ellipses={} quotes={} spaces={}",
                targetLanguage,
                result.apostrophes(),
                result.ellipses(),
                result.quotes(),
                result.spaces());
        return result;
    }

    // A space before a mark goes unless a dash stands before it ("— …" is a silent reply) or a letter or digit follows
    // the mark (".gitignore", ".5"); the lookbehind cannot see past a token because each stretch is matched alone.
    private static Pattern spacePattern(final TypographyRules rules) {
        return Pattern.compile("(?<=[^\\s\\p{Pd}])[ \\t]+(?=[\\Q" + rules.noSpaceBefore() + "\\E](?![\\p{L}\\p{N}]))");
    }
}
