package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
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

    // IF the voice note named the other person, THEN the prompt would say "first person" and "third-person limited".
    @Test
    void suggestStyle_voiceContradictsTheSetNarrator_isNotWrittenIntoTheBrief() {
        chooseModel();
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.MALE);
            return null;
        });
        final String voiceBefore = stored().voiceEra();
        assistant.answersBrief(Result.ok(new BriefSuggestion(
                "noir", Register.NEUTRAL, "third-person limited", null, NarratorPerson.THIRD, Gender.UNKNOWN)));

        suggest();

        assertThat(stored().voiceEra()).isEqualTo(voiceBefore);
        assertThat(stored().genre()).isEqualTo("noir");
        assertThat(stored().narrator().person()).isEqualTo(NarratorPerson.FIRST);
    }

    // IF an uncertain register were written as neutral, THEN the tip "left it as it was" would be false.
    @Test
    void suggestStyle_registerUncertain_keepsThePersonsRegister() {
        chooseModel();
        onFx(() -> {
            brief.setRegister(Register.CASUAL);
            return null;
        });
        assistant.answersBrief(Result.ok(new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.REGISTER, new FieldEvidence(List.of(), 1, 2)))));

        suggest();

        assertThat(stored().register()).isEqualTo(Register.CASUAL);
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

    // IF a field the model left out were cleared, THEN pressing the button would erase what the person had written.
    @Test
    void suggestStyle_modelLeavesFieldsEmpty_keepsWhatThePersonWrote() {
        chooseModel();
        onFx(() -> {
            brief.setAudience("Teenagers");
            brief.setVoiceEra("Victorian");
            brief.setGenre("Gothic novel");
            return null;
        });
        assistant.answersBrief(Result.ok(
                new BriefSuggestion(null, Register.CASUAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)));

        suggest();

        assertThat(stored().audience()).isEqualTo("Teenagers");
        assertThat(stored().voiceEra()).isEqualTo("Victorian");
        assertThat(stored().genre()).isEqualTo("Gothic novel");
        assertThat(stored().register()).isEqualTo(Register.CASUAL);
    }

    // IF the model could replace a narrator the person gave, THEN the answer at Start would be overwritten by a guess.
    @Test
    void suggestStyle_personAlreadyNamedANarrator_isNeverReplaced() {
        chooseModel();
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.MALE);
            return null;
        });
        assistant.answersBrief(Result.ok(
                new BriefSuggestion(null, Register.NEUTRAL, null, null, NarratorPerson.THIRD, Gender.UNKNOWN)));

        suggest();

        assertThat(stored().narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(stored().narrator().gender()).isEqualTo(Gender.MALE);
    }

    private static final FieldEvidence AGREED = new FieldEvidence(List.of("Hang on, man"), 2, 2);
    private static final FieldEvidence UNCERTAIN = new FieldEvidence(List.of(), 1, 2);

    private static BriefSuggestion withEvidence(final NarratorPerson narrator) {
        return new BriefSuggestion(
                "noir",
                Register.CASUAL,
                null,
                null,
                narrator,
                Gender.UNKNOWN,
                Map.of(
                        BriefField.GENRE, AGREED,
                        BriefField.REGISTER, AGREED,
                        BriefField.AUDIENCE, UNCERTAIN,
                        BriefField.NARRATOR, AGREED));
    }

    private java.util.Optional<FieldEvidence> evidence(final BriefField field) {
        return onFx(
                () -> brief.styleSuggestion().evidenceFor(field, brief.brief().get()));
    }

    // IF the evidence were dropped on the way to the screen, THEN the person could not see why a field was filled.
    @Test
    void suggestStyle_answerWithEvidence_keepsEachFieldsEvidenceForTheScreen() {
        chooseModel();
        assistant.answersBrief(Result.ok(withEvidence(NarratorPerson.FIRST)));

        suggest();

        assertThat(evidence(BriefField.REGISTER)).contains(AGREED);
        assertThat(evidence(BriefField.AUDIENCE)).contains(UNCERTAIN);
        assertThat(evidence(BriefField.NARRATOR)).contains(AGREED);
        assertThat(evidence(BriefField.VOICE)).isEmpty();
    }

    @Test
    void suggestStyle_personChangesASuggestedField_itsEvidenceIsNoLongerOffered() {
        chooseModel();
        assistant.answersBrief(Result.ok(withEvidence(NarratorPerson.FIRST)));
        suggest();

        onFx(() -> {
            brief.setRegister(Register.FORMAL_LITERARY);
            return null;
        });

        assertThat(evidence(BriefField.REGISTER)).isEmpty();
        assertThat(evidence(BriefField.GENRE)).contains(AGREED);
    }

    // IF the model's narrator evidence showed beside the person's own narrator, THEN it would vouch for their choice.
    @Test
    void suggestStyle_personAlreadyNamedANarrator_offersNoNarratorEvidence() {
        chooseModel();
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.MALE);
            return null;
        });
        assistant.answersBrief(Result.ok(withEvidence(NarratorPerson.THIRD)));

        suggest();

        assertThat(evidence(BriefField.NARRATOR)).isEmpty();
        assertThat(evidence(BriefField.GENRE)).contains(AGREED);
    }

    private static final FieldEvidence VOCATIVE = new FieldEvidence(List.of("Hang on to your ass, Jack"), 1, 2);

    private static BriefSuggestion provenGender(final Gender gender) {
        return new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.FIRST,
                gender,
                Map.of(BriefField.NARRATOR, AGREED, BriefField.NARRATOR_GENDER, VOCATIVE));
    }

    private boolean genderSuggested() {
        return onFx(() ->
                brief.styleSuggestion().isNarratorGenderSuggested(brief.brief().get()));
    }

    // IF the verified gender were discarded for a first-person narrator nobody gendered, THEN the Start question would
    // ask what the book already showed.
    @Test
    void suggestStyle_firstPersonNarratorWithNoGender_fillsTheProvenGenderAsSuggested() {
        chooseModel();
        onFx(() -> {
            brief.setNarratorPerson(NarratorPerson.FIRST);
            return null;
        });
        assistant.answersBrief(Result.ok(provenGender(Gender.MALE)));

        suggest();

        assertThat(stored().narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(stored().narrator().gender()).isEqualTo(Gender.MALE);
        assertThat(evidence(BriefField.NARRATOR_GENDER)).contains(VOCATIVE);
        assertThat(genderSuggested()).isTrue();
    }

    @Test
    void suggestStyle_personChangesTheSuggestedGender_isNoLongerSuggested() {
        chooseModel();
        onFx(() -> {
            brief.setNarratorPerson(NarratorPerson.FIRST);
            return null;
        });
        assistant.answersBrief(Result.ok(provenGender(Gender.MALE)));
        suggest();

        onFx(() -> {
            brief.setNarratorGender(Gender.FEMALE);
            return null;
        });

        assertThat(genderSuggested()).isFalse();
    }

    @Test
    void suggestStyle_personChoseTheGender_neitherOverridesNorMarksIt() {
        chooseModel();
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.FEMALE);
            return null;
        });
        assistant.answersBrief(Result.ok(provenGender(Gender.MALE)));

        suggest();

        assertThat(stored().narrator().gender()).isEqualTo(Gender.FEMALE);
        assertThat(genderSuggested()).isFalse();
    }

    @Test
    void suggestStyle_noGenderProven_leavesItUnknownAndUnmarked() {
        chooseModel();
        onFx(() -> {
            brief.setNarratorPerson(NarratorPerson.FIRST);
            return null;
        });
        assistant.answersBrief(Result.ok(provenGender(Gender.UNKNOWN)));

        suggest();

        assertThat(stored().narrator().gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(genderSuggested()).isFalse();
    }
}
