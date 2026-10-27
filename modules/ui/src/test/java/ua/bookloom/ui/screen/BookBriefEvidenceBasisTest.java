package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * What each predicted field says it stands on (15i.B4's basis, worded by 15i.U4): the passages that agreed, what the
 * model knows of the book, or the code's own count, and an "uncertain" chip on a field nothing supported.
 */
class BookBriefEvidenceBasisTest extends BookBriefScreenTestBase {

    private void suggestWithGenreEvidence(final FieldEvidence evidence) throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class))
                .answersBrief(Result.ok(new BriefSuggestion(
                        "Gothic novel",
                        Register.NEUTRAL,
                        null,
                        null,
                        NarratorPerson.UNSPECIFIED,
                        Gender.UNKNOWN,
                        Map.of(BriefField.GENRE, evidence))));
        fillInAndApply();
        awaitFx(() -> isShown("brief-evidence-genre"));
    }

    static Stream<Arguments> basisLines() {
        return Stream.of(
                Arguments.of(
                        Named.of("3 of 4 passages", new FieldEvidence(Basis.PASSAGES, List.of(), 3, 4)),
                        "3 of 4 passages"),
                Arguments.of(
                        Named.of("1 of 1 passage", new FieldEvidence(Basis.PASSAGES, List.of(), 1, 1)),
                        "1 of 1 passage"),
                Arguments.of(
                        Named.of("a quote", new FieldEvidence(Basis.PASSAGES, List.of("a lonely house"), 2, 3)),
                        "“a lonely house” · 2 of 3 passages"),
                Arguments.of(
                        Named.of("knowledge", new FieldEvidence(Basis.KNOWLEDGE, List.of(), 1, 4)),
                        "From what the model knows of this book"),
                Arguments.of(
                        Named.of("measured", new FieldEvidence(Basis.MEASURED, List.of(), 1, 1)),
                        "Counted over the whole book"));
    }

    // IF the basis were worded in "samples" or left out, THEN a person could not tell how many passages of the book
    // stood behind a field, or that it came from what the model remembers.
    @ParameterizedTest
    @MethodSource("basisLines")
    void evidenceLine_suggestedFieldWithABasis_saysWhatItStandsOnWithNoUncertainChip(
            final FieldEvidence evidence, final String expected) throws TimeoutException {
        suggestWithGenreEvidence(evidence);

        assertThat(((Label) required("brief-evidence-genre")).getText()).isEqualTo(expected);
        assertThat(isShown("brief-evidence-genre-uncertain")).isFalse();
    }

    // IF an unsupported field looked like a supported one, THEN a guess would pass for a reading of the book.
    @Test
    void evidenceLine_unsupportedField_showsTheUncertainChipBesideHowManyPassagesAgreed() throws TimeoutException {
        suggestWithGenreEvidence(new FieldEvidence(Basis.UNCERTAIN, List.of(), 1, 4));

        assertThat(isShown("brief-evidence-genre-uncertain")).isTrue();
        assertThat(((Label) required("brief-evidence-genre-uncertain")).getText())
                .isEqualTo("? Uncertain");
        assertThat(((Label) required("brief-evidence-genre")).getText()).isEqualTo("1 of 4 passages");
    }

    // IF the chip had no explanation, THEN "uncertain" would not say the field was left as it was.
    @Test
    void uncertainChip_shown_explainsItselfOnHover() throws TimeoutException {
        suggestWithGenreEvidence(new FieldEvidence(Basis.UNCERTAIN, List.of(), 1, 4));

        assertThat(TooltipProbe.tipText(required("brief-evidence-genre-uncertain")))
                .contains("left it as it was");
    }

    // IF the basis words were English only, THEN a Ukrainian session would read English under a Ukrainian field.
    @Test
    void evidenceLine_underUkrainian_isWordedInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));

        suggestWithGenreEvidence(new FieldEvidence(Basis.PASSAGES, List.of(), 3, 4));

        assertThat(((Label) required("brief-evidence-genre")).getText())
                .matches(".*\\p{IsCyrillic}.*")
                .doesNotContain("passages");
    }

    // IF the evidence rows were not in the right column, THEN the basis would sit away from the field it vouches for.
    @Test
    void evidenceRows_suggestedField_sitInTheToneCardOfTheRightColumn() throws TimeoutException {
        suggestWithGenreEvidence(new FieldEvidence(Basis.PASSAGES, List.of(), 3, 4));

        assertThat(isInside("brief-evidence-genre", "brief-column-right")).isTrue();
        assertThat(isInside("brief-evidence-genre", "brief-tone-card")).isTrue();
    }
}
