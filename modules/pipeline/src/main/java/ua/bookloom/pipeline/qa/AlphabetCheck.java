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
 * ({@code forbiddenLetters}), so a language that lists none is never checked. The finding is soft: it earns one
 * directed fix with the word, and never flags a segment, because a kept foreign word or a quoted foreign phrase can
 * legitimately hold such a letter; a word that stands in the source as well is such a kept word and is left alone.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlphabetCheck {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");

    static List<CheckFinding> find(final String source, final String target, final String targetLanguage) {
        final String forbidden = LanguageRules.bundled().forbiddenLetters(targetLanguage);
        if (forbidden.isEmpty()) {
            return List.of();
        }
        final String lowerSource = source.toLowerCase(Locale.ROOT);
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher matcher = WORD.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group();
            final String stray = strayLetters(word, forbidden);
            if (!stray.isEmpty() && !lowerSource.contains(word.toLowerCase(Locale.ROOT))) {
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
                        false));
            }
        }
        return List.copyOf(findings);
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
