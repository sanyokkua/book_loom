package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;

/** The model-suggested style: what it writes into the brief, and what it leaves alone. */
class BookBriefViewModelSuggestStyleTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant = new ScriptedSetupAssistant();

    @BeforeEach
    void openTheBookWithAHelper() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        brief = onFx(() -> new BookBriefViewModel(
                current,
                projects,
                new DirectExecutor(),
                tag -> true,
                new SetupHelper(assistant, models, settings, new DirectExecutor(), activities)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void chooseModel() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
    }

    private void suggest() {
        onFx(() -> {
            brief.styleSuggestion().ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private BookBrief stored() {
        return onFx(() -> brief.brief().get());
    }

    // IF the model's genre were stored as typed, THEN "science fiction" would sit beside the list's "Science fiction".
    @Test
    void suggestStyle_modelAnswers_fillsTheToneFieldsAndJoinsAKnownGenreToTheList() {
        chooseModel();
        assistant.answersBrief(Result.ok(new BriefSuggestion(
                "science fiction",
                Register.CASUAL,
                "first-person, short sentences",
                "adult readers",
                NarratorPerson.FIRST,
                Gender.FEMALE)));

        suggest();

        final BookBrief result = stored();
        assertThat(result.genre()).isEqualTo("Science fiction");
        assertThat(result.register()).isEqualTo(Register.CASUAL);
        assertThat(result.voiceEra()).isEqualTo("first-person, short sentences");
        assertThat(result.audience()).isEqualTo("adult readers");
        assertThat(result.narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(result.narrator().gender()).isEqualTo(Gender.FEMALE);
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.DONE);
    }

    // IF an undecided narrator overwrote the person's choice, THEN a guess would undo what they set by hand.
    @Test
    void suggestStyle_narratorNotTold_keepsThePersonsNarrator() {
        chooseModel();
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.MALE);
            return null;
        });
        assistant.answersBrief(Result.ok(
                new BriefSuggestion(null, Register.NEUTRAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)));

        suggest();

        assertThat(stored().narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(stored().narrator().gender()).isEqualTo(Gender.MALE);
    }

    @Test
    void suggestStyle_noModelChosen_saysSoAndChangesNothing() {
        final BookBrief before = stored();

        suggest();

        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.NO_MODEL);
        assertThat(stored()).isEqualTo(before);
        assertThat(assistant.asked()).isEmpty();
    }

    @Test
    void suggestStyle_modelFails_showsItsMessageAndChangesNothing() {
        chooseModel();
        assistant.answersBrief(Result.err(AppError.of(ErrorCode.validation, "Unreadable", "It could not be read.")));
        final BookBrief before = stored();

        suggest();

        assertThat(onFx(() -> brief.styleSuggestion().outcome().get()))
                .isEqualTo(new StyleSuggestionOutcome(StyleSuggestionOutcome.Kind.FAILED, "It could not be read."));
        assertThat(stored()).isEqualTo(before);
    }
}
