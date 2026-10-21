package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.BriefSuggestion;
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
 * The language the model proposes for an Auto source: held for the screen to offer, never written into the brief
 * (15i.U4, "only a proposal the person may take").
 */
class BookBriefViewModelLanguageProposalTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant = new ScriptedSetupAssistant();

    private void openWith(final @Nullable String declared) {
        openBook(
                dir.resolve("book.txt"),
                declared == null
                        ? BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1)
                        : BookFixtures.withSource(
                                BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1), declared));
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
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
    }

    private void answerWith(final String languageProposal) {
        assistant.answersBrief(Result.ok(new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(),
                languageProposal)));
    }

    private void suggest() {
        onFx(() -> {
            brief.styleSuggestion().ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private @Nullable String proposal() {
        return onFx(() -> brief.styleSuggestion().languageProposal().get());
    }

    private BookBrief stored() {
        return onFx(() -> brief.brief().get());
    }

    // IF the proposal were applied by itself, THEN a wrong guess would translate from the wrong language unseen.
    @Test
    void languageProposal_autoSourceAndModelNamesEnglish_isHeldAndTheBriefKeepsAuto() {
        openWith(null);
        answerWith("en");

        suggest();

        assertThat(proposal()).isEqualTo("en");
        assertThat(stored().sourceLanguage()).isNull();
    }

    // IF a stale proposal stayed after an answer with none, THEN the screen would offer a language nobody proposed now.
    @Test
    void languageProposal_laterAnswerProposesNone_isCleared() {
        openWith(null);
        answerWith("en");
        suggest();

        assistant.answersBrief(Result.ok(
                new BriefSuggestion(null, Register.NEUTRAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)));
        suggest();

        assertThat(proposal()).isNull();
    }

    // IF a proposal were held for a book whose source is chosen, THEN "Use it" would override the person's choice.
    @Test
    void languageProposal_sourceAlreadyChosen_isNotHeld() {
        openWith("fr");
        answerWith("en");

        suggest();

        assertThat(proposal()).isNull();
        assertThat(stored().sourceLanguage()).isEqualTo("fr");
    }

    // IF nothing proposed still produced a value, THEN the screen would offer "Looks like" with no language.
    @Test
    void languageProposal_beforeAnySuggestion_isNone() {
        openWith(null);

        assertThat(proposal()).isNull();
    }

    private static HiddenWork hiddenWork(final ActivityTracker tracker) {
        return new HiddenWork(tracker, new RecordingToasts(), new Messages((LocaleProvider) () -> Locale.ENGLISH));
    }
}
