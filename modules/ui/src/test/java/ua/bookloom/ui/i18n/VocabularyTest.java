package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The words of the vocabulary table (mockups README, Words) on the main screens: a person translating a book reads
 * "segment", "to review" and "Try again now", never the engine's chunk, flagged or Retry now. The technical words stay
 * in the keys of the Detailed views, Settings › Providers and Models, and Import's technical line.
 */
class VocabularyTest {

    private static final Pattern ENGINE_WORDS =
            Pattern.compile("\\b(flagged|retry now|send again|chunks?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENDERED_PAST = Pattern.compile(
            "\\b(Ви|ви|ти)\\s+(не\\s+)?(вибрав|вибрала|зробив|зробила|ввів|ввела|змінив|змінила|обрав|обрала)\\b");

    // Keys of Detailed / Advanced views and of the Settings tabs where the technical words are the point.
    private static final Set<String> TECHNICAL_PREFIXES = Set.of(
            "progress.detail.",
            "live.call.",
            "settings.models.field.tokens",
            "settings.tab.providers",
            "settings.providers.",
            "settings.models.limit.tech",
            "structure.tokens.hint");

    private static boolean isMainScreen(final String key) {
        return TECHNICAL_PREFIXES.stream().noneMatch(key::startsWith);
    }

    private static Stream<Map.Entry<String, String>> mainScreenValues(final String language) {
        return Catalogues.load(language).entrySet().stream().filter(e -> isMainScreen(e.getKey()));
    }

    // IF a main-screen line said flagged, chunk or Send again, THEN a person would meet the engine's words.
    @ParameterizedTest
    @ValueSource(strings = {"en"})
    void mainScreenValues_english_haveNoEngineWords(final String language) {
        final var hits = mainScreenValues(language)
                .filter(e -> ENGINE_WORDS.matcher(e.getValue()).find())
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits)
                .as("main-screen keys that still use the engine's words")
                .isEmpty();
    }

    // The Ukrainian source of the same rule: the English loan "чанк" is never a word of the interface.
    @ParameterizedTest
    @ValueSource(strings = {"uk"})
    void mainScreenValues_ukrainian_haveNoChunkLoanWord(final String language) {
        final var hits = mainScreenValues(language)
                .filter(e -> e.getValue().toLowerCase(java.util.Locale.ROOT).contains("чанк"))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits).isEmpty();
    }

    // "No gendered past tense for you": a verb that makes the user a man or a woman.
    @ParameterizedTest
    @ValueSource(strings = {"uk"})
    void values_ukrainian_doNotGenderTheUser(final String language) {
        final var hits = Catalogues.load(language).entrySet().stream()
                .filter(e -> GENDERED_PAST.matcher(e.getValue()).find())
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "run.action.tryAgain|en|Try again now",
                "run.action.tryAgain|uk|Спробувати знову зараз",
                "review.retry|en|↻ Try again",
                "error.retry|en|Try again",
                "settings.models.refused.send|en|Try again now",
                "brief.option.transliterate|en|Spell in Ukrainian letters",
                "brief.suggest|en|✦ Fill in from the book",
                "brief.policy.foreign|en|Text in other languages",
            })
    void wording_tableEntries_matchTheReadme(final String key, final String language, final String expected) {
        assertThat(Catalogues.load(language)).containsEntry(key, expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void skipAction_withACount_alwaysNamesTheCount(final String language) {
        assertThat(Catalogues.load(language).get("run.action.skip")).contains("#");
    }

    // IF the provider tab's technical words leaked into prose, the English would say provider where it means the model.
    @ParameterizedTest
    @ValueSource(strings = {"nav.settings.tip", "export.suggestName.noModel", "brief.suggest.noModel"})
    void aiModelWording_promptsToChooseAModel_sayAiModelNotProvider(final String key) {
        assertThat(Catalogues.load("en").get(key)).contains("AI model").doesNotContain("provider");
    }
}
