package ua.bookloom.pipeline.checks;

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
    private static final Pattern LABEL =
            Pattern.compile("\\b(?:Outcome|Output|Translation|Target)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final String EXPLANATION =
            "The text holds reply structure that is not part of the book ({, }, [, ] or a label such as Outcome:)."
                    + " Remove it and write the translation only.";

    static Optional<CheckFinding> find(final String source, final String target) {
        final Matcher brackets = BRACKETS.matcher(target);
        if (brackets.find() && count(BRACKETS, target) > count(BRACKETS, source)) {
            return finding(target, brackets.start());
        }
        final Matcher label = LABEL.matcher(target);
        if (label.find() && count(LABEL, target) > count(LABEL, source)) {
            return finding(target, label.start());
        }
        return Optional.empty();
    }

    private static long count(final Pattern pattern, final String text) {
        return pattern.matcher(text).results().count();
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
