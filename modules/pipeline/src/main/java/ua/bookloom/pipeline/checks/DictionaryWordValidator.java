package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link WordValidator} that looks each word up in a word list the caller supplies. It is the shape any bundled
 * dictionary plugs into; no word list ships with the application.
 */
@Slf4j
public final class DictionaryWordValidator implements WordValidator {

    private static final String NOTE =
            "This word is not in the dictionary and may be garbled or invented. Check it and use the real word.";

    private final Predicate<String> isKnown;

    /**
     * Creates a validator over a word list.
     *
     * @param isKnown whether a lower-cased word is a real word; never null
     */
    public DictionaryWordValidator(final Predicate<String> isKnown) {
        this.isKnown = Objects.requireNonNull(isKnown, "isKnown");
    }

    @Override
    public List<CheckFinding> find(final String target, final String targetLanguage) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher matcher = Words.WORD.matcher(target);
        while (matcher.find()) {
            final String word = matcher.group();
            if (!isKnown.test(word.toLowerCase(Locale.ROOT))) {
                log.debug("Dictionary does not know a word at {}..{}", matcher.start(), matcher.end());
                log.trace("Dictionary does not know '{}'", word);
                findings.add(new CheckFinding(
                        FindingKind.UNKNOWN_WORD, new TextSpan(matcher.start(), matcher.end(), word), NOTE, false));
            }
        }
        return List.copyOf(findings);
    }
}
