package ua.bookloom.pipeline.qa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;
import ua.bookloom.pipeline.checks.TextSpan;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * A letter the target language's alphabet does not have, inside a target word — the Russian {@code ы ъ э ё} in a
 * Ukrainian text ({@code бэкона}, {@code кобыли}). The letters come from the language's own file
 * ({@code forbiddenLetters}), so a language that lists none is never checked. The finding blocks acceptance like a
 * mixed-script word and earns the directed fix with the word; a draft that still holds the letter after the repair
 * round is flagged. A word that stands in the source as well is a kept foreign word and is left alone.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlphabetCheck {

    private static final String ARROW = " → ";
    private static final int MIN_STEM = 3;
    private static final int MAX_ENDING = 3;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");

    static List<CheckFinding> find(
            final String source, final String target, final String targetLanguage, final List<String> glossaryPairs) {
        final String forbidden = LanguageRules.bundled().forbiddenLetters(targetLanguage);
        if (forbidden.isEmpty()) {
            return List.of();
        }
        final String lowerSource = source.toLowerCase(Locale.ROOT);
        final Set<String> typed = renderingWords(glossaryPairs);
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher matcher = WORD.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group();
            final String stray = strayLetters(word, forbidden);
            if (!stray.isEmpty() && !lowerSource.contains(word.toLowerCase(Locale.ROOT)) && !isTyped(word, typed)) {
                log.debug(
                        "Alphabet check: letter(s) {} outside the {} alphabet at {}",
                        stray,
                        targetLanguage,
                        matcher.start());
                findings.add(new CheckFinding(
                        FindingKind.ALPHABET,
                        new TextSpan(matcher.start(), matcher.end(), word),
                        "The word has the letter " + stray + ", which the target language does not use. Write the"
                                + " word with the target language's own letters.",
                        true));
            }
        }
        return List.copyOf(findings);
    }

    // A glossary rendering is what the person typed, so its word, or a word that declines its stem, is theirs to keep.
    private static Set<String> renderingWords(final List<String> glossaryPairs) {
        final Set<String> words = new LinkedHashSet<>();
        for (final String pair : glossaryPairs) {
            final int at = pair.indexOf(ARROW);
            if (at > 0) {
                WORD.matcher(pair.substring(at + ARROW.length()).toLowerCase(Locale.ROOT))
                        .results()
                        .forEach(word -> words.add(word.group()));
            }
        }
        return words;
    }

    private static boolean isTyped(final String word, final Set<String> typed) {
        final String lower = word.toLowerCase(Locale.ROOT);
        return typed.stream()
                .anyMatch(held -> lower.equals(held)
                        || (held.length() >= MIN_STEM
                                && lower.startsWith(held)
                                && lower.length() <= held.length() + MAX_ENDING));
    }

    private static String strayLetters(final String word, final String forbidden) {
        final Set<String> stray = new LinkedHashSet<>();
        word.toLowerCase(Locale.ROOT).codePoints().forEach(point -> {
            if (forbidden.indexOf(point) >= 0) {
                stray.add(new String(Character.toChars(point)));
            }
        });
        return String.join("", stray);
    }
}
