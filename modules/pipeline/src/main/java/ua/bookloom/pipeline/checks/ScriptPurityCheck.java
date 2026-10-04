package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.lang.Script;

/**
 * Per-word script purity: a word with letters of the target script that also holds a letter of another script
 * ({@code навчg3вся}, a Latin {@code o} in {@code імпoву}) or a digit between two lowercase letters. Whole-segment
 * script share cannot see this, because one stray letter barely moves it. A word wholly in another script is a name or
 * a kept foreign word and is left alone, and a digit at the edge of a word ({@code 90х}) is a normal suffix.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScriptPurityCheck {

    private static final Set<Script> CHECKED = Set.of(Script.LATIN, Script.CYRILLIC, Script.GREEK);

    static List<CheckFinding> find(final String target, final Optional<Script> targetScript) {
        if (targetScript.isEmpty() || !CHECKED.contains(targetScript.get())) {
            log.debug("Script purity skipped: target script {} is not a spaced alphabet", targetScript);
            return List.of();
        }
        final Script script = targetScript.get();
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher matcher = Words.RUN.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group();
            final String stray = stray(word, script);
            if (!stray.isEmpty()) {
                log.debug("Mixed-script word at {}..{}: stray {}", matcher.start(), matcher.end(), stray);
                findings.add(new CheckFinding(
                        FindingKind.MIXED_SCRIPT,
                        new TextSpan(matcher.start(), matcher.end(), word),
                        "The word mixes alphabets: " + stray + " does not belong among " + name(script)
                                + " letters. Write the whole word in " + name(script) + " only.",
                        true));
            }
        }
        return List.copyOf(findings);
    }

    /** The distinct characters of {@code word} that do not belong in a word of {@code script}, in order. */
    private static String stray(final String word, final Script script) {
        if (word.codePoints().noneMatch(point -> isLetterOf(point, script))) {
            return "";
        }
        final Set<String> stray = new LinkedHashSet<>();
        final int[] points = word.codePoints().toArray();
        for (int index = 0; index < points.length; index++) {
            if (isForeignLetter(points[index], script) || isInteriorDigit(points, index, script)) {
                stray.add(new String(Character.toChars(points[index])));
            }
        }
        return String.join("", stray);
    }

    private static boolean isLetterOf(final int point, final Script script) {
        return Character.isLetter(point) && script.letterScripts().contains(Character.UnicodeScript.of(point));
    }

    private static boolean isForeignLetter(final int point, final Script script) {
        return Character.isLetter(point) && !script.letterScripts().contains(Character.UnicodeScript.of(point));
    }

    private static boolean isInteriorDigit(final int[] points, final int index, final Script script) {
        return Character.isDigit(points[index])
                && index > 0
                && index < points.length - 1
                && isLowerLetterOf(points[index - 1], script)
                && isLowerLetterOf(points[index + 1], script);
    }

    private static boolean isLowerLetterOf(final int point, final Script script) {
        return isLetterOf(point, script) && Character.isLowerCase(point);
    }

    private static String name(final Script script) {
        return script.name().charAt(0) + script.name().substring(1).toLowerCase(Locale.ROOT);
    }
}
