package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** What a stopped suggestion and the person's narrator edits do to the narrator's gender origin (15i.B4). */
class BookBriefViewModelNarratorOriginTest extends ExportViewModelTestBase {

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
                new SetupHelper(
                        assistant,
                        models,
                        settings,
                        new DirectExecutor(),
                        activities,
                        capabilities,
                        hiddenWork(activities))));
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
        SuggestionApplies.applyPending(brief);
    }

    private BookBrief stored() {
        return onFx(() -> brief.brief().get());
    }

    private static final FieldEvidence VOCATIVE =
            new FieldEvidence(FieldEvidence.Basis.PASSAGES, List.of("Hold on tight, Ray"), 1, 2);

    private static BriefSuggestion provenGender(final Gender gender) {
        return new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.FIRST,
                gender,
                Map.of(BriefField.NARRATOR_GENDER, VOCATIVE));
    }

    // IF a stop were shown as a failure or applied as an answer, THEN pressing Stop would change the brief or put an
    // error banner over a book the person chose to stop reading.
    @Test
    void suggestStyle_stoppedByThePerson_changesNothingAndSaysItWasStopped() {
        chooseModel();
        assistant.answersBrief(Result.err(AppError.of(ErrorCode.cancelled, "Stopped", "The call was stopped.")));
        final BookBrief before = stored();

        suggest();

        assertThat(stored()).isEqualTo(before);
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.STOPPED);
        assertThat(onFx(() -> brief.styleSuggestion().suggesting().get())).isFalse();
    }

    // The gender's quotes are about the narrator's gender, not the person: changing the person keeps them, and the
    // person's own pick of a gender replaces them with the person's origin.
    @Test
    void narrator_verifiedGenderThenThePersonChangesPersonAndGender_keepsTheQuotesOnlyForThePerson() {
        chooseModel();
        onFx(() -> {
            brief.setNarratorPerson(NarratorPerson.FIRST);
            return null;
        });
        assistant.answersBrief(Result.ok(provenGender(Gender.MALE)));
        suggest();
        assertThat(stored().narrator().genderOrigin()).isEqualTo(Narrator.GenderOrigin.VERIFIED);

        onFx(() -> {
            brief.setNarratorPerson(NarratorPerson.THIRD);
            return null;
        });
        assertThat(stored().narrator().genderOrigin()).isEqualTo(Narrator.GenderOrigin.VERIFIED);
        assertThat(stored().narrator().evidence()).containsExactly("Hold on tight, Ray");

        onFx(() -> {
            brief.setNarratorGender(Gender.FEMALE);
            return null;
        });
        assertThat(stored().narrator().genderOrigin()).isEqualTo(Narrator.GenderOrigin.PERSON);
        assertThat(stored().narrator().evidence()).isEmpty();
    }

    // IF "not stated" were the person's origin, THEN a later step would believe the person had decided.
    @Test
    void narrator_personLeavesTheGenderUnstated_hasNoOrigin() {
        onFx(() -> {
            brief.setFirstPersonNarrator(Gender.UNKNOWN);
            return null;
        });

        assertThat(stored().narrator().genderOrigin()).isEqualTo(Narrator.GenderOrigin.NONE);
    }

    private static HiddenWork hiddenWork(final ActivityTracker tracker) {
        return new HiddenWork(tracker, new RecordingToasts(), new Messages((LocaleProvider) () -> Locale.ENGLISH));
    }
}
