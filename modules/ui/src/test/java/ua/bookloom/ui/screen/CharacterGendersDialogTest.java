package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javafx.scene.control.CheckBox;
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
 * The Start question about genders nobody has settled: rows in the order given with the offered gender chosen, a note
 * on where it comes from, a Confirm tick that ticks itself on a change, and only ticked rows stored as the person's.
 */
class CharacterGendersDialogTest extends TranslatingScreenTestBase {

    private static final String CARD = "genders-card";
    private static final GlossaryEntry NAME =
            GlossaryEntry.of("e1", "p1", "Ivar", "Івар", TermType.CHARACTER, Gender.UNKNOWN, false);

    private static UnknownGender likely(
            final String id, final String term, final Gender offered, final double confidence) {
        return new UnknownGender(term, 12, true, id, offered, Origin.LIKELY, confidence, null);
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

    private boolean ticked(final int row) {
        return ((CheckBox) required("genders-confirm-" + row)).isSelected();
    }

    private boolean chosen(final String gender, final int row) {
        return ((RadioButton) required("genders-choice-" + gender + "-" + row)).isSelected();
    }

    @Test
    void rows_listed_followTheOrderGivenWithTheOfferedGenderChosen() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));

        final List<String> texts = textsUnder(required(CARD));
        assertThat(texts.indexOf("Ivar — 12 mentions · likely, confirm it"))
                .isNotNegative()
                .isLessThan(texts.indexOf("Mira Holt — 12 mentions · likely, confirm it"));
        assertThat(chosen("male", 0)).isTrue();
        assertThat(chosen("female", 1)).isTrue();
        // The choices name a missing gender as the table does.
        assertThat(texts).contains("not specified").doesNotContain("unknown");
        assertThat(ticked(0)).isFalse();
        assertThat(ticked(1)).isFalse();
    }

    @Test
    void note_likelyAndLowConfidence_areWordedApart() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));

        assertThat(textsUnder(required(CARD))).contains("likely, low confidence", "likely");
    }

    @Test
    void note_ruleAgainstModel_showsBothAndChoosesTheRule() throws Exception {
        ask(List.of(new UnknownGender("Ivar", 12, true, "e1", Gender.MALE, Origin.RULE, null, Gender.FEMALE)));

        assertThat(textsUnder(required(CARD))).contains("rule: male · model: female");
        assertThat(chosen("male", 0)).isTrue();
    }

    @Test
    void note_ruleAgainstModelInUkrainian_isWordedInUkrainian() throws Exception {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        ask(List.of(new UnknownGender("Ivar", 12, true, "e1", Gender.MALE, Origin.RULE, null, Gender.FEMALE)));

        assertThat(textsUnder(required(CARD))).contains("правило: чоловічий · модель: жіночий");
    }

    // IF changing the choice left the tick off, THEN a corrected gender would be dropped as a mere hint.
    @Test
    void choice_changed_ticksTheRowAndLeavesTheOthersAlone() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));

        onFx(() -> ((RadioButton) required("genders-choice-female-0")).setSelected(true));

        assertThat(ticked(0)).isTrue();
        assertThat(ticked(1)).isFalse();
    }

    // IF an untouched row were stored, THEN a guess nobody looked at would become the person's word.
    @Test
    void start_onlyTickedRows_reachAnswerGenders() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55), likely("e2", "Mira Holt", Gender.FEMALE, 0.9)));
        onFx(() -> ((RadioButton) required("genders-choice-female-0")).setSelected(true));
        onFx(() -> ((CheckBox) required("genders-confirm-1")).setSelected(true));

        onFx(() -> button("genders-start").fire());

        awaitFx(() -> !glossary.genderAnswers().isEmpty());
        assertThat(glossary.genderAnswers()).containsExactly(Map.of("e1", Gender.FEMALE, "e2", Gender.FEMALE));
    }

    @Test
    void start_noRowTicked_storesNothing() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55)));

        onFx(() -> button("genders-start").fire());

        assertThat(glossary.genderAnswers()).isEmpty();
    }

    @Test
    void controls_shown_explainThemselvesOnHover() throws Exception {
        ask(List.of(likely("e1", "Ivar", Gender.MALE, 0.55)));

        assertThat(TooltipProbe.tipText(required("genders-confirm-0"))).contains("make this gender yours");
        assertThat(TooltipProbe.tipText(required("genders-choice-male-0"))).contains("best guess");
    }
}
