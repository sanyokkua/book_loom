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

    // IF a main-screen line said LLM, THEN a person would meet the engine's word for an AI model (Words table).
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void mainScreenValues_eachLanguage_sayAiModelNotLlm(final String language) {
        final var hits = mainScreenValues(language)
                .filter(e -> e.getValue().contains("LLM"))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits).isEmpty();
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
                "brief.option.transliterate|en|Spell in the target alphabet",
                "brief.suggest|en|✦ Fill in from the book",
                "brief.policy.foreign|en|Text in other languages",
                "activity.providerCheck|en|AI model test",
                "activity.providerCheck|uk|Перевірка моделі ШІ",
                "dialog.samePair.use|uk|Створити переклад ({0}) з цією назвою",
                "projects.empty.step3|uk|Запустіть переклад. Книга перекладається годинами; можна зупинити й продовжити будь-коли.",
                "brief.aux.nav|en|Table of contents and chapter titles",
                "brief.aux.alt|en|Picture descriptions",
                "brief.aux.metadata|en|The book's title and author",
                "brief.aux.frontmatter|en|Copyright and edition notes",
                "brief.source.label|en|Translate from",
                "brief.source.auto|en|Find out from the text",
                "brief.target.label|en|Translate into",
                "brief.tone.audience|en|Who reads it",
                "brief.tone.narrator|en|Who tells the story",
                "brief.narrator.unspecified|en|Unknown",
                "brief.narrator.first|en|“I”",
                "brief.narrator.third|en|“He / she”",
                "brief.tone.narrator.gender|en|Narrator's gender",
                "brief.card.policies|en|How to translate",
                "brief.card.quality|en|Quality and speed",
                "brief.policy.names|en|Names of people and places",
                "brief.option.keepOriginal|en|Keep as written",
                "brief.policy.balance|en|Close to the original ↔ natural",
                "brief.policy.balance.hint|en|closer · balanced · freer",
                "structure.readingOrder|en|Chapters",
                "structure.readingOrder|uk|Розділи",
                "export.card.title|en|Where to save",
                "export.also.title|en|Also save",
            })
    void wording_tableEntries_matchTheReadme(final String key, final String language, final String expected) {
        assertThat(Catalogues.load(language)).containsEntry(key, expected);
    }

    // IF the quit questions still said nothing survives a restart, THEN they would contradict Continue after restart.
    @ParameterizedTest
    @CsvSource({
        "en, confirm.quit.text, restart yet",
        "en, confirm.quitUnexported.text, restart yet",
        "en, confirm.quitUnexported.yes.tip, is lost",
        "uk, confirm.quit.text, поки що нічого не зберігається",
        "uk, confirm.quitUnexported.text, поки що нічого не зберігається",
        "uk, confirm.quitUnexported.yes.tip, буде втрачено",
    })
    void quitQuestions_progressIsSaved_noStaleLossClaim(final String language, final String key, final String stale) {
        assertThat(Catalogues.load(language).get(key)).doesNotContain(stale);
    }

    // IF a main-screen line said alt-text, ToC, front matter or metadata, THEN a reader would meet the file format's
    // words where the Words table gives plain ones.
    @ParameterizedTest
    @ValueSource(strings = {"en"})
    void mainScreenValues_english_haveNoFileFormatWords(final String language) {
        final Pattern formatWords = Pattern.compile(
                "alt[- ]texts?|\\bToC\\b|front ?matter|metadata|navigation labels", Pattern.CASE_INSENSITIVE);
        final var hits = mainScreenValues(language)
                .filter(e -> formatWords.matcher(e.getValue()).find())
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits).isEmpty();
    }

    // IF the import message named a label the Book Brief does not show, THEN the person would look for it in vain.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void twoLanguageMarks_namesTheSourceLabelTheBriefShows(final String language) {
        final Map<String, String> catalogue = Catalogues.load(language);

        assertThat(catalogue.get("import.verdict.twoMarks")).contains(catalogue.get("brief.source.label"));
    }

    // IF the question said the book "is being translated", THEN it would be false for a paused translation, which it
    // also asks about (W6-6).
    @ParameterizedTest
    @CsvSource({
        "en, confirm.switch.text, is being translated",
        "en, confirm.removeRunning.text, is being translated",
        "uk, confirm.switch.text, Зараз перекладається",
        "uk, confirm.removeRunning.text, Зараз перекладається",
    })
    void stopQuestions_pausedOrTranslating_doNotClaimTheModelIsAtWork(
            final String language, final String key, final String claim) {
        assertThat(Catalogues.load(language).get(key)).doesNotContain(claim);
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

    // IF the theme hint named a choice the control does not show, THEN a person would look for a "Match system" that
    // is not there.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void themeHint_namesTheSystemChoiceAsTheControlShowsIt(final String language) {
        final Map<String, String> catalogue = Catalogues.load(language);

        assertThat(catalogue.get("appearance.theme.hint")).contains(catalogue.get("appearance.theme.system"));
    }
}
