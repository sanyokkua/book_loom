package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The languages and pairs the first version of the language-rules map ships files for, shared by the tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class V1Languages {

    /** The twelve languages with a file of their own. */
    public static final List<String> LANGUAGES =
            List.of("en", "ru", "uk", "fr", "hr", "pl", "cs", "sl", "sk", "es", "pt", "de");

    /** The pairs with a file of their own. */
    public static final List<String> PAIRS = List.of(
            "en-uk", "en-ru", "en-pl", "uk-en", "ru-uk", "uk-ru", "pl-uk", "uk-pl", "cs-uk", "uk-cs", "sk-uk", "uk-sk",
            "sl-uk", "uk-sl", "hr-uk", "uk-hr");

    /** The tags as a stream, for a parameterized test. */
    public static Stream<String> languages() {
        return LANGUAGES.stream();
    }

    /** The pairs as a stream, for a parameterized test. */
    public static Stream<String> pairs() {
        return PAIRS.stream();
    }
}
