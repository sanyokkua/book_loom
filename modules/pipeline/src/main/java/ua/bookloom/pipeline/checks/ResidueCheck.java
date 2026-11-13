package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reply structure a model leaves inside a target: braces and square brackets the source does not have (the JSON
 * closers of a batch reply written into the last string) and a wrapper label such as {@code Outcome:}. Prose never
 * needs a brace, and a bracket the source also has (a note mark) is the book's own, so only a surplus counts. The
 * text is never repaired here; the segment fails and is drafted again.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ResidueCheck {

    private static final Pattern BRACKETS = Pattern.compile("[{}\\[\\]]");
    private static final String LABEL_WORD = "\\b(?:Outcome|Output|Translation|Target)\\s*:";

    /**
     * A wrapper label where a reply puts one: at the very start of the text (after an opening quote), in front of a
     * brace, or as the text's last word. A label word inside a sentence ("Ціль: знищити міст" → "Target: destroy the
     * bridge") is the book's own. The three places are read by hand around each label word, because one regular
     * expression holding all three retries its blank runs from every start.
     */
    private static final Pattern LABEL = Pattern.compile(LABEL_WORD, Pattern.CASE_INSENSITIVE);

    private static final String LEADING = " \t\n\u000B\f\r\"'«“„";
    private static final String BLANKS = " \t\n\u000B\f\r";
    private static final String QUOTES = "\"'«“„";
    private static final String UNICODE_LINE_ENDS = "\u0085\u2028\u2029";

    private static final String ENGLISH = "en";
    private static final String EXPLANATION =
            "The text holds reply structure that is not part of the book ({, }, [, ] or a label such as Outcome:)."
                    + " Remove it and write the translation only.";

    /**
     * The reply residue a target holds.
     *
     * @param source the source text
     * @param target the target text
     * @param targetLanguage the target language tag; an English target is never held to the label rule
     * @return the blocking finding at the first residue, or empty when the target holds none
     */
    static Optional<CheckFinding> find(final String source, final String target, final String targetLanguage) {
        final Matcher brackets = BRACKETS.matcher(target);
        if (brackets.find() && count(BRACKETS, target) > count(BRACKETS, source)) {
            return finding(target, brackets.start());
        }
        // An English target may open with the label words themselves ("Target: …"); its reply residue is the brace
        // the bracket rule above already caught.
        if (ENGLISH.equals(Locale.forLanguageTag(targetLanguage).getLanguage())) {
            log.debug("Reply-label rule skipped for an English target");
            return Optional.empty();
        }
        final List<Integer> labels = labelStarts(target);
        if (!labels.isEmpty() && labels.size() > labelStarts(source).size()) {
            return finding(target, labels.getFirst());
        }
        return Optional.empty();
    }

    private static long count(final Pattern pattern, final String text) {
        return pattern.matcher(text).results().count();
    }

    // Where each label sits that a reply would put: the start of the text for a leading one, else the label word.
    private static List<Integer> labelStarts(final String text) {
        final List<Integer> starts = new ArrayList<>();
        final Matcher label = LABEL.matcher(text);
        boolean first = true;
        while (label.find()) {
            // Only the first label can have nothing but opening quotes before it: any other has a label word there.
            if (first && isLeading(text, label.start())) {
                starts.add(0);
            } else if (precedesBrace(text, label.end()) || endsText(text, label.end())) {
                starts.add(label.start());
            }
            first = false;
        }
        return starts;
    }

    private static boolean isLeading(final String text, final int labelStart) {
        return text.substring(0, labelStart).chars().allMatch(c -> LEADING.indexOf(c) >= 0);
    }

    private static boolean precedesBrace(final String text, final int from) {
        int at = skip(text, from, BLANKS);
        if (at < text.length() && QUOTES.indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        at = skip(text, at, BLANKS);
        return at < text.length() && text.charAt(at) == '{';
    }

    private static boolean endsText(final String text, final int from) {
        final int at = skip(text, from, BLANKS);
        return at == text.length() || (at == text.length() - 1 && UNICODE_LINE_ENDS.indexOf(text.charAt(at)) >= 0);
    }

    private static int skip(final String text, final int from, final String members) {
        int at = from;
        while (at < text.length() && members.indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        return at;
    }

    private static Optional<CheckFinding> finding(final String target, final int start) {
        log.debug("Reply residue at {} of {}", start, target.length());
        return Optional.of(new CheckFinding(
                FindingKind.PROTOCOL_LEAK,
                new TextSpan(start, target.length(), target.substring(start)),
                EXPLANATION,
                true));
    }
}
