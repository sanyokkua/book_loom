package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spacing artefacts a model leaves: a doubled space, a space before a full stop or comma that follows a non-letter
 * ({@code ) .}), a space just inside a bracket. A space after a letter before a full stop is a dropped word, which the
 * length check owns. Only artefacts the source does not have are reported, so a book's own habit is never an error,
 * and the marks that take a space in one language and none in another ({@code ! ? : ;}) are never touched.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SpacingCheck {

    private static final Pattern DOUBLE_SPACE = Pattern.compile("(?<=\\S)[ \\t]{2,}(?=\\S)");
    private static final Pattern BEFORE_STOP = Pattern.compile("(?<=[^\\p{L}\\s])[ \\t]+[.,](?=\\s|$)");
    private static final Pattern INSIDE_BRACKET = Pattern.compile("[(\\[][ \\t]+(?=\\S)|(?<=\\S)[ \\t]+[)\\]]");

    static List<CheckFinding> find(final String source, final String target) {
        final List<CheckFinding> findings = new ArrayList<>();
        add(findings, DOUBLE_SPACE, "A space is doubled. Use a single space.", source, target);
        add(findings, BEFORE_STOP, "A space stands before a full stop or comma. Remove it.", source, target);
        add(findings, INSIDE_BRACKET, "A space stands just inside a bracket. Remove it.", source, target);
        return List.copyOf(findings);
    }

    private static void add(
            final List<CheckFinding> findings,
            final Pattern pattern,
            final String explanation,
            final String source,
            final String target) {
        final Matcher matcher = pattern.matcher(target);
        if (matcher.find()
                && pattern.matcher(target).results().count()
                        > pattern.matcher(source).results().count()) {
            final int from = Math.max(0, matcher.start() - 1);
            final int to = Math.min(target.length(), matcher.end() + 1);
            log.debug("Spacing artefact at {}..{}: {}", from, to, explanation);
            findings.add(new CheckFinding(
                    FindingKind.SPACING, new TextSpan(from, to, target.substring(from, to)), explanation, false));
        }
    }
}
