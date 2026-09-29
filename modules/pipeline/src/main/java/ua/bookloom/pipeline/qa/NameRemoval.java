package ua.bookloom.pipeline.qa;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.WholeWord;

/**
 * The {@code Keep original} name removal {@link ScriptCheck} and {@link EchoCheck} both apply before comparing
 * texts, so a names-only line kept by policy is never held against either check (design D8).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameRemoval {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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

    private static String removeWholeWord(final String text, final String term) {
        return WholeWord.pattern(term).matcher(text).replaceAll("");
    }

    private static String collapseWhitespace(final String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
