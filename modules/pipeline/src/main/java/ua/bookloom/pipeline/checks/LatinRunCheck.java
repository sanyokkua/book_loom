package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.lang.Script;

/**
 * Two or more Latin words in a row that the source also holds, left as they are in a target written in another script
 * («Daily Courier» in a Cyrillic paragraph): the model copied a phrase it should have translated. Blocking. A single
 * Latin word is left alone, as is a target that is mostly Latin (an echo, owned by other checks), since a name or a brand is often kept on purpose, and glossary names, kept foreign runs
 * and protected spans are already out of the texts this check reads.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LatinRunCheck {

    private static final Set<Script> CHECKED = Set.of(Script.CYRILLIC, Script.GREEK);
    private static final String LATIN_WORD = "[\\p{IsLatin}][\\p{IsLatin}\\p{M}'’ʼ-]*";
    private static final Pattern RUN = Pattern.compile(LATIN_WORD + "(?:[ \\u00a0]+" + LATIN_WORD + ")+");

    static List<CheckFinding> find(final String source, final String target, final Optional<Script> targetScript) {
        if (targetScript.isEmpty() || !CHECKED.contains(targetScript.get())) {
            log.debug("Latin run check skipped: target script {} is not Cyrillic or Greek", targetScript);
            return List.of();
        }
        final String lowerSource = source.toLowerCase(Locale.ROOT);
        final long ownLetters = lettersOf(target, targetScript.get());
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher runs = RUN.matcher(target);
        while (runs.find()) {
            if (lowerSource.contains(runs.group().toLowerCase(Locale.ROOT))
                    && runs.group().length() < ownLetters) {
                log.debug("Latin run of the source left in the target at {}", runs.start());
                findings.add(new CheckFinding(
                        FindingKind.LATIN_RUN,
                        new TextSpan(runs.start(), runs.end(), runs.group()),
                        "The source's own words are left untranslated. Translate the phrase into the target language.",
                        true));
            }
        }
        return List.copyOf(findings);
    }

    // A target that is mostly Latin is an untranslated echo or a leftover paragraph, which the echo, script and
    // language-identity checks own; this check is for a phrase left inside a translated text.
    private static long lettersOf(final String target, final Script script) {
        return target.codePoints()
                .filter(point ->
                        Character.isLetter(point) && script.letterScripts().contains(Character.UnicodeScript.of(point)))
                .count();
    }
}
