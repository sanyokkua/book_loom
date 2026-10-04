package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The same word twice in a row ({@code одно одно таки}), a decode slip a model makes when it loses its place. A
 * soft finding, because speech repeats on purpose ({@code дуже, дуже}); a doubling the source has too
 * ({@code very very}) is the author's and is left alone, and a comma between the words makes them two words.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DuplicateWordCheck {

    private static final int MIN_WORD_LENGTH = 2;
    private static final Pattern DOUBLED = Pattern.compile(
            "(?<![\\p{L}\\p{M}'’ʼ-])(\\p{L}[\\p{L}\\p{M}'’ʼ-]*)[ \\t]+\\1(?![\\p{L}\\p{M}'’ʼ-])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static List<CheckFinding> find(final String source, final String target) {
        final List<CheckFinding> findings = new ArrayList<>();
        final boolean sourceDoubles = DOUBLED.matcher(source).find();
        final Matcher matcher = DOUBLED.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group(1);
            if (word.length() >= MIN_WORD_LENGTH && !sourceDoubles) {
                log.debug("Doubled word at {}..{}", matcher.start(), matcher.end());
                findings.add(new CheckFinding(
                        FindingKind.DUPLICATE_WORD,
                        new TextSpan(matcher.start(), matcher.end(), matcher.group()),
                        "The word is written twice in a row. Keep one, unless the source repeats it on purpose.",
                        false));
            }
        }
        return List.copyOf(findings);
    }
}
