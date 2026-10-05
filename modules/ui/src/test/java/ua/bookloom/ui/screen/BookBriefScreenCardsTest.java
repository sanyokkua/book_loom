package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.text.Text;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The cards of the book brief below Languages, read from the real scene: each draws the control the reference draws,
 * is available, shows the brief's values and writes a person's choice into it.
 */
class BookBriefScreenCardsTest extends BookBriefScreenTestBase {

    private static final List<String> CARD_IDS = List.of(
            "brief-languages-card", "brief-tone-card", "brief-policies-card", "brief-aux-card", "brief-quality-card");

    // IF a card were left drawn but disabled, THEN a person could not set what the run reads.
    @Test
    void cards_bookOpened_areAllPresentAndEnabled() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(CARD_IDS).allSatisfy(id -> {
            assertThat(isShown(id)).as(id).isTrue();
            assertThat(required(id).isDisabled()).as(id).isFalse();
        });
    }

    // IF the destination stayed on the brief, THEN the question of where to save would be asked before the run.
    @Test
    void screen_bookOpened_offersNoDestinationBrowseOrOverwrite() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(optional("brief-destination-card")).isNull();
        assertThat(optional("brief-destination")).isNull();
        assertThat(optional("brief-destination-browse")).isNull();
        assertThat(optional("brief-overwrite")).isNull();
        assertThat(String.join(" ", textsUnder(required("brief-state"))))
                .doesNotContain("Where to save")
                .doesNotContain("Browse");
    }

    // IF a review-mode control were drawn, THEN a preference decided at launch would be offered per book.
    @Test
    void screen_bookOpened_offersNoReviewModeChoice() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(String.join(" ", textsUnder(required("brief-state"))))
                .doesNotContain("Automatic")
                .doesNotContain("Assisted")
                .doesNotContain("Manual");
    }

    // IF the genre offered fewer or more than the forty, THEN the catalogue a completeness test checks would drift.
    @Test
    void genre_nothingTyped_suggestsForty() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(box("brief-tone-genre").getItems()).hasSize(40).contains("Gothic novel", "True crime");
    }

    // IF free text were replaced by a suggestion, THEN a genre no list holds could not be given.
    @Test
    void genre_freeTextTypedAndFocusLeft_isKeptAsWritten() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> {
            box("brief-tone-genre").getEditor().requestFocus();
            box("brief-tone-genre").getEditor().setText("Cosy mystery set in 1920s Kyiv");
        });
        onFx(() -> button("brief-back").requestFocus());

        assertThat(brief().genre()).isEqualTo("Cosy mystery set in 1920s Kyiv");
    }

    // IF the prompt received the shown name, THEN a Ukrainian interface would send a Ukrainian genre to it.
    @Test
    void genre_suggestionChosenUnderUkrainian_reachesTheBriefInEnglish() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-tone-genre").select("Готичний роман"));

        assertThat(brief().genre()).isEqualTo("Gothic novel");
        assertThat(box("brief-tone-genre").getEditor().getText()).isEqualTo("Готичний роман");
    }

    // IF the quality dial were a row of separate buttons, THEN two could be on at once.
    @Test
    void controls_bookOpened_areSegmentedButtonsAndToggleSwitches() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(required("brief-quality")).isInstanceOf(SegmentedButton.class);
        assertThat(selectedStates("brief-quality")).containsExactly(false, true, false);
        assertThat(List.of("brief-aux-nav", "brief-aux-alt", "brief-aux-metadata", "brief-aux-frontmatter"))
                .allSatisfy(id -> assertThat(required(id)).isInstanceOf(ToggleSwitch.class));
        assertThat(toggle("brief-aux-frontmatter").isSelected()).isFalse();
        assertThat(toggle("brief-aux-nav").isSelected()).isTrue();
    }

    // IF the choices did not reach the brief, THEN the run would ignore what the person set.
    @Test
    void choices_madeInTheControls_reachTheBrief() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> segmented("brief-tone-register").getButtons().get(2).setSelected(true));
        onFx(() -> segmented("brief-policy-names").getButtons().get(2).setSelected(true));
        onFx(() -> segmented("brief-policy-footnotes").getButtons().get(1).setSelected(true));
        onFx(() -> segmented("brief-quality").getButtons().get(2).setSelected(true));
        onFx(() -> toggle("brief-aux-frontmatter").setSelected(true));
        onFx(() -> ((Slider) required("brief-policy-balance")).setValue(20));
        onFx(() -> ((TextArea) required("brief-tone-voice")).setText("Early-19th-century English"));
        onFx(() -> ((TextField) required("brief-tone-audience")).setText("General adult readers"));

        assertThat(brief().register()).isEqualTo(Register.CASUAL);
        assertThat(brief().names()).isEqualTo(NamePolicy.KEEP_ORIGINAL);
        assertThat(brief().footnotes()).isEqualTo(FootnotePolicy.KEEP);
        assertThat(brief().dial()).isEqualTo(QualityDial.MAX);
        assertThat(brief().alsoTranslate()).isEqualTo(new AlsoTranslate(true, true, true, true));
        assertThat(brief().balance()).isEqualTo(20);
        assertThat(brief().voiceEra()).isEqualTo("Early-19th-century English");
        assertThat(brief().audience()).isEqualTo("General adult readers");
    }

    // IF the narrator choice did not reach the brief, THEN the gender check would never know who narrates.
    @Test
    void narrator_firstPersonFemaleChosen_reachesTheBriefAndEnablesTheGenderChoice() throws TimeoutException {
        openFrankensteinThenShowBrief();
        assertThat(required("brief-tone-narrator-gender").isDisabled()).isTrue();

        onFx(() -> segmented("brief-tone-narrator").getButtons().get(1).setSelected(true));
        onFx(() -> segmented("brief-tone-narrator-gender").getButtons().get(2).setSelected(true));

        assertThat(brief().narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
        assertThat(required("brief-tone-narrator-gender").isDisabled()).isFalse();
    }

    // IF the narrator controls did not follow the brief, THEN a trip to another screen would show a choice already
    // lost.
    @Test
    void narrator_changedInTheViewModel_isShownInBothChoices() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> briefModel().setNarratorPerson(NarratorPerson.FIRST));
        onFx(() -> briefModel().setNarratorGender(Gender.MALE));

        assertThat(selectedStates("brief-tone-narrator")).containsExactly(false, true, false);
        assertThat(selectedStates("brief-tone-narrator-gender")).containsExactly(false, true, false);
    }

    // IF a narrator choice had no explanation, THEN a person could not tell what the check does with it.
    @ParameterizedTest
    @ValueSource(strings = {"brief-tone-narrator", "brief-tone-narrator-gender"})
    void narrator_choices_explainThemselvesOnHover(final String id) throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(TooltipProbe.tipText(required(id))).contains("narrator");
    }

    // IF the controls did not follow the brief, THEN a trip to another screen would show choices already lost.
    @Test
    void controls_briefChangedInTheViewModel_showTheChange() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> briefModel().setRegister(Register.FORMAL_LITERARY));
        onFx(() -> briefModel().setBalance(80));
        onFx(() -> briefModel().setAlsoTranslate(new AlsoTranslate(false, true, true, false)));

        assertThat(selectedStates("brief-tone-register")).containsExactly(true, false, false);
        assertThat(((Slider) required("brief-policy-balance")).getValue()).isEqualTo(80);
        assertThat(toggle("brief-aux-nav").isSelected()).isFalse();
    }

    // IF pressing the selected segment cleared the choice, THEN a policy could be left with no value.
    @Test
    void segmented_selectedSegmentPressedAgain_keepsItsChoice() throws TimeoutException {
        openFrankensteinThenShowBrief();
        final ToggleButton neutral =
                segmented("brief-tone-register").getButtons().get(1);

        onFx(() -> neutral.setSelected(false));

        assertThat(neutral.isSelected()).isTrue();
        assertThat(brief().register()).isEqualTo(Register.NEUTRAL);
    }

    // IF the hint did not say what each position turns on, THEN the trade could not be made knowingly.
    @Test
    void qualityHint_dialMovedToMax_followsTheDial() throws TimeoutException {
        openFrankensteinThenShowBrief();
        assertThat(labelText("brief-quality-hint"))
                .isEqualTo("Balanced: chunks of up to 8 segments · AI reviewer on · backward-consistency pass off");

        onFx(() -> segmented("brief-quality").getButtons().get(2).setSelected(true));

        assertThat(labelText("brief-quality-hint"))
                .isEqualTo("Max: chunks of up to 2 segments · AI reviewer on · backward-consistency pass on");
    }

    // IF the model row named another model than the run uses, THEN the person would trust the wrong one; and
    // IF change did not open the settings, THEN the model could not be changed from here.
    @Test
    void modelRow_ollamaWithGemma_namesItAndChangeOpensTheSettings() throws TimeoutException {
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma4:26b"));
        openFrankensteinThenShowBrief();

        assertThat(labelText("brief-model")).isEqualTo("gemma4:26b · Ollama");
        assertThat(button("brief-model-change").getText()).isEqualTo("change");
        assertThat(optional("brief-model-ready")).isNull();

        onFx(() -> button("brief-model-change").fire());

        assertThat(currentView()).isEqualTo(ViewNames.SETTINGS);
    }

    // IF the model row kept its text after the model changed, THEN it would name a model the run no longer uses.
    @Test
    void modelRow_modelChosenAfterwards_followsTheSettings() throws TimeoutException {
        openFrankensteinThenShowBrief();
        assertThat(labelText("brief-model")).isEqualTo("No model chosen yet");

        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("qwen3:8b"));

        assertThat(labelText("brief-model")).isEqualTo("qwen3:8b · Ollama");
    }

    // IF a Ukrainian label were cut short, THEN a choice could not be read; the reference rendering was seen to read
    // "Транслітерув…" at this width.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "brief-policy-names",
                "brief-policy-foreign",
                "brief-tone-register",
                "brief-tone-narrator",
                "brief-tone-narrator-gender"
            })
    void segmentedLabels_underUkrainianAt1024_showWholeWithNoEllipsis(final String id) throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(1024, 768);
        openFrankensteinThenShowBrief();

        assertThat(segmented(id).getButtons())
                .allSatisfy(button ->
                        assertThat(shownTexts(button)).as(button.getText()).containsExactly(button.getText()));
    }

    private String labelText(final String id) {
        return ((Label) required(id)).getText();
    }

    private static List<String> shownTexts(final Node root) {
        return ThemeTestSupport.onFx(() -> {
            root.getScene().getRoot().applyCss();
            root.getScene().getRoot().layout();
            root.getScene().getRoot().applyCss();
            root.getScene().getRoot().layout();
            final ArrayList<String> texts = new ArrayList<>();
            collectTexts(root, texts);
            return texts;
        });
    }

    private static void collectTexts(final Node node, final List<String> texts) {
        if (node instanceof Text text) {
            texts.add(text.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectTexts(child, texts));
        }
    }
}
