package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The words of the read-only brief, the project name line and the fill-in dialog, in both shipped languages. */
class BriefFillMessagesTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    static Stream<Arguments> plainWords() {
        return Stream.of(
                Arguments.of(MessageKey.BRIEF_SUGGEST, "en", "✦ Fill in from the book"),
                Arguments.of(MessageKey.BRIEF_SUGGEST, "uk", "✦ Заповнити з книги"),
                Arguments.of(MessageKey.BRIEF_READ_ONLY_PAUSE, "en", "Pause"),
                Arguments.of(MessageKey.BRIEF_READ_ONLY_PAUSE, "uk", "Призупинити"),
                Arguments.of(MessageKey.BRIEF_PROJECT_NAME_LABEL, "en", "Project name"),
                Arguments.of(MessageKey.BRIEF_PROJECT_NAME_NAMED, "en", "named by you"),
                Arguments.of(MessageKey.BRIEF_PROJECT_NAME_NAMED, "uk", "задана вами"),
                Arguments.of(MessageKey.BRIEF_PROJECT_NAME_RENAME, "en", "Rename…"),
                Arguments.of(MessageKey.BRIEF_APPLY, "en", "Apply to the brief"),
                Arguments.of(MessageKey.BRIEF_APPLY, "uk", "Застосувати до брифу"),
                Arguments.of(MessageKey.BRIEF_DECIDES_GROUP, "en", "What it decides"),
                Arguments.of(MessageKey.BRIEF_STEPS_GROUP, "uk", "Кроки"),
                Arguments.of(MessageKey.BRIEF_DECIDES_NO_QUOTE, "en", "no quote — not used"),
                Arguments.of(MessageKey.BRIEF_DECIDES_READERS, "en", "Who reads it · voice"),
                Arguments.of(MessageKey.BRIEF_STEP_SYNTHESIS, "en", "Decide genre, readers and voice"),
                Arguments.of(MessageKey.BRIEF_STEP_KNOWLEDGE, "en", "What the model knows about the book"),
                Arguments.of(
                        MessageKey.BRIEF_DIALOG_WRITES_NOTHING,
                        "en",
                        "Nothing is written to the brief until you apply it."));
    }

    // IF a word of the new surfaces were missing in one language, THEN that session would show a bare key.
    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("plainWords")
    void get_newBriefWords_readAsAgreedInEachLanguage(final MessageKey key, final String tag, final String expected) {
        assertThat(messagesFor(tag).get(key)).isEqualTo(expected);
    }

    // The brief's own words: the sample's number and the keep button name what they stand for.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"en, Read sample 3, 3", "uk, Читання зразка 3, 3"})
    void get_sampleStep_namesTheSampleNumber(final String tag, final String expected, final int number) {
        assertThat(messagesFor(tag).get(MessageKey.BRIEF_STEP_SAMPLE, number)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"en, Keep Ukrainian", "uk, Залишити Ukrainian"})
    void get_keepButton_namesThePreviousLanguage(final String tag, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.BRIEF_SAME_PAIR_KEEP, "Ukrainian"))
                .isEqualTo(expected);
    }

    // IF the agreement ignored the plural, THEN "1 samples agree" would read wrong in either language.
    @ParameterizedTest(name = "{0}: {1} of {2}")
    @CsvSource({
        "en, 3, 4, 3 of 4 samples agree",
        "en, 1, 1, 1 of 1 sample agrees",
        "uk, 1, 1, 1 із 1 зразка збігається",
        "uk, 3, 4, 3 із 4 зразків збігаються",
        "uk, 2, 5, 2 із 5 зразків збігаються"
    })
    void get_agreement_followsThePluralRulesOfTheLanguage(
            final String tag, final int agreeing, final int samples, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.BRIEF_DECIDES_AGREE, agreeing, samples))
                .isEqualTo(expected);
    }
}
