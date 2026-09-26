package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The resolver renders a catalogue key for the display locale. Expected English strings are the published wording,
 * written out by hand; Ukrainian forms are asserted by their properties, because the Ukrainian text is a working
 * draft.
 */
class MessagesTest {

    private static final Locale UKRAINIAN = Locale.forLanguageTag("uk");
    private static final char NO_BREAK_SPACE = ' ';

    private static Messages messagesFor(final Locale locale) {
        return new Messages(new FixedLocaleProvider(locale));
    }

    // IF the English plural chose the wrong form or the number grouping were wrong, THEN the text would differ.
    @ParameterizedTest
    @CsvSource({
        "1, 1 segment remaining",
        "469, 469 segments remaining",
        "1240, '1,240 segments remaining'",
    })
    void get_remainingSegmentsInEnglish_selectsFormAndGroupsThousands(final int count, final String expected) {
        assertThat(messagesFor(Locale.ENGLISH).get(MessageKey.TRANSLATING_SEGMENTS_REMAINING, count))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1 сегмент залишився",
        "3, 3 сегменти залишилися",
        "5, 5 сегментів залишилося",
        "21, 21 сегмент залишився",
    })
    void get_remainingSegmentsInUkrainian_selectsOneFewManyForm(final int count, final String expected) {
        // IF Ukrainian were an English-style pair, THEN 3 and 5 would take the same ending as each other.
        assertThat(messagesFor(UKRAINIAN).get(MessageKey.TRANSLATING_SEGMENTS_REMAINING, count))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "en, paused, The run was paused.",
        "uk, paused, Переклад призупинено.",
        "en, unknownToken, The run reached a milestone.",
    })
    void get_milestoneEntry_selectsTheCataloguesOwnWording(
            final String language, final String token, final String expected) {
        // IF a milestone's text came from the caller, THEN the catalogue would not own the sentence.
        assertThat(messagesFor(Locale.forLanguageTag(language)).get(MessageKey.LOG_MILESTONE, token))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void get_remainingSegmentsInUkrainian_containsTheCountAndNoLatinLetters(final int count) {
        // IF the Ukrainian key held an English sentence, THEN Latin letters would appear around the number.
        assertThat(messagesFor(UKRAINIAN).get(MessageKey.TRANSLATING_SEGMENTS_REMAINING, count))
                .contains(String.valueOf(count))
                .doesNotContainPattern("[A-Za-z]");
    }

    @ParameterizedTest
    @ValueSource(ints = {1240})
    void get_remainingSegmentsInUkrainian_groupsThousandsWithoutAComma(final int count) {
        // IF the count were formatted for the machine's region, THEN a Ukrainian text would carry an American comma.
        assertThat(messagesFor(UKRAINIAN).get(MessageKey.TRANSLATING_SEGMENTS_REMAINING, count))
                .contains(String.valueOf(NO_BREAK_SPACE))
                .doesNotContain(",");
    }

    // IF the segment number were joined to the sentence instead of substituted, THEN it would not appear whole here.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void get_acceptedLogEntry_substitutesTheSegmentNumber(final String language) {
        assertThat(messagesFor(Locale.forLanguageTag(language)).get(MessageKey.LOG_ACCEPTED, 741))
                .contains("741");
    }

    @ParameterizedTest
    @ValueSource(ints = {741})
    void get_acceptedLogEntryInUkrainian_isNotAnEnglishSentence(final int segment) {
        // IF the Ukrainian catalogue reused the English text, THEN Latin letters would appear.
        assertThat(messagesFor(UKRAINIAN).get(MessageKey.LOG_ACCEPTED, segment))
                .contains("741")
                .doesNotContainPattern("[A-Za-z]");
    }

    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void locale_resolvedFromProvider_isTheDisplayLocale(final String language) {
        final Locale requested = Locale.forLanguageTag(language);

        assertThat(messagesFor(requested).locale().getLanguage()).isEqualTo(language);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void get_everyKeyWithoutArguments_isResolvedNotTheRawKey(final String language) {
        // IF a key were missing from a bundle, THEN the resolver would fall back to showing the raw key.
        final Messages messages = messagesFor(Locale.forLanguageTag(language));
        final List<String> resolved = Stream.of(MessageKey.NAV_IMPORT, MessageKey.SHELL_TITLE, MessageKey.COMMON_CLOSE)
                .map(messages::get)
                .toList();

        assertThat(resolved)
                .doesNotContainAnyElementsOf(List.of(
                        MessageKey.NAV_IMPORT.key(), MessageKey.SHELL_TITLE.key(), MessageKey.COMMON_CLOSE.key()))
                .noneMatch(String::isBlank);
    }

    // IF the bundle handed to FXML were not the active catalogue, THEN a %key in a view would render in the wrong
    // language or as the raw key.
    @ParameterizedTest
    @CsvSource({
        "en, nav.import, Import book",
        "uk, nav.import, Імпорт книги",
        "en, nav.brief, Book Brief",
        "uk, nav.brief, Опис книги",
    })
    void bundle_activeLocale_resolvesRawCatalogueEntry(final String language, final String key, final String expected) {
        assertThat(messagesFor(Locale.forLanguageTag(language)).bundle().getString(key))
                .isEqualTo(expected);
    }

    // IF the waiting wording were English-only or lost its time argument, THEN a Ukrainian reader would see an English
    // banner or a banner with no clock.
    @ParameterizedTest
    @CsvSource({
        "en, 0:12, Waiting for the model… 0:12",
        "uk, 0:12, Очікування відповіді моделі… 0:12",
    })
    void get_waitingForModel_rendersTheClockInBothLanguages(
            final String language, final String clock, final String expected) {
        assertThat(messagesFor(Locale.forLanguageTag(language)).get(MessageKey.TRANSLATING_WAITING_FOR_MODEL, clock))
                .isEqualTo(expected);
    }
}
