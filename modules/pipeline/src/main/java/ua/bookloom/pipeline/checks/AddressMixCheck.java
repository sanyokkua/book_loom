package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.AddressForms;
import ua.bookloom.pipeline.qa.CheckLevel;

/**
 * One quoted speech that addresses its listener with the informal and the formal "you" together (ти-forms and
 * capitalised Ви-forms), which a reader hears as a slip. Only the speech is read, never the narrator's words, and each
 * speech on its own. The informal forms count in any case; a formal form counts only when it is capitalised in the
 * middle of a sentence, because a sentence start is capitalised whatever the word is. The forms are the language file's
 * ({@link AddressForms}), so a language without them is not checked. Soft: it earns one directed fix.
 *
 * <p>The source branch ({@link #parity}) compares each target speech with its source speech: one the source addresses
 * only informally (or only formally) must not become the other kind. It reads the speeches in order and only when both
 * texts hold the same number of them, and it is off unless both languages list both kinds of form.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AddressMixCheck {

    private static final String OPENING_MARKS = "«“„\"‘‹(—–―[";
    private static final String SENTENCE_ENDS = ".!?…";

    static List<CheckFinding> find(final String target, final String targetLanguage) {
        final Set<String> informal = AddressForms.informal(targetLanguage);
        final Set<String> formal = AddressForms.formal(targetLanguage);
        if (informal.isEmpty() || formal.isEmpty() || target.isEmpty()) {
            log.debug("Address mix check skipped for {}: no forms listed or no text", targetLanguage);
            return List.of();
        }
        final List<CheckFinding> findings = new ArrayList<>();
        for (final TextSpan speech : QuotedSpans.speeches(target, targetLanguage)) {
            final String spoken = withoutNestedQuotes(target, speech, targetLanguage);
            mixIn(target, spoken, speech, informal, formal).ifPresent(findings::add);
        }
        log.debug("Address mix check found {} mixed speech(es) in {}", findings.size(), targetLanguage);
        return List.copyOf(findings);
    }

    // A word a speaker only names ("Ви" in quotes inside the speech) addresses nobody: the speech's own opening mark
    // and everything outside it are blanked, so the quote left inside is a top-level one that narration() blanks.
    private static String withoutNestedQuotes(final String target, final TextSpan speech, final String targetLanguage) {
        final char[] only = new char[target.length()];
        Arrays.fill(only, ' ');
        final int from =
                OPENING_MARKS.indexOf(target.charAt(speech.start())) >= 0 ? speech.start() + 1 : speech.start();
        target.getChars(from, speech.end(), only, from);
        return QuotedSpans.narration(new String(only), targetLanguage);
    }

    private static Optional<CheckFinding> mixIn(
            final String target,
            final String spoken,
            final TextSpan speech,
            final Set<String> informal,
            final Set<String> formal) {
        String informalForm = null;
        TextSpan formalForm = null;
        final Matcher words = Words.WORD.matcher(spoken).region(speech.start(), speech.end());
        while (words.find()) {
            final String word = words.group();
            if (informalForm == null && informal.contains(word.toLowerCase(Locale.ROOT))) {
                informalForm = word;
            } else if (formalForm == null
                    && formal.contains(word)
                    && !isSentenceInitial(target, speech.start(), words.start())) {
                formalForm = new TextSpan(words.start(), words.end(), word);
            }
        }
        if (informalForm == null || formalForm == null) {
            log.trace("Speech at {}..{} is addressed one way", speech.start(), speech.end());
            return Optional.empty();
        }
        log.debug("Speech at {}..{} mixes the forms of address", speech.start(), speech.end());
        log.trace("Speech mixes \"{}\" and \"{}\": {}", informalForm, formalForm.text(), speech.text());
        return Optional.of(new CheckFinding(
                FindingKind.ADDRESS_MIX,
                formalForm,
                "One speech addresses its listener both informally (\"" + informalForm + "\") and formally (\""
                        + formalForm.text() + "\"): choose the one form that fits the listener and use it throughout"
                        + " the speech.",
                false));
    }

    /** What one speech's address forms are: which kinds it holds, and where the first formal form stands. */
    private record Forms(boolean hasInformal, @Nullable TextSpan formal) {

        boolean isInformalOnly() {
            return hasInformal && formal == null;
        }

        boolean isFormalOnly() {
            return !hasInformal && formal != null;
        }
    }

    static List<CheckFinding> parity(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        if (sourceLanguage == null || !hasBothKinds(sourceLanguage) || !hasBothKinds(targetLanguage)) {
            log.debug("Address parity check skipped: the pair lists no address forms on both sides");
            return List.of();
        }
        final List<TextSpan> said = QuotedSpans.speeches(source, sourceLanguage);
        final List<TextSpan> written = QuotedSpans.speeches(target, targetLanguage);
        if (said.isEmpty() || said.size() != written.size()) {
            log.debug("Address parity check skipped: {} source and {} target speeches", said.size(), written.size());
            return List.of();
        }
        final List<CheckFinding> findings = new ArrayList<>();
        for (int index = 0; index < said.size(); index++) {
            final Forms from = formsOf(source, said.get(index), sourceLanguage, false);
            final Forms to = formsOf(target, written.get(index), targetLanguage, true);
            if ((from.isInformalOnly() && to.isFormalOnly()) || (from.isFormalOnly() && to.isInformalOnly())) {
                findings.add(switchedFinding(from, written.get(index), target));
            }
        }
        log.debug("Address parity check found {} switched speech(es)", findings.size());
        return List.copyOf(findings);
    }

    private static boolean hasBothKinds(final String language) {
        return !AddressForms.informal(language).isEmpty()
                && !AddressForms.formal(language).isEmpty();
    }

    // A source writes the formal forms in lower case, so they are compared in lower case there; a target writes them
    // with a capital, which a polite Ukrainian speech keeps even at its start.
    private static Forms formsOf(
            final String text, final TextSpan speech, final String language, final boolean isTarget) {
        final Set<String> informal = AddressForms.informal(language);
        final Set<String> formal = AddressForms.formal(language);
        final Set<String> formalLower = new HashSet<>();
        formal.forEach(form -> formalLower.add(form.toLowerCase(Locale.ROOT)));
        final String spoken = withoutNestedQuotes(text, speech, language);
        boolean hasInformal = false;
        TextSpan formalForm = null;
        final Matcher words = Words.WORD.matcher(spoken).region(speech.start(), speech.end());
        while (words.find()) {
            final String word = words.group();
            hasInformal |= informal.contains(word.toLowerCase(Locale.ROOT));
            final boolean isFormal =
                    isTarget ? formal.contains(word) : formalLower.contains(word.toLowerCase(Locale.ROOT));
            if (formalForm == null && isFormal) {
                formalForm = new TextSpan(words.start(), words.end(), word);
            }
        }
        return new Forms(hasInformal, formalForm);
    }

    private static CheckFinding switchedFinding(final Forms from, final TextSpan written, final String target) {
        final boolean wasInformal = from.isInformalOnly();
        log.debug("Speech at {}..{} switches the form of address", written.start(), written.end());
        return new CheckFinding(
                FindingKind.ADDRESS_PARITY,
                new TextSpan(written.start(), written.end(), target.substring(written.start(), written.end())),
                "The source addresses the listener " + (wasInformal ? "informally" : "formally") + " and the"
                        + " translation addresses the listener " + (wasInformal ? "formally" : "informally")
                        + ": use the " + (wasInformal ? "informal" : "formal") + " form of address in this speech,"
                        + " as the source does.",
                CheckLevel.REPAIR);
    }

    // True when only a quote mark, a dash or white space stands between the word and the start of its speech or of its
    // sentence.
    private static boolean isSentenceInitial(final String text, final int speechStart, final int wordStart) {
        int index = wordStart - 1;
        while (index >= speechStart
                && (Character.isWhitespace(text.charAt(index)) || OPENING_MARKS.indexOf(text.charAt(index)) >= 0)) {
            index--;
        }
        return index < speechStart || SENTENCE_ENDS.indexOf(text.charAt(index)) >= 0;
    }
}
