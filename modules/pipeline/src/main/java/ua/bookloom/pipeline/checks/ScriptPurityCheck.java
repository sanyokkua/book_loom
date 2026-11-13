package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.lang.Script;

/**
 * Per-word script purity: a word with letters of the target script that also holds a letter of another script
 * ({@code навчg3вся}, a Latin {@code o} in {@code імпoву}) or a digit between two lowercase letters. Whole-segment
 * script share cannot see this, because one stray letter barely moves it. A word wholly in a script the target does not use (CJK, Arabic) is blocked unless the source holds it;
 * a wholly Latin word is a name or a brand and is left alone, and a digit at the edge of a word ({@code 90х}) is a normal suffix.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScriptPurityCheck {

    private static final int CJK_SIGNS_FROM = 0x3000;
    private static final int CJK_SIGNS_TO = 0x303F;
    private static final int FULL_WIDTH_FROM = 0xFF00;
    private static final int FULL_WIDTH_TO = 0xFFEF;

    private static final Set<Script> CHECKED = Set.of(Script.LATIN, Script.CYRILLIC, Script.GREEK);

    static List<CheckFinding> find(final String source, final String target, final Optional<Script> targetScript) {
        if (targetScript.isEmpty() || !CHECKED.contains(targetScript.get())) {
            log.debug("Script purity skipped: target script {} is not a spaced alphabet", targetScript);
            return List.of();
        }
        final Script script = targetScript.get();
        final List<CheckFinding> findings = new ArrayList<>();
        final String lowerSource = source.toLowerCase(Locale.ROOT);
        final Matcher matcher = Words.RUN.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group();
            if (isForeignScriptWord(word, script) && !lowerSource.contains(word.toLowerCase(Locale.ROOT))) {
                findings.add(foreignScript(matcher, script));
                continue;
            }
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
        findings.addAll(fullWidthSigns(source, target));
        return List.copyOf(findings);
    }

    private static CheckFinding foreignScript(final Matcher matcher, final Script script) {
        log.debug("Foreign-script word at {}..{}", matcher.start(), matcher.end());
        return new CheckFinding(
                FindingKind.MIXED_SCRIPT,
                new TextSpan(matcher.start(), matcher.end(), matcher.group()),
                "The word is written in an alphabet the target language does not use. Translate it into " + name(script)
                        + ".",
                true);
    }

    // CJK and full-width punctuation (。 、 ，) is nobody's mark in a spaced alphabet; one the source holds is kept.
    private static List<CheckFinding> fullWidthSigns(final String source, final String target) {
        final List<CheckFinding> findings = new ArrayList<>();
        int index = 0;
        while (index < target.length()) {
            final int point = target.codePointAt(index);
            final int width = Character.charCount(point);
            if (isFullWidthSign(point) && source.indexOf(point) < 0) {
                final String sign = new String(Character.toChars(point));
                log.debug("Full-width sign at {}", index);
                findings.add(new CheckFinding(
                        FindingKind.MIXED_SCRIPT,
                        new TextSpan(index, index + width, sign),
                        "The sign " + sign + " belongs to another writing system. Use the target language's own mark.",
                        true));
            }
            index += width;
        }
        return findings;
    }

    private static boolean isFullWidthSign(final int point) {
        return (point >= CJK_SIGNS_FROM && point <= CJK_SIGNS_TO)
                || (point >= FULL_WIDTH_FROM && point <= FULL_WIDTH_TO);
    }

    // A word wholly in one script that is neither the target's, Latin (brands, names) nor common.
    private static boolean isForeignScriptWord(final String word, final Script script) {
        final Set<Character.UnicodeScript> scripts = word.codePoints()
                .filter(Character::isLetter)
                .mapToObj(Character.UnicodeScript::of)
                .collect(Collectors.toSet());
        if (scripts.size() != 1) {
            return false;
        }
        final Character.UnicodeScript only = scripts.iterator().next();
        return !script.letterScripts().contains(only)
                && only != Character.UnicodeScript.LATIN
                && only != Character.UnicodeScript.COMMON
                && only != Character.UnicodeScript.INHERITED;
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
