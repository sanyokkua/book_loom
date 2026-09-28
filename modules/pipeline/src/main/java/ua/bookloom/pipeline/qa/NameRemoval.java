package ua.bookloom.pipeline.qa;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.NamePolicy;

/**
 * The {@code Keep original} name removal {@link ScriptCheck} and {@link EchoCheck} both apply before comparing
 * texts, so a names-only line kept by policy is never held against either check (design D8).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameRemoval {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String NOT_AFTER_WORD_CHARACTER = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_BEFORE_WORD_CHARACTER = "(?![\\p{L}\\p{N}])";

    /**
     * The source display text with every whole-word glossary-term occurrence removed under
     * {@link NamePolicy#KEEP_ORIGINAL}.
     *
     * @param input the check input
     * @return {@code input.sourceDisplayText()} unchanged under any other name policy
     */
    static String sourceWithoutNames(final SoftCheckInput input) {
        return withoutNames(input.sourceDisplayText(), input);
    }

    /**
     * The target display text with every whole-word glossary-term occurrence removed under
     * {@link NamePolicy#KEEP_ORIGINAL}.
     *
     * @param input the check input
     * @return {@code input.targetDisplayText()} unchanged under any other name policy
     */
    static String targetWithoutNames(final SoftCheckInput input) {
        return withoutNames(input.targetDisplayText(), input);
    }

    private static String withoutNames(final String text, final SoftCheckInput input) {
        if (input.namePolicy() != NamePolicy.KEEP_ORIGINAL
                || input.glossaryTerms().isEmpty()) {
            return text;
        }
        String result = text;
        for (final String term : input.glossaryTerms()) {
            result = removeWholeWord(result, term);
        }
        return collapseWhitespace(result);
    }

    /**
     * Removes {@code term} wherever no letter or digit touches it on either side. Not {@code \b}: since JDK 19 that
     * boundary is ASCII-only, so it never matches around a Cyrillic, Greek or Han name.
     */
    private static String removeWholeWord(final String text, final String term) {
        return Pattern.compile(NOT_AFTER_WORD_CHARACTER + Pattern.quote(term) + NOT_BEFORE_WORD_CHARACTER)
                .matcher(text)
                .replaceAll("");
    }

    private static String collapseWhitespace(final String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
