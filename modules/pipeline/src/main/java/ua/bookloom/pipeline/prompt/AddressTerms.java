package ua.bookloom.pipeline.prompt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;

/**
 * The words a language uses to address a man or a woman ({@code sir}, {@code ma'am}, {@code пане}, {@code дівчино}),
 * from its {@code addressTerms} key: {@code term>male} or {@code term>female} pairs. A quote in which another character
 * addresses the narrator with one of them shows the narrator's gender in words the code can check, so the brief
 * suggestion may keep that gender. A pair with any other gender is ignored and logged.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AddressTerms {

    private static final String KEY = "addressTerms";
    private static final char ARROW = '>';

    /**
     * The address terms of a language.
     *
     * @param languageTag the non-null language tag
     * @return each lower-case term with its gender, male or female; never null, empty when the language lists none
     */
    public static Map<String, Gender> of(final String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        final Map<String, Gender> terms = new HashMap<>();
        for (final String pair : LanguageRules.bundled().wordsOf(languageTag, KEY)) {
            final int arrow = pair.indexOf(ARROW);
            final String gender = arrow < 0 ? "" : pair.substring(arrow + 1);
            switch (gender) {
                case "male" -> terms.put(term(pair, arrow), Gender.MALE);
                case "female" -> terms.put(term(pair, arrow), Gender.FEMALE);
                default -> log.warn("Address term '{}' of {} names no gender; it is ignored", pair, languageTag);
            }
        }
        log.debug("Address terms of {}: {}", languageTag, terms.size());
        return Map.copyOf(terms);
    }

    private static String term(final String pair, final int arrow) {
        return pair.substring(0, arrow).replace('’', '\'').toLowerCase(Locale.ROOT);
    }
}
