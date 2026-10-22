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
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/**
 * The model's brief suggestion is held until the person applies it: an answer writes nothing, Apply writes it once
 * through the brief's own save path, and a stopped, failed or discarded suggestion leaves the brief as it was.
 */
class StyleSuggestionApplyTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant = new ScriptedSetupAssistant();

    private static final BriefSuggestion SCIENCE_FICTION = new BriefSuggestion(
            "science fiction",
            Register.FORMAL_LITERARY,
            "terse and wry",
            "adult readers",
            NarratorPerson.THIRD,
            Gender.UNKNOWN,
            Map.of(
                    BriefField.REGISTER,
                    new FieldEvidence(FieldEvidence.Basis.PASSAGES, List.of("He left at dawn."), 3, 4)),
            "en");

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
                        new HiddenWork(activities, new RecordingToasts(), new Messages((LocaleProvider)
                                () -> Locale.ENGLISH)))));
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void ask() {
        onFx(() -> {
            brief.styleSuggestion().ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private BookBrief stored() {
        return onFx(() -> brief.brief().get());
    }

    private PendingSuggestion pending() {
        return onFx(() -> brief.styleSuggestion().pending().get());
    }

    private void applyHeld() {
        onFx(() -> {
            brief.styleSuggestion().apply();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void discardHeld() {
        onFx(() -> {
            brief.styleSuggestion().discard();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the answer wrote the brief at once, THEN closing the dialog would still have changed what the person never
    // saw.
    @Test
    void ask_answerArrives_isHeldAndWritesNothing() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        final BookBrief before = stored();
        final int savesBefore = projects.briefs().size();

        ask();

        assertThat(pending()).isNotNull();
        assertThat(pending().suggestion()).isEqualTo(SCIENCE_FICTION);
        assertThat(stored()).isEqualTo(before);
        assertThat(projects.briefs()).hasSize(savesBefore);
    }

    // IF Apply wrote through any other path, THEN the saved brief and the one on screen could differ.
    @Test
    void apply_answerHeld_writesTheFieldsOnceThroughTheBriefsSave() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        ask();
        final int savesBefore = projects.briefs().size();

        applyHeld();

        final BookBrief written = stored();
        assertThat(written.genre()).isEqualTo("Science fiction");
        assertThat(written.register()).isEqualTo(Register.FORMAL_LITERARY);
        assertThat(written.voiceEra()).isEqualTo("terse and wry");
        assertThat(written.audience()).isEqualTo("adult readers");
        assertThat(written.narrator().person()).isEqualTo(NarratorPerson.THIRD);
        assertThat(projects.briefs()).hasSize(savesBefore + 1);
        assertThat(projects.briefs().getLast()).isEqualTo(written);
        assertThat(pending()).isNull();
    }

    // IF the answer were merged against the brief as it was when asked, THEN a field edited meanwhile would be undone.
    @Test
    void apply_personEditedAnotherFieldWhileAsking_keepsTheEditAndTheSuggestion() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        ask();
        onFx(() -> {
            brief.setBalance(2);
            return null;
        });

        applyHeld();

        assertThat(stored().balance()).isEqualTo(2);
        assertThat(stored().genre()).isEqualTo("Science fiction");
    }

    // IF the evidence and the proposal were kept before Apply, THEN a discarded answer would still decorate the fields.
    @Test
    void ask_answerHeldNotApplied_keepsNoEvidenceAndNoLanguageProposal() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));

        ask();

        assertThat(onFx(() -> brief.styleSuggestion().evidence().get())).isEmpty();
        assertThat(onFx(() -> brief.styleSuggestion().languageProposal().get())).isNull();
    }

    @Test
    void apply_answerWithEvidence_keepsTheEvidenceOfTheWrittenField() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        ask();

        applyHeld();

        assertThat(onFx(() -> brief.styleSuggestion().evidenceFor(BriefField.REGISTER, stored())))
                .isPresent();
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.DONE);
    }

    // IF Discard still wrote, THEN pressing Cancel on the dialog would not leave the brief unchanged.
    @Test
    void discard_answerHeld_leavesTheBriefAndNoSaveBehind() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        ask();
        final BookBrief before = stored();
        final int savesBefore = projects.briefs().size();

        discardHeld();

        assertThat(pending()).isNull();
        assertThat(stored()).isEqualTo(before);
        assertThat(projects.briefs()).hasSize(savesBefore);
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.NONE);
    }

    // A failed suggestion offers no Apply.
    @Test
    void ask_modelFails_holdsNothingToApply() {
        assistant.answersBrief(Result.err(AppError.of(ErrorCode.validation, "Unreadable", "It could not be read.")));

        ask();

        assertThat(pending()).isNull();
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.FAILED);
    }

    // A person who stopped the suggestion keeps the brief and nothing is held to apply.
    @Test
    void ask_personStopsTheSuggestion_holdsNothingAndKeepsTheBrief() {
        assistant.answersBrief(Result.err(AppError.of(ErrorCode.cancelled, "Stopped", "It was stopped.")));

        ask();

        assertThat(pending()).isNull();
        assertThat(stored().genre()).isEqualTo(BookBrief.defaults("en").genre());
    }

    // IF a second Ask replaced the held answer silently, THEN the first answer would be applied unseen.
    @Test
    void ask_bookChangesWhileAnswerHeld_dropsTheHeldAnswer() {
        assistant.answersBrief(Result.ok(SCIENCE_FICTION));
        ask();

        openBook(dir.resolve("Other.epub"), BookFixtures.frankensteinImport());

        assertThat(pending()).isNull();
    }
}
