package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The model-suggested file name: held as an offer until the person uses it, what it replaces in the destination, and
 * what is said when it cannot be had or the title changes. */
class ExportViewModelSuggestNameTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant =
            new ScriptedSetupAssistant().answersFileName(Result.ok("Франкенштейн. Мері Шеллі. 1818"));

    @BeforeEach
    void openTheBookWithAHelper() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        exports = onFx(() -> newExports(
                new DirectExecutor(),
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

    private void suggest() {
        onFx(() -> {
            exports.nameSuggestion.ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The answer is held as an offer in the dialog; the name changes only when the person presses "Use this name".
    private ResultOffer offer() {
        final Activity held = onFx(() -> activities.running().getFirst());
        return Objects.requireNonNull(held.progress().outcome().offer(), "the held offer");
    }

    private void use() {
        onFx(() -> {
            offer().apply().run();
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

    // IF the answer wrote the destination at once, THEN the person could not look at a name before it replaced theirs.
    @Test
    void suggestName_modelAnswers_holdsAUseThisNameOfferAndLeavesTheDestination() {
        modelChosen();
        final String before = onFx(() -> exports.destination().get());

        suggest();

        assertThat(offer().label()).isEqualTo(MessageKey.EXPORT_SUGGEST_NAME_USE);
        assertThat(offer().enabled()).isTrue();
        assertThat(onFx(() -> exports.destination().get())).isEqualTo(before);
    }

    // IF the suggestion replaced the whole path, THEN the book could be written into a folder the person never chose.
    @Test
    void suggestName_useThisName_keepsTheFolderAndTheFormatSuffix() {
        modelChosen();
        suggest();

        use();

        assertThat(onFx(() -> exports.destination().get()))
                .isEqualTo(dir.resolve("Франкенштейн. Мері Шеллі. 1818.epub").toString());
        assertThat(onFx(() -> exports.nameSuggestion.notice().get()))
                .isEqualTo("The model suggested this name. Edit it if you like.");
        assertThat(onFx(() -> exports.nameSuggestion.suggesting().get())).isFalse();
    }

    // IF Cancel applied the name, THEN "Cancel keeps the current name" would be false.
    @Test
    void suggestName_cancelledInsteadOfUsed_keepsTheCurrentName() {
        modelChosen();
        final String before = onFx(() -> exports.destination().get());
        suggest();

        onFx(() -> {
            offer().discard().run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> exports.destination().get())).isEqualTo(before);
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
        modelChosen();
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
        modelChosen();
        assistant.answersFileName(new FileNameSuggestion("Франкенштейн. Mary Shelley", true));

        suggest();
        use();
        final boolean shownAfterUse =
                onFx(() -> exports.nameSuggestion.authorKept().get());
        onFx(() -> {
            exports.editDestination(dir.resolve("Франкенштейн. Мері Шеллі.epub").toString());
            return null;
        });

        assertThat(shownAfterUse).isTrue();
        assertThat(onFx(() -> exports.nameSuggestion.authorKept().get())).isFalse();
    }

    // IF the hint showed for every suggestion, THEN it would claim a Latin author where the model translated the name.
    @Test
    void suggestName_authorTranslated_saysNothingAboutIt() {
        modelChosen();

        suggest();
        use();

        assertThat(onFx(() -> exports.nameSuggestion.authorKept().get())).isFalse();
    }

    private void titleEdited() {
        onFx(() -> {
            exports.nameSuggestion.afterTitleEdit();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF an edited title asked the model again by itself, THEN a title edit would start model work nobody asked for.
    @Test
    void afterTitleEdit_nameWasSuggestedAndUsed_asksNothingAndSaysTheTitleChanged() {
        modelChosen();
        suggest();
        use();

        titleEdited();

        assertThat(assistant.asked()).hasSize(1);
        assertThat(onFx(() -> exports.nameSuggestion.notice().get()))
                .isEqualTo("The title changed. Press ✦ Suggest a name for a file name that matches it.");
    }

    // IF an edit overwrote a name the person typed, THEN their choice would be lost to a model.
    @Test
    void afterTitleEdit_personTypedTheName_keepsItAndAsksNothing() {
        modelChosen();
        suggest();
        use();
        onFx(() -> {
            exports.editDestination(dir.resolve("my book.epub").toString());
            return null;
        });

        titleEdited();

        assertThat(assistant.asked()).hasSize(1);
        assertThat(onFx(() -> exports.destination().get()))
                .isEqualTo(dir.resolve("my book.epub").toString());
    }

    @Test
    void afterTitleEdit_noSuggestionWasAsked_asksNothing() {
        modelChosen();

        titleEdited();

        assertThat(assistant.asked()).isEmpty();
    }

    private static HiddenWork hiddenWork(final ActivityTracker tracker) {
        return new HiddenWork(tracker, new RecordingToasts(), new Messages((LocaleProvider) () -> Locale.ENGLISH));
    }
}
