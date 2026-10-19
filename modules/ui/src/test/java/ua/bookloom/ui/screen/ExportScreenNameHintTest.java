package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.SettingsViewModel;

/** The Export screen says, under Save to, when the suggested name kept the author in the source alphabet. */
class ExportScreenNameHintTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    // IF the hint were not on screen, THEN the view model's flag would reach nobody; IF it stayed after an edit, THEN
    // it would nag about a name the person already changed.
    @Test
    void suggestName_authorKeptInLatin_showsTheHintUntilTheNameIsEdited() throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class))
                .answersFileName(new FileNameSuggestion("Франкенштейн. Mary Shelley", true));
        onFx(() -> shell.activate(ViewNames.EXPORT));

        onFx(() -> button("export-suggest-name").fire());
        awaitFx(() -> injector.getInstance(ExportViewModel.class)
                .nameSuggestion
                .authorKept()
                .get());
        final TextField saveTo = (TextField) required("export-save-to");
        final boolean caretAtEnd = saveTo.getCaretPosition() == saveTo.getText().length();
        final Node hint = required("export-author-kept");
        final boolean shown = hint.isVisible();
        final String words = ((Label) hint).getText();
        onFx(() -> ((TextField) required("export-save-to")).setText("Франкенштейн. Мері Шеллі.epub"));

        assertThat(caretAtEnd)
                .as("the suggested name, at the path's end, is what the field shows")
                .isTrue();
        assertThat(shown).isTrue();
        assertThat(words).isEqualTo("The author's name was kept in Latin — edit it if you want it translated.");
        assertThat(required("export-author-kept").isVisible()).isFalse();
    }

    private ScriptedSetupAssistant assistant() {
        return (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
    }

    private void showExportWithASuggestedName() throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        ((ScriptedReviewDesk) injector.getInstance(ReviewDesk.class))
                .willAnswerSegment(new SegmentView(
                        "aux:title",
                        "title",
                        SegmentKind.METADATA_TITLE,
                        SegmentStatus.ACCEPTED,
                        "Amber Tide",
                        "Amber Tide",
                        "Бурштин",
                        null,
                        null,
                        List.of(),
                        null,
                        SegmentPath.DRAFT,
                        false,
                        null,
                        null));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        assistant().answersFileName(new FileNameSuggestion("Бурштин. Анна Гейл", false));
        onFx(() -> shell.activate(ViewNames.EXPORT));
        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштин"));
        onFx(() -> button("export-suggest-name").fire());
        awaitFx(() -> ((TextField) required("export-save-to")).getText().contains("Бурштин. Анна Гейл"));
    }

    private void commitTitle(final String text) {
        onFx(() -> {
            final TextField field = (TextField) required("export-book-title");
            field.setText(text);
            field.fireEvent(new javafx.event.ActionEvent());
        });
    }

    // IF an edit of the title left the suggested name as it was, THEN the file would carry a title the book lost.
    @Test
    void titleEdit_afterASuggestedName_suggestsTheNameAgain() throws TimeoutException {
        showExportWithASuggestedName();
        assistant().answersFileName(new FileNameSuggestion("Янтар. Анна Гейл", false));

        commitTitle("Янтар");

        awaitFx(() -> ((TextField) required("export-save-to")).getText().contains("Янтар. Анна Гейл"));
        assertThat(assistant().asked()).hasSize(2);
    }

    // IF the edit overwrote a typed file name, THEN the person's choice would be lost.
    @Test
    void titleEdit_afterTheNameWasTyped_keepsTheTypedName() throws TimeoutException {
        showExportWithASuggestedName();
        onFx(() -> ((TextField) required("export-save-to")).setText("my book.epub"));

        commitTitle("Янтар");
        awaitFx(() -> injector.getInstance(BookTitleViewModel.class).edits().get() == 1);

        assertThat(assistant().asked()).hasSize(1);
        assertThat(((TextField) required("export-save-to")).getText()).isEqualTo("my book.epub");
    }
}
