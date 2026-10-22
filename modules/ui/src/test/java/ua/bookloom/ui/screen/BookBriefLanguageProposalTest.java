package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StyleSuggestionOutcome;

/**
 * The language the model proposes for an Auto source, as the Languages card shows it: "Looks like English" with a
 * "Use it" button, only while the source is Auto, and never applied without the click (15i.U4).
 */
class BookBriefLanguageProposalTest extends BookBriefScreenTestBase {

    private void openAutoBook() throws TimeoutException {
        openBookThenShowBrief(Path.of("Diary.txt"), BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1));
    }

    private void suggestWithProposal(final String tag) throws TimeoutException {
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class))
                .answersBrief(Result.ok(new BriefSuggestion(
                        null,
                        Register.NEUTRAL,
                        null,
                        null,
                        NarratorPerson.UNSPECIFIED,
                        Gender.UNKNOWN,
                        Map.of(),
                        tag)));
        fillInAndApply();
        awaitFx(() -> briefModel().styleSuggestion().outcome().get().kind() == StyleSuggestionOutcome.Kind.DONE);
    }

    // IF a proposal showed before the model had read the book, THEN the screen would claim a language from nowhere.
    @Test
    void proposal_nothingAskedYet_isNotShown() throws TimeoutException {
        openAutoBook();

        assertThat(isShown("brief-source-proposal")).isFalse();
    }

    // IF the proposal were applied by itself, THEN a wrong guess would translate from the wrong language unseen.
    @Test
    void proposal_modelNamesEnglishForAnAutoSource_isOfferedAndNotApplied() throws TimeoutException {
        openAutoBook();

        suggestWithProposal("en");

        assertThat(isShown("brief-source-proposal")).isTrue();
        assertThat(((Label) required("brief-source-proposal-text")).getText()).isEqualTo("Looks like English");
        assertThat(button("brief-source-use").getText()).isEqualTo("Use it");
        assertThat(brief().sourceLanguage()).isNull();
        assertThat(box("brief-source").getEditor().getText()).isEqualTo("Auto — the model identifies it");
    }

    // IF "Use it" did not set the source, THEN the proposal would be a label the person had to retype.
    @Test
    void useIt_pressed_setsTheSourceToTheProposedLanguageAndRetiresTheProposalAndTheAutoNote() throws TimeoutException {
        openAutoBook();
        suggestWithProposal("en");

        onFx(() -> button("brief-source-use").fire());

        assertThat(brief().sourceLanguage()).isEqualTo("en");
        assertThat(box("brief-source").getEditor().getText()).isEqualTo("English");
        assertThat(isShown("brief-source-proposal")).isFalse();
        assertThat(isShown("brief-source-undeclared")).isFalse();
    }

    // IF a source the person typed stayed beside a proposal for another, THEN "Use it" would override their choice.
    @Test
    void proposal_personChoosesAnotherSource_isHiddenAndTheirChoiceStands() throws TimeoutException {
        openAutoBook();
        suggestWithProposal("en");

        onFx(() -> box("brief-source").select("fr"));

        assertThat(isShown("brief-source-proposal")).isFalse();
        assertThat(brief().sourceLanguage()).isEqualTo("fr");
    }

    // IF a book that declares its language were offered a proposal, THEN the model would second-guess the file.
    @Test
    void proposal_bookDeclaringItsSource_isNeverShown() throws TimeoutException {
        openFrankensteinThenShowBrief();

        suggestWithProposal("fr");

        assertThat(isShown("brief-source-proposal")).isFalse();
        assertThat(brief().sourceLanguage()).isEqualTo("en");
    }

    // IF an answer that proposes no language still showed the row, THEN "Looks like" would name nothing.
    @Test
    void proposal_modelAnswersWithNoLanguage_isNotShown() throws TimeoutException {
        openAutoBook();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class))
                .answersBrief(Result.ok(new BriefSuggestion(
                        null, Register.NEUTRAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)));

        fillInAndApply();
        awaitFx(() -> isShown("brief-suggest-outcome"));

        assertThat(isShown("brief-source-proposal")).isFalse();
    }

    // IF the button did not explain itself, THEN nobody would know it changes the source and nothing else.
    @Test
    void useIt_proposalShown_explainsItselfOnHover() throws TimeoutException {
        openAutoBook();
        suggestWithProposal("en");

        assertThat(TooltipProbe.tipText(required("brief-source-use"))).contains("source language");
    }

    // IF the proposal were English-only, THEN a Ukrainian session would read "Looks like" over a Ukrainian list.
    @Test
    void proposal_underUkrainian_isWordedInUkrainianWithTheLanguageNamedThere() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openAutoBook();

        suggestWithProposal("en");

        assertThat(((Label) required("brief-source-proposal-text")).getText())
                .matches(".*\\p{IsCyrillic}.*")
                .doesNotContain("Looks like")
                .doesNotContain("English");
        assertThat(button("brief-source-use").getText()).matches(".*\\p{IsCyrillic}.*");
    }
}
