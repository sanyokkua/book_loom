package ua.bookloom.pipeline.typography;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * The deterministic speech rewrites that fix what a model gets wrong about quotes and dialogue punctuation, run after
 * the plain typography steps and only for a language whose table line turns them on. Each rule is a small class of its
 * own, changes no letter (bar the case of one) and leaves its own output alone, so a second pass changes nothing. The
 * order matters: an opening quote is restored before the stray-quote rule judges whether the rest is balanced.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SpeechRepairs {

    /**
     * What the rewrites did.
     *
     * @param text the text after them
     * @param tallies each rule that fired with its count
     */
    record Result(String text, List<Normalisation.Tally> tallies) {}

    static Result apply(final String source, final String text, final String languageTag, final TypographyRules rules) {
        final List<QuotePair> pairs = rules.quotes();
        if (pairs.isEmpty() || !(rules.isSpeechRepaired() || rules.isCommaOutsideQuote())) {
            return new Result(text, List.of());
        }
        final List<Normalisation.Tally> tallies = new ArrayList<>();
        String current = text;
        if (rules.isSpeechRepaired()) {
            final QuotePair primary = pairs.get(0);
            final Locale locale = Locale.forLanguageTag(languageTag);
            current = run(tallies, "mixed quote mark", MixedQuotes.apply(current, pairs));
            current = run(tallies, "opening quote restored", OpeningQuote.apply(source, current, primary));
            current = run(tallies, "split speech merged", SplitSpeech.apply(source, current, primary));
            current = run(tallies, "extra closing quote removed", ExtraCloser.apply(current, primary));
            current = run(tallies, "stray quote mark removed", StrayQuote.apply(current, languageTag));
            current = run(tallies, "sentence capitalised", SentenceCase.apply(source, current, locale));
        }
        if (rules.isCommaOutsideQuote()) {
            current = run(tallies, "comma moved outside the quote", CommaBeforeDash.apply(current, pairs.get(0)));
        }
        if (!tallies.isEmpty()) {
            log.debug("Speech repairs language={} fired={}", languageTag, tallies);
        }
        return new Result(current, List.copyOf(tallies));
    }

    private static String run(final List<Normalisation.Tally> tallies, final String label, final Edit edit) {
        if (edit.count() > 0) {
            tallies.add(new Normalisation.Tally(label, edit.count()));
            log.trace("Speech rewrite {} count={}", label, edit.count());
        }
        return edit.text();
    }
}
