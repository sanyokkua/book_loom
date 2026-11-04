package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import ua.bookloom.pipeline.index.Occurrences;
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
        final Set<String> sourceOnly = sourceOnly(sourceLanguage, targetLanguage);
        final List<CheckFinding> findings = new ArrayList<>();
        final List<Hit> residue = new ArrayList<>();
        final Matcher lines = LINE.matcher(target);
        while (lines.find()) {
            final String line = NonProse.withoutLocators(lines.group());
            if (Words.count(line) >= MIN_WORDS && isLeftover(line, sourceScript, targetScript, sourceOnly)) {
                findings.add(finding(lines));
            } else {
                residue.addAll(hits(lines.group(), lines.start(), sourceOnly));
            }
        }
        residueFinding(residue).ifPresent(findings::add);
        return List.copyOf(findings);
    }

    /** A source-only function word found in the target, with where it starts. */
    record Hit(int start, String word) {}

    /**
     * The source-only function words in one text: whole words, lower-case initial, and a one-letter word only when
     * the next word starts in lower case (a capital there is a name or a sentence start, not a conjunction).
     */
    static List<Hit> hits(final String text, final int offset, final Set<String> sourceOnly) {
        if (sourceOnly.isEmpty()) {
            return List.of();
        }
        final List<Hit> hits = new ArrayList<>();
        final Matcher words = Words.WORD.matcher(text);
        boolean more = words.find();
        while (more) {
            final String word = words.group();
            final int start = words.start();
            more = words.find();
            if (Character.isLowerCase(word.codePointAt(0))
                    && sourceOnly.contains(Occurrences.keyOf(word))
                    && (word.codePointCount(0, word.length()) > 1 || (more && startsLower(words.group())))) {
                hits.add(new Hit(offset + start, word));
            }
        }
        return List.copyOf(hits);
    }

    /** How many source-only function words a text holds, counted the way the residue finding counts them. */
    static int residueCount(final String text, @Nullable final String sourceLanguage, final String targetLanguage) {
        return hits(text, 0, sourceOnly(sourceLanguage, targetLanguage)).size();
    }

    private static boolean startsLower(final String word) {
        return Character.isLowerCase(word.codePointAt(0));
    }

    private static Optional<CheckFinding> residueFinding(final List<Hit> residue) {
        if (residue.isEmpty()) {
            return Optional.empty();
        }
        final Set<String> distinct = new LinkedHashSet<>();
        residue.forEach(hit -> distinct.add(hit.word()));
        log.debug("Function-word residue: {} word(s), {} distinct", residue.size(), distinct.size());
        log.trace("Function-word residue words: {}", distinct);
        final Hit first = residue.getFirst();
        return Optional.of(new CheckFinding(
                FindingKind.FUNCTION_WORD_RESIDUE,
                new TextSpan(first.start(), first.start() + first.word().length(), first.word()),
                "The text still holds words of the source language: " + String.join(", ", distinct)
                        + ". Translate them into the target language.",
                false));
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
            final Set<String> sourceOnly) {
        final boolean otherScript = sourceScript.isPresent()
                && targetScript.isPresent()
                && sourceScript.get() != targetScript.get()
                && targetScriptShare(line, targetScript.get()) < MIN_TARGET_SCRIPT_SHARE;
        final double rate = stopWordRate(line, sourceOnly);
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

    private static double stopWordRate(final String line, final Set<String> sourceOnly) {
        if (sourceOnly.isEmpty()) {
            return 0.0;
        }
        final Matcher words = STOP_WORD.matcher(line);
        int total = 0;
        int hits = 0;
        while (words.find()) {
            total++;
            if (sourceOnly.contains(words.group().toLowerCase(Locale.ROOT).replace('’', '\''))) {
                hits++;
            }
        }
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /** The pair's source-only function words; empty when the source is undeclared or either file is missing. */
    private static Set<String> sourceOnly(@Nullable final String sourceLanguage, final String targetLanguage) {
        return sourceLanguage == null ? Set.of() : FunctionWords.sourceOnly(sourceLanguage, targetLanguage);
    }
}
