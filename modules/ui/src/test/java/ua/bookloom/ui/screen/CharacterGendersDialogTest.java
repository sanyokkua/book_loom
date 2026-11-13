package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javafx.scene.control.RadioButton;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.dialog.CharacterGendersDialog;

/**
 * The Start question about genders nobody has settled: rows in the order given with the offered gender chosen, the
 * name's rendering, one line of count and confidence, and the radio as the one answer: clicking any radio, the offered
 * one too, stores that gender as the person's, and a row left alone stores nothing.
 */
class CharacterGendersDialogTest extends TranslatingScreenTestBase {

    private static final String CARD = "genders-card";
    private static final GlossaryEntry NAME =
            GlossaryEntry.of("e1", "p1", "Ivar", "Івар", TermType.CHARACTER, Gender.UNKNOWN, false);

    private static UnknownGender likely(
            final String id, final String term, final Gender offered, final double confidence) {
        return new UnknownGender(term, 12, true, id, offered, Origin.LIKELY, confidence, null, null);
    }

    private static UnknownGender withTarget(final UnknownGender character, final String target) {
        return new UnknownGender(
                character.term(),
                character.mentions(),
                character.suggested(),
                character.entryId(),
                character.offered(),
                character.origin(),
                character.confidence(),
                character.modelGender(),
                target);
    }

    private void ask(final List<UnknownGender> unknown) throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        glossary.willAnswer(Result.ok(List.of(NAME)));
        glossary.willListUnknownGenders(unknown);
        final CharacterGendersDialog dialog = injector.getInstance(CharacterGendersDialog.class);
        onFx(() -> dialog.startAfterAsking(() -> {}));
        awaitFx(() -> isVisibleInScene(CARD));
    }

    private boolean chosen(final String gender, final int row) {
        return ((RadioButton) required("genders-choice-" + gender + "-" + row)).isSelected();
    }

    @Test
    void rows_listed_followTheOrderGivenWithTheOfferedGenderChosen() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));

        final List<String> texts = textsUnder(required(CARD));
        assertThat(texts.indexOf("Ivar → no translation yet"))
                .isNotNegative()
                .isLessThan(texts.indexOf("Mira Holt → no translation yet"));
        assertThat(chosen("male", 0)).isTrue();
        assertThat(chosen("female", 1)).isTrue();
        // The choices name a missing gender as the table does.
        assertThat(texts).contains("not specified").doesNotContain("unknown");
    }

    @Test
    void row_withTarget_showsTheNameAndItsRendering() throws Exception {
        ask(List.of(withTarget(likely("e1", "Ivar", Gender.MALE, 0.55), "Івар")));

        assertThat(textsUnder(required(CARD))).contains("Ivar → Івар").doesNotContain("Ivar → no translation yet");
    }

    @Test
    void row_likely_showsOneLineWithCountAndConfidenceAndNoSecondNote() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));

        assertThat(textsUnder(required(CARD)))
                .contains("12 mentions · likely, low confidence", "12 mentions · likely")
                .doesNotContain("likely, low confidence", "likely");
    }

    @Test
    void note_ruleAgainstModel_showsBothAndChoosesTheRule() throws Exception {
        ask(List.of(new UnknownGender("Ivar", 12, true, "e1", Gender.MALE, Origin.RULE, null, Gender.FEMALE, null)));

        assertThat(textsUnder(required(CARD))).contains("12 mentions · rule: male · model: female");
        assertThat(chosen("male", 0)).isTrue();
    }

    @Test
    void note_ruleAgainstModelInUkrainian_isWordedInUkrainian() throws Exception {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        ask(List.of(new UnknownGender("Ivar", 12, true, "e1", Gender.MALE, Origin.RULE, null, Gender.FEMALE, null)));

        assertThat(textsUnder(required(CARD))).contains("12 згадок · правило: чоловічий · модель: жіночий");
    }

    // IF a click on the already-offered gender stored nothing, THEN agreeing with the guess would be impossible.
    @Test
    void start_offeredRadioClicked_storesThatGenderAsTheAnswer() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));
        onFx(((RadioButton) required("genders-choice-male-0"))::fire);

        onFx(() -> button("genders-start").fire());

        awaitFx(() -> !glossary.genderAnswers().isEmpty());
        assertThat(glossary.genderAnswers()).containsExactly(Map.of("e1", Gender.MALE));
    }

    @Test
    void start_otherRadioClicked_storesTheChangedGenderAndLeavesUntouchedRowsOut() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));
        onFx(((RadioButton) required("genders-choice-female-0"))::fire);

        onFx(() -> button("genders-start").fire());

        awaitFx(() -> !glossary.genderAnswers().isEmpty());
        assertThat(glossary.genderAnswers()).containsExactly(Map.of("e1", Gender.FEMALE));
    }

    @Test
    void start_noRowAnswered_storesNothing() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55)));

        onFx(() -> button("genders-start").fire());

        assertThat(glossary.genderAnswers()).isEmpty();
    }

    @Test
    void confirmAll_pressed_storesEverySuggestedRowsOfferedGender() throws Exception {
        ask(List.of(
                likely("e1", "Ivar", Gender.MALE, 0.55),
                likely("e2", "Mira Holt", Gender.FEMALE, 0.9),
                new UnknownGender("Ember", 12, false, "e3", Gender.UNKNOWN, null, null, null, null)));
        onFx(() -> button("genders-confirm-all").fire());

        onFx(() -> button("genders-start").fire());

        awaitFx(() -> !glossary.genderAnswers().isEmpty());
        assertThat(glossary.genderAnswers()).containsExactly(Map.of("e1", Gender.MALE, "e2", Gender.FEMALE));
    }

    @Test
    void footer_shown_saysWhatHappensToRowsLeftAlone() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55)));

        assertThat(textsUnder(required(CARD)))
                .contains(
                        "Rows you leave alone stay as suggestions; one confirmed later is proofread when you continue the"
                                + " translation.");
    }

    @Test
    void controls_shown_explainThemselvesOnHover() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55)));

        assertThat(TooltipProbe.tipText(required("genders-confirm-all"))).contains("suggested");
        assertThat(TooltipProbe.tipText(required("genders-choice-male-0"))).contains("best guess");
    }
}
