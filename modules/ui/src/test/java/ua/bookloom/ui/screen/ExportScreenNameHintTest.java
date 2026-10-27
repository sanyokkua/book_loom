package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.RecordingFileChooser;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * "✦ Suggest a name" is a short dialog whose answer is used only on "Use this name"; Cancel keeps the current name; the
 * author hint and the title-changed notice sit under Save as, and a title edit asks the model nothing.
 */
class ExportScreenNameHintTest extends ExportScreenTestBase {

    @TempDir
    private Path dir;

    private Path suggested;

    @BeforeEach
    void showExportWithAModelAndATitle() throws TimeoutException {
        suggested = dir.resolve("Бурштин. Mary Shelley.epub");
        desk.willAnswerSegment(new SegmentView(
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
        assistant().answersFileName(new FileNameSuggestion("Бурштин. Mary Shelley", true));
        openAndShowExport(dir.resolve("Frankenstein.epub"));
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштин"));
    }

    private ScriptedSetupAssistant assistant() {
        return (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
    }

    private void suggest() throws TimeoutException {
        onFx(() -> button("export-suggest-name").fire());
        awaitFx(() ->
                optional("progress-apply") != null && !button("progress-apply").isDisabled());
    }

    private String areaText(final String id) {
        return ua.bookloom.ui.ThemeTestSupport.onFx(() -> ((javafx.scene.control.TextArea) required(id)).getText());
    }

    private String destinationPath() {
        return TooltipProbe.tipText(required("export-save-to"));
    }

    // IF the dialog showed only raw model calls, THEN the person could not see what was asked or how the name was made.
    @Test
    void suggest_answerArrives_showsOneStepWithWhatWasSentAndTheName() throws TimeoutException {
        suggest();
        awaitFx(() -> areaText("progress-input").contains("Into"));

        assertThat(labelText("progress-title")).isEqualTo("Suggest a file name");
        assertThat(labelText("progress-purpose")).contains("made safe for a file name");
        assertThat(areaText("progress-input")).contains("Into: Ukrainian").contains("format EPUB");
        assertThat(areaText("progress-output")).contains("Бурштин. Mary Shelley");
    }

    // IF the answer wrote the name at once, THEN the person could not look at it before it replaced theirs.
    @Test
    void suggest_answerArrives_offersUseThisNameAndLeavesTheCurrentName() throws TimeoutException {
        final String before = destinationPath();

        suggest();

        assertThat(button("progress-apply").getText()).isEqualTo("Use this name");
        assertThat(((Label) required("progress-footnote")).getText()).isEqualTo("Cancel keeps the current name.");
        assertThat(destinationPath()).isEqualTo(before);
    }

    @Test
    void suggest_useThisNamePressed_putsTheNameInSaveAsBesideTheSameFolder() throws TimeoutException {
        suggest();

        onFx(() -> ((Button) required("progress-apply")).fire());

        awaitFx(() -> suggested.toString().equals(destinationPath()));
        assertThat(labelText("export-save-to")).endsWith("Бурштин. Mary Shelley.epub");
    }

    // IF Cancel used the name, THEN "Cancel keeps the current name" would be a lie.
    @Test
    void suggest_cancelPressed_keepsTheCurrentName() throws TimeoutException {
        final String before = destinationPath();
        suggest();

        onFx(() -> ((Button) required("progress-cancel")).fire());
        awaitFx(() -> optional("progress-apply") == null);

        assertThat(destinationPath()).isEqualTo(before);
    }

    // IF the hint were not on screen, THEN the view model's flag would reach nobody; IF it stayed after the person
    // chose another name, THEN it would nag about a name they already changed.
    @Test
    void suggest_authorKeptInLatin_showsTheHintUntilAnotherNameIsChosen() throws TimeoutException {
        suggest();
        onFx(() -> ((Button) required("progress-apply")).fire());
        awaitFx(() -> isShown("export-author-kept"));

        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class)).answer(dir.resolve("mine.epub"));
        onFx(() -> button("export-browse").fire());
        awaitFx(() -> !isShown("export-author-kept"));

        assertThat(destinationPath()).isEqualTo(dir.resolve("mine.epub").toString());
    }

    // IF an edit of the title asked the model again by itself, THEN a title edit would start model work unasked.
    @Test
    void titleEdit_afterAUsedName_asksNothingAndSaysTheTitleChanged() throws TimeoutException {
        suggest();
        onFx(() -> ((Button) required("progress-apply")).fire());
        awaitFx(() -> suggested.toString().equals(destinationPath()));

        onFx(() -> {
            final TextField field = (TextField) required("export-book-title");
            field.setText("Янтар");
            field.fireEvent(new javafx.event.ActionEvent());
        });
        awaitFx(() -> isShown("export-name-notice")
                && ((Label) required("export-name-notice")).getText().startsWith("The title changed."));

        assertThat(((Label) required("export-name-notice")).getText())
                .isEqualTo("The title changed. Press ✦ Suggest a name for a file name that matches it.");
        assertThat(assistant().asked()).hasSize(1);
    }
}
