package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.ForeignWords;

/**
 * A word of another language that the target language's file lists with its replacement ({@code тоже>теж} in a
 * Ukrainian text), found whole in the target and absent from the source, so a quoted word of the book is left alone.
 * The finding names the exact replacement. Soft: it earns one directed fix and never flags a segment.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForeignWordCheck {

    static List<CheckFinding> find(final String source, final String target, final String targetLanguage) {
        final Map<String, String> listed = ForeignWords.of(targetLanguage);
        if (listed.isEmpty()) {
            return List.of();
        }
        final String lowerSource = source.toLowerCase(Locale.ROOT);
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher words = Words.WORD.matcher(target);
        while (words.find()) {
            final String word = words.group();
            final String listedReplacement = listed.get(word.toLowerCase(Locale.ROOT));
            if (listedReplacement != null && !lowerSource.contains(word.toLowerCase(Locale.ROOT))) {
                final String replacement = Character.isUpperCase(word.codePointAt(0))
                        ? listedReplacement.substring(0, 1).toUpperCase(Locale.ROOT) + listedReplacement.substring(1)
                        : listedReplacement;
                log.debug("Foreign word \"{}\" at {} has the replacement \"{}\"", word, words.start(), replacement);
                findings.add(new CheckFinding(
                        FindingKind.FOREIGN_WORD,
                        new TextSpan(words.start(), words.end(), word),
                        "The word belongs to another language: write \"" + replacement + "\" instead.",
                        false));
            }
        }
        return List.copyOf(findings);
    }
}
