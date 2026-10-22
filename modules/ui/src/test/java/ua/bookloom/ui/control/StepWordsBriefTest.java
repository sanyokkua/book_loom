package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StepEntry;

/** The three request steps of the brief suggestion read as words, with a sample's place in the book after its name. */
class StepWordsBriefTest {

    private static StepEntry ended(final String label, final @Nullable String place) {
        return new StepEntry(StepKind.REQUEST, label, 0, StepState.ENDED, place, StepResult.DONE, null);
    }

    // IF the raw label showed, THEN the dialog would list "sample-1" and "knowledge" as a person's steps.
    @ParameterizedTest(name = "{0} {1} -> {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "en|knowledge||What the model knows about the book",
                "en|sample-1|ch1 · p02–p14|Read sample 1 · ch1 · p02–p14",
                "en|sample-4||Read sample 4",
                "en|synthesis||Decide genre, readers and voice",
                "uk|knowledge||Що модель знає про книгу",
                "uk|sample-2|ch1 · p15–p27|Читання зразка 2 · ch1 · p15–p27",
                "uk|synthesis||Визначення жанру, читачів і голосу"
            })
    void name_briefRequestStep_isReadInTheInterfaceLanguage(
            final String tag, final String label, final @Nullable String place, final String expected) {
        final Messages messages = new Messages(() -> Locale.forLanguageTag(tag));

        assertThat(StepWords.name(messages, ended(label, place))).isEqualTo(expected);
    }

    // Any other request label keeps its own words, as the other work announces them.
    @ParameterizedTest
    @CsvSource({"prescan", "sample", "export"})
    void name_otherRequestLabel_isUnchanged(final String label) {
        assertThat(StepWords.name(new Messages(() -> Locale.ENGLISH), ended(label, null)))
                .isEqualTo(label);
    }
}
