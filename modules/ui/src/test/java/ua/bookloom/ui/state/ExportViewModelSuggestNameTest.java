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
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;

/** The model-suggested file name: what it replaces in the destination, and what is said when it cannot be had. */
class ExportViewModelSuggestNameTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant =
            new ScriptedSetupAssistant().answersFileName(Result.ok("Франкенштейн. Мері Шеллі. 1818"));

    @BeforeEach
    void openTheBookWithAHelper() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        exports = onFx(() -> new ExportViewModel(
                current,
                desk,
                messages,
                exportService,
                models,
                settings,
                progress,
                new DirectExecutor(),
                activities,
                new SetupHelper(assistant, models, settings, new DirectExecutor(), activities)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void suggest() {
        onFx(() -> {
            exports.nameSuggestion.ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the suggestion replaced the whole path, THEN the book could be written into a folder the person never chose.
    @Test
    void suggestName_modelAnswers_keepsTheFolderAndTheFormatSuffix() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });

        suggest();

        assertThat(onFx(() -> exports.destination().get()))
                .isEqualTo(dir.resolve("Франкенштейн. Мері Шеллі. 1818.epub").toString());
        assertThat(onFx(() -> exports.nameSuggestion.notice().get()))
                .isEqualTo("The model suggested this name. Edit it if you like.");
        assertThat(onFx(() -> exports.nameSuggestion.suggesting().get())).isFalse();
    }

    @Test
    void suggestName_noModelChosen_saysToChooseOneAndKeepsTheDestination() {
        final String before = onFx(() -> exports.destination().get());

        suggest();

        assertThat(onFx(() -> exports.nameSuggestion.notice().get()))
                .isEqualTo("Choose a provider and model in Settings first.");
        assertThat(onFx(() -> exports.destination().get())).isEqualTo(before);
    }

    @Test
    void suggestName_modelFails_showsItsMessageAndKeepsTheDestination() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        assistant.answersFileName(
                Result.err(AppError.of(ErrorCode.validation, "No name", "The model did not suggest a usable name.")));
        final String before = onFx(() -> exports.destination().get());

        suggest();

        assertThat(onFx(() -> exports.nameSuggestion.notice().get()))
                .isEqualTo("The model did not suggest a usable name.");
        assertThat(onFx(() -> exports.destination().get())).isEqualTo(before);
    }

    @Test
    void canSuggestName_withNoSetupHelper_isFalse() {
        final ExportViewModel plain = onFx(() -> newExports(new DirectExecutor()));

        assertThat(onFx(plain.nameSuggestion::isOffered)).isFalse();
        assertThat(onFx(() -> exports.nameSuggestion.isOffered())).isTrue();
    }

    // IF the person were not told the author stayed in Latin, THEN a Ukrainian file name would carry "Mary Shelley"
    // unnoticed; the hint goes as soon as the person edits the name, since it is then theirs.
    @Test
    void suggestName_authorKeptInSourceScript_saysSoUntilThePersonEdits() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        assistant.answersFileName(new FileNameSuggestion("Франкенштейн. Mary Shelley", true));

        suggest();
        final boolean shownAfterSuggestion =
                onFx(() -> exports.nameSuggestion.authorKept().get());
        onFx(() -> {
            exports.editDestination(dir.resolve("Франкенштейн. Мері Шеллі.epub").toString());
            return null;
        });

        assertThat(shownAfterSuggestion).isTrue();
        assertThat(onFx(() -> exports.nameSuggestion.authorKept().get())).isFalse();
    }

    // IF the hint showed for every suggestion, THEN it would claim a Latin author where the model translated the name.
    @Test
    void suggestName_authorTranslated_saysNothingAboutIt() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });

        suggest();

        assertThat(onFx(() -> exports.nameSuggestion.authorKept().get())).isFalse();
    }

    private void titleEdited() {
        onFx(() -> {
            exports.nameSuggestion.afterTitleEdit();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void modelChosen() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
    }

    // IF an edited title left the old suggested name, THEN the file would carry a title the book no longer has.
    @Test
    void afterTitleEdit_nameWasSuggested_suggestsAgain() {
        modelChosen();
        suggest();
        assistant.answersFileName(Result.ok("Бурштиновий Приплив. Анна Гейл"));

        titleEdited();

        assertThat(assistant.asked()).hasSize(2);
        assertThat(onFx(() -> exports.destination().get()))
                .isEqualTo(dir.resolve("Бурштиновий Приплив. Анна Гейл.epub").toString());
    }

    // IF an edit overwrote a name the person typed, THEN their choice would be lost to a model.
    @Test
    void afterTitleEdit_personTypedTheName_keepsItAndAsksNothing() {
        modelChosen();
        suggest();
        onFx(() -> {
            exports.editDestination(dir.resolve("my book.epub").toString());
            return null;
        });

        titleEdited();

        assertThat(assistant.asked()).hasSize(1);
        assertThat(onFx(() -> exports.destination().get()))
                .isEqualTo(dir.resolve("my book.epub").toString());
    }

    // IF an edit asked the model before any suggestion was wanted, THEN a title edit would start model work unasked.
    @Test
    void afterTitleEdit_noSuggestionWasAsked_asksNothing() {
        modelChosen();

        titleEdited();

        assertThat(assistant.asked()).isEmpty();
    }
}
