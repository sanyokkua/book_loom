package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.StopWords;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * Whether a whole paragraph of the target is still in the source language — the silent English paragraph an earlier
 * run accepted. Only a paragraph of {@value #MIN_WORDS} words or more is judged, because a short line (a name, an
 * exclamation, a title) legitimately reads the same in both languages; there the echo check speaks. A paragraph fails
 * when its letters are mostly not in the target script, or, for a pair that shares a script, when the source
 * language's function words are too large a share of it. Words the target language uses too are not counted against
 * it, so a Polish {@code to} is never read as English.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LanguageIdentityCheck {

    static final int MIN_WORDS = 12;

    /** Under this share of target-script letters a paragraph counts as written in another script. */
    private static final double MIN_TARGET_SCRIPT_SHARE = 0.60;

    /** Function words of the source language make up about two fifths of its prose; a translation has almost none. */
    private static final double MAX_SOURCE_STOP_WORD_RATE = 0.30;

    private static final Pattern STOP_WORD = Pattern.compile("[\\p{L}\\p{M}'’ʼ]+");
    private static final Pattern LINE = Pattern.compile("[^\\r\\n]+");

    static List<CheckFinding> find(
            final String target, @Nullable final String sourceLanguage, final String targetLanguage) {
        final Optional<Script> sourceScript = Languages.scriptOf(sourceLanguage);
        final Optional<Script> targetScript = Languages.scriptOf(targetLanguage);
        final Set<String> sourceStops = sourceStops(sourceLanguage, targetLanguage);
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher lines = LINE.matcher(target);
        while (lines.find()) {
            final String line = NonProse.withoutLocators(lines.group());
            if (Words.count(line) >= MIN_WORDS && isLeftover(line, sourceScript, targetScript, sourceStops)) {
                findings.add(finding(lines));
            }
        }
        return List.copyOf(findings);
    }

    private static CheckFinding finding(final Matcher lines) {
        return new CheckFinding(
                FindingKind.LEFTOVER_LANGUAGE,
                new TextSpan(lines.start(), lines.end(), lines.group()),
                "This paragraph is still in the source language. Translate all of it.",
                true);
    }

    private static boolean isLeftover(
            final String line,
            final Optional<Script> sourceScript,
            final Optional<Script> targetScript,
            final Set<String> sourceStops) {
        final boolean otherScript = sourceScript.isPresent()
                && targetScript.isPresent()
                && sourceScript.get() != targetScript.get()
                && targetScriptShare(line, targetScript.get()) < MIN_TARGET_SCRIPT_SHARE;
        final double rate = stopWordRate(line, sourceStops);
        final boolean leftover = otherScript || rate >= MAX_SOURCE_STOP_WORD_RATE;
        log.debug(
                "Paragraph of {} words: other script {}, source stop-word rate {}, left in the source language {}",
                Words.count(line),
                otherScript,
                rate,
                leftover);
        return leftover;
    }

    private static double targetScriptShare(final String line, final Script script) {
        final long letters = line.codePoints().filter(Character::isLetter).count();
        final long inScript = line.codePoints()
                .filter(point ->
                        Character.isLetter(point) && script.letterScripts().contains(Character.UnicodeScript.of(point)))
                .count();
        return letters == 0 ? 1.0 : (double) inScript / letters;
    }

    private static double stopWordRate(final String line, final Set<String> sourceStops) {
        if (sourceStops.isEmpty()) {
            return 0.0;
        }
        final Matcher words = STOP_WORD.matcher(line);
        int total = 0;
        int hits = 0;
        while (words.find()) {
            total++;
            if (sourceStops.contains(words.group().toLowerCase(Locale.ROOT).replace('’', '\''))) {
                hits++;
            }
        }
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /** The source language's function words that the target language does not also use; empty when there is no list. */
    private static Set<String> sourceStops(@Nullable final String sourceLanguage, final String targetLanguage) {
        final Optional<Set<String>> source = StopWords.bundled(sourceLanguage);
        if (source.isEmpty() || sameLanguage(sourceLanguage, targetLanguage)) {
            return Set.of();
        }
        final Set<String> only = new HashSet<>(source.get());
        StopWords.bundled(targetLanguage).ifPresent(only::removeAll);
        return only;
    }

    private static boolean sameLanguage(@Nullable final String first, final String second) {
        return first != null
                && Locale.forLanguageTag(first)
                        .getLanguage()
                        .equals(Locale.forLanguageTag(second).getLanguage());
    }
}
