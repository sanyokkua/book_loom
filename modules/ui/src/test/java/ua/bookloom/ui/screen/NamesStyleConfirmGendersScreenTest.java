package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/** "Confirm suggested genders" in Names &amp; style: shown while a suggestion stands, confirms them all, and Undo all. */
class NamesStyleConfirmGendersScreenTest extends TranslatingScreenTestBase {

    private static final String CONFIRM = "names-style-confirm-genders";
    private static final GlossaryEntry ANNE = new GlossaryEntry(
                    "e1", "p1", "Anne", "Анна", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.FEMALE);
    private static final GlossaryEntry BORIS = new GlossaryEntry(
                    "e2", "p1", "Boris", "Борис", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    private static final GlossaryEntry CARL =
            new GlossaryEntry("e3", "p1", "Carl", "Карл", TermType.CHARACTER, Gender.MALE, false);

    private void showGlossary(final GlossaryEntry... entries) throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(entries)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private List<String> genderLabels() {
        return ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                .map(label -> ((Label) label).getText())
                .toList());
    }

    @Test
    void button_suggestedGendersStand_isShownWithWordsAndAHoverExplanation() throws Exception {
        showGlossary(ANNE, BORIS, CARL);

        assertThat(button(CONFIRM).isVisible()).isTrue();
        assertThat(button(CONFIRM).getText()).isEqualTo("Confirm suggested genders");
        assertThat(TooltipProbe.tipText(required(CONFIRM))).startsWith("Turns every suggested character gender");
    }

    @Test
    void button_noSuggestedGender_isNotShown() throws Exception {
        showGlossary(CARL);

        assertThat(button(CONFIRM).isVisible()).isFalse();
    }

    // IF the press stored nothing, THEN the (suggested) mark would stay and the run would still not hold the gender.
    @Test
    void press_suggestedGendersStand_storesThemAsThePersonsAndTheMarkGoes() throws Exception {
        showGlossary(ANNE, BORIS, CARL);
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));

        onFx(() -> button(CONFIRM).fire());
        awaitFx(() -> glossary.updated().size() == 2);

        assertThat(glossary.updated())
                .extracting(GlossaryEntry::genderOrigin)
                .containsExactly(GenderOrigin.PERSON, GenderOrigin.PERSON);
        awaitFx(() -> genderLabels().stream().noneMatch(label -> label.contains("(suggested)")));
        awaitFx(() -> !button(CONFIRM).isVisible());
    }

    // IF Undo all left the confirmation in place, THEN a mistaken press could not be taken back.
    @Test
    void undoAll_afterThePress_putsTheSuggestedMarksBack() throws Exception {
        showGlossary(ANNE, BORIS, CARL);
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));
        onFx(() -> button(CONFIRM).fire());
        awaitFx(() -> scene.getRoot().lookup("#results-undo-all") != null);
        glossary.willAnswer(Result.ok(ANNE));
        glossary.willAnswer(Result.ok(BORIS));

        onFx(() -> button("results-undo-all").fire());

        awaitFx(() -> button("results-undo-all").isDisabled());
        awaitFx(() -> genderLabels().stream()
                        .filter(label -> label.contains("(suggested)"))
                        .count()
                == 2);
        assertThat(button(CONFIRM).isVisible()).isTrue();
    }

    @Test
    void press_whileAModelActionRuns_isNotPossible() throws Exception {
        showGlossary(ANNE);
        glossary.holdModelCalls();
        onFx(() -> button("names-style-scan").fire());
        awaitFx(() -> ((Button) required("names-style-stop")).isVisible());

        assertThat(button(CONFIRM).isDisabled()).isTrue();

        glossary.releaseModelCalls();
    }

    // IF the Ukrainian bundle lacked the button, tip or results words, THEN a Ukrainian person could not confirm or
    // undo.
    @Test
    void pressAndUndo_ukrainianBundle_wordsTheButtonAndRestoresTheMarks() throws Exception {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        showGlossary(ANNE, BORIS, CARL);
        assertThat(button(CONFIRM).getText()).isEqualTo("Підтвердити припущені статі");
        assertThat(TooltipProbe.tipText(required(CONFIRM))).startsWith("Робить вашою кожну припущену стать");
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));
        onFx(() -> button(CONFIRM).fire());
        awaitFx(() -> scene.getRoot().lookup("#results-undo-all") != null);
        assertThat(button("results-undo-all").getText()).isEqualTo("Скасувати все");
        glossary.willAnswer(Result.ok(ANNE));
        glossary.willAnswer(Result.ok(BORIS));

        onFx(() -> button("results-undo-all").fire());

        awaitFx(() -> genderLabels().stream()
                        .filter(label -> label.contains("(припущення)"))
                        .count()
                == 2);
    }
}
