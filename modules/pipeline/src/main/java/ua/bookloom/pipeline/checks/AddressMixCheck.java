package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.AddressForms;

/**
 * One quoted speech that addresses its listener with the informal and the formal "you" together (ти-forms and
 * capitalised Ви-forms), which a reader hears as a slip. Only the speech is read, never the narrator's words, and each
 * speech on its own. The informal forms count in any case; a formal form counts only when it is capitalised in the
 * middle of a sentence, because a sentence start is capitalised whatever the word is. The forms are the language file's
 * ({@link AddressForms}), so a language without them is not checked. Soft: it earns one directed fix.
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
            mixIn(target, speech, informal, formal).ifPresent(findings::add);
        }
        log.debug("Address mix check found {} mixed speech(es) in {}", findings.size(), targetLanguage);
        return List.copyOf(findings);
    }

    private static Optional<CheckFinding> mixIn(
            final String target, final TextSpan speech, final Set<String> informal, final Set<String> formal) {
        String informalForm = null;
        TextSpan formalForm = null;
        final Matcher words = Words.WORD.matcher(target).region(speech.start(), speech.end());
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
