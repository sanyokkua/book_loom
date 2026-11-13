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
        return normalise(maskedSource, maskedText, targetLanguage, TypographyChoices.DEFAULTS);
    }

    /**
     * Normalises one target text with the Brief's apostrophe and quote-style keys applied.
     *
     * @param maskedSource the segment's masked source; empty when unknown, which turns off the rewrites that need it
     * @param maskedText a target with its {@code ⟦gN⟧} tokens in place
     * @param targetLanguage the BCP 47 tag of the language the text is written in
     * @param choices the Brief's keys; a null key falls back to the language's own default
     * @return the text and what was changed in it
     */
    public static Normalisation normalise(
            final String maskedSource,
            final String maskedText,
            final String targetLanguage,
            final TypographyChoices choices) {
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedText, "maskedText");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(choices, "choices");
        final TypographyRules rules =
                TypographyRules.forLanguage(targetLanguage).with(choices);
        final Edit stress = StressStrip.apply(maskedText, targetLanguage);
        final Edit quotes = QuoteStyler.apply(stress.text(), rules.quotes());
        final Edit spaces = OutsideTokens.replaceAll(quotes.text(), spacePattern(rules), "");
        final Edit ellipses = OutsideTokens.replaceAll(spaces.text(), THREE_DOTS, "…");
        final Edit apostrophes =
                OutsideTokens.replaceAll(ellipses.text(), STRAIGHT_APOSTROPHE, String.valueOf(rules.apostrophe()));
        final SpeechRepairs.Result speech =
                SpeechRepairs.apply(maskedSource, apostrophes.text(), targetLanguage, rules);
        final Dashes dashes = Dashes.apply(maskedSource, speech.text(), rules);
        final Edit number = LeadingNumber.apply(maskedSource, dashes.text());
        final Normalisation result = new Normalisation(
                number.text(),
                apostrophes.count(),
                ellipses.count(),
                quotes.count(),
                spaces.count(),
                speech.tallies(),
                new Normalisation.Repairs(
                        stress.count(), dashes.leading(), dashes.inLine(), number.count(), dashes.isLostBeforeQuote()));
        logRepairs(result);
        return result;
    }

    private static void logRepairs(final Normalisation result) {
        final Normalisation.Repairs repairs = result.repairs();
        log.debug(
                "norm stress={} leadingDash={} dialogueDash={} leadingNumber={} quotes={}",
                repairs.stress(),
                repairs.leadingDash(),
                repairs.dialogueDash(),
                repairs.leadingNumber(),
                result.quotes());
    }

    // The leading dash is put back before the dash forms are unified, so a restored dash is already in the one form.
    private record Dashes(String text, int leading, int inLine, boolean isLostBeforeQuote) {

        static Dashes apply(final String source, final String text, final TypographyRules rules) {
            if (!rules.isDialogueDashWritten()) {
                return new Dashes(text, 0, 0, false);
            }
            final Edit leading = LeadingDash.apply(source, text);
            final Edit inLine = DialogueDash.apply(leading.text());
            return new Dashes(
                    inLine.text(), leading.count(), inLine.count(), LeadingDash.isLostBeforeQuote(source, text));
        }
    }

    // A space before a mark goes unless a dash stands before it ("— …" is a silent reply) or a letter or digit follows
    // the mark (".gitignore", ".5"); the lookbehind cannot see past a token because each stretch is matched alone.
    private static Pattern spacePattern(final TypographyRules rules) {
        return Pattern.compile("(?<=[^\\s\\p{Pd}])[ \\t]+(?=[\\Q" + rules.noSpaceBefore() + "\\E](?![\\p{L}\\p{N}]))");
    }
}
