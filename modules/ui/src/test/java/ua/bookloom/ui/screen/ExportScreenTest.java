package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingDestinationChooser;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.RunState;

/**
 * The export screen as the place the book is written: where it goes, what is written beside it, the action, and the
 * result it reports afterwards. Nothing here may write except the one Export book action.
 */
class ExportScreenTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void showExport() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
    }

    private void openBookAndShowExport(final Path source, final ua.bookloom.api.pipeline.ImportedBook book)
            throws TimeoutException {
        projects.on(source, Result.ok(book));
        openImport();
        openBook(source);
        chooseTarget();
        showExport();
        awaitFx(() ->
                injector.getInstance(ExportViewModel.class).exportAvailable().get());
    }

    private void openEpubAndShowExport() throws TimeoutException {
        openBookAndShowExport(EPUB, BookFixtures.frankensteinImport());
    }

    private ScriptedExportService exportService() {
        return (ScriptedExportService) injector.getInstance(ExportService.class);
    }

    private RecordingDestinationChooser chooser() {
        return (RecordingDestinationChooser) injector.getInstance(DestinationChooser.class);
    }

    private RecordingFileRevealer revealer() {
        return (RecordingFileRevealer) injector.getInstance(FileRevealer.class);
    }

    private void exportBook(final ExportReport report) throws TimeoutException {
        exportService().reportWith(report);
        onFx(() -> button("export-run").fire());
        awaitFx(() -> injector.getInstance(ExportViewModel.class).outcome().get() != null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static ExportReport report(final int written, final int auto, final int reviewed) {
        return new ExportReport(
                Path.of("Frankenstein.uk.epub"),
                written,
                0,
                0,
                0,
                auto,
                reviewed,
                List.of(),
                written,
                ConsistencySummary.NOT_RUN,
                0);
    }

    // IF the tiles were not filled from the report, THEN a person would not see what the written book contains.
    @Test
    void screen_afterExport_showsTheTilesAndAPassedMark() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(report(1240, 1224, 16));

        assertThat(labelText("export-written-value")).isEqualTo("1,240");
        assertThat(labelText("export-auto-value")).isEqualTo("98.7%");
        assertThat(labelText("export-reviewed-value")).isEqualTo("16");
        assertThat(labelText("export-valid-value")).isEqualTo("✓");
        assertThat(textOf("export-check-reopened")).contains("Re-opened and verified");
        assertThat(isShown("export-actions")).isTrue();
    }

    private static ExportReport reportWith(final List<Path> sideFiles, final ConsistencySummary consistency) {
        return new ExportReport(Path.of("Frankenstein.uk.epub"), 10, 0, 0, 0, 10, 0, sideFiles, 10, consistency, 0);
    }

    // IF the written side files were not listed, THEN a person ticking three could not tell what was written.
    @Test
    void screen_afterExportWithSideFiles_listsEachFileOnItsOwnLine() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(reportWith(
                List.of(Path.of("/out/Frankenstein.uk.glossary.csv"), Path.of("/out/Frankenstein.uk.report.md")),
                ConsistencySummary.NOT_RUN));

        assertThat(textOf("export-check-side-file-0")).contains("Frankenstein.uk.glossary.csv");
        assertThat(textOf("export-check-side-file-1")).contains("Frankenstein.uk.report.md");
        assertThat(optional("export-check-consistency")).isNull();
    }

    // IF a pass that changed segments said nothing, THEN the person could not tell it had done anything.
    @Test
    void screen_afterConsistencyPassThatAdjusted_countsTheSegments() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(reportWith(List.of(), new ConsistencySummary(ConsistencySummary.Status.RAN, 2, 1)));

        assertThat(textOf("export-check-consistency")).contains("Consistency pass: 3 segments adjusted");
    }

    @Test
    void screen_afterConsistencyPassThatChangedNothing_saysItRan() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(reportWith(List.of(), new ConsistencySummary(ConsistencySummary.Status.RAN, 0, 0)));

        assertThat(textOf("export-check-consistency")).contains("Consistency pass ran — nothing needed changing");
    }

    @Test
    void screen_afterConsistencyPassWithNoModel_saysTheGenderStepWasSkipped() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(reportWith(List.of(), new ConsistencySummary(ConsistencySummary.Status.RAN_WITHOUT_MODEL, 0, 0)));

        assertThat(textOf("export-check-consistency"))
                .contains("Consistency pass: the gender step was skipped — no model available");
    }

    // IF the file just written were named in the red refusal, THEN an export that went well would read as a failure
    // beside its own green checks; the screen says it in a neutral line instead.
    @Test
    void screen_afterExportOfANewFile_namesItInANeutralLineNotARefusal(@TempDir final Path dir)
            throws TimeoutException {
        openBookAndShowExport(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        exportService().writeFiles(true);
        exportService().reportWith(null);

        onFx(() -> button("export-run").fire());
        awaitFx(() ->
                !injector.getInstance(ExportViewModel.class).currentNote().get().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("export-current-note")).isTrue();
        assertThat(required("export-current-note").getStyleClass())
                .contains("hint")
                .doesNotContain("status-err");
        assertThat(isShown("export-refusal")).isFalse();
    }

    // IF the result stayed after Replace changed, THEN the tiles and Open buttons would vouch for another run.
    @Test
    void screen_replaceChangedAfterExport_clearsTheResult() throws TimeoutException {
        openEpubAndShowExport();
        exportBook(report(10, 10, 0));

        onFx(() -> ((ToggleSwitch) required("export-replace")).setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("export-written-value")).isEqualTo("—");
        assertThat(isShown("export-actions")).isFalse();
        assertThat(optional("export-check-reopened")).isNull();
    }

    // IF nothing written still showed a percentage, THEN the tile would divide by zero.
    @Test
    void screen_exportWroteNothing_showsDashesForThePercentage() throws TimeoutException {
        openEpubAndShowExport();

        exportBook(report(0, 0, 0));

        assertThat(labelText("export-auto-value")).isEqualTo("—");
    }

    // IF a Markdown book with no declared language claimed a metadata update, THEN the line would be false.
    @Test
    void screen_markdownWithNoLangKey_listsTheReopenCheckAndNoLanguageLine() throws TimeoutException {
        openBookAndShowExport(
                Path.of("Book.md"), BookFixtures.imported("p-md", BookFormat.MARKDOWN, null, "T", "A", 3));

        exportBook(report(3, 3, 0));

        assertThat(optional("export-check-reopened")).isNotNull();
        assertThat(optional("export-check-language")).isNull();
    }

    // IF a Markdown book that declares a language did not say its metadata was updated, THEN the change is hidden.
    @Test
    void screen_markdownWithLangKey_listsTheLanguageLine() throws TimeoutException {
        openBookAndShowExport(
                Path.of("Notes.md"), BookFixtures.imported("p-md", BookFormat.MARKDOWN, "en", "T", "A", 3));

        exportBook(report(3, 3, 0));

        assertThat(textOf("export-check-language")).contains("Language metadata updated (en → uk)");
    }

    // IF a TXT book listed a language line, THEN it would claim metadata the format does not have.
    @Test
    void screen_txtExport_hasNoLanguageLine() throws TimeoutException {
        openBookAndShowExport(Path.of("Letter.txt"), BookFixtures.imported("p-txt", BookFormat.TXT, "en", "T", "A", 3));

        exportBook(report(3, 3, 0));

        assertThat(optional("export-check-language")).isNull();
    }

    // IF checks and result actions showed before an export, THEN they would vouch for a file that does not exist.
    @Test
    void screen_beforeAnyExport_hasNoChecksAndDashesAndNoResultActions() throws TimeoutException {
        openEpubAndShowExport();

        assertThat(optional("export-check-reopened")).isNull();
        assertThat(optional("export-check-language")).isNull();
        assertThat(labelText("export-written-value")).isEqualTo("—");
        assertThat(labelText("export-valid-value")).isEqualTo("—");
        assertThat(isShown("export-actions")).isFalse();
        assertThat(((TextField) required("export-save-to")).getText()).isEqualTo("Frankenstein.uk.epub");
        assertThat(textOf("export-format")).contains("EPUB — same as the original");
        assertThat(labelText("export-title")).isEqualTo("Export");
    }

    // IF the no-book state offered a destination or an export, THEN there would be nothing to write.
    @Test
    void screen_noBookOpen_showsTheNoBookStateAndNextIsUnavailable() {
        showExport();

        assertThat(isShown("nobook-card")).isTrue();
        assertThat(optional("export-save-to")).isNull();
        assertThat(optional("export-run")).isNull();
        assertThat(button("export-next").isDisabled()).isTrue();
    }

    // IF the side files stayed usable while a run translates, THEN a person could choose outputs that cannot be
    // written.
    @Test
    void screen_runRunning_showsSideFilesAndTheSwitchUnavailableAndExportBlockedWithTheNote() throws TimeoutException {
        openEpubAndShowExport();

        publish(RunState.RUNNING);

        assertThat(((CheckBox) required("export-aux-glossary")).isDisabled()).isTrue();
        assertThat(((CheckBox) required("export-aux-bilingual")).isDisabled()).isTrue();
        assertThat(((CheckBox) required("export-aux-report")).isDisabled()).isTrue();
        assertThat(((ToggleSwitch) required("export-aux-consistency")).isDisabled())
                .isTrue();
        assertThat(isShown("export-aux-consistency")).isTrue();
        assertThat(button("export-run").isDisabled()).isTrue();
        assertThat(labelText("export-note")).isEqualTo("Pause the run to export");
    }

    // IF a pause did not free the choices again, THEN a paused run could never export.
    @Test
    void screen_runPaused_sideFilesAndExportAreUsable() throws TimeoutException {
        openEpubAndShowExport();
        publish(RunState.RUNNING);

        publish(RunState.PAUSED);

        assertThat(required("export-aux-glossary").isDisabled()).isFalse();
        assertThat(button("export-run").isDisabled()).isFalse();
        assertThat(isShown("export-note")).isFalse();
    }

    // IF a side-file box did not reach the viewmodel, THEN the chosen file would never be written.
    @Test
    void sideFileBox_checked_choosesTheSideFile() throws TimeoutException {
        openEpubAndShowExport();

        onFx(() -> ((CheckBox) required("export-aux-bilingual")).setSelected(true));

        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(ExportViewModel.class).sideFiles()))
                .contains(SideFile.GLOSSARY_CSV, SideFile.BILINGUAL_HTML);
    }

    // IF Browse did not hand the choice to the viewmodel, THEN the picked file would not be the destination.
    @Test
    void browse_pressed_callsChooseDestinationWithThePickedFile() throws TimeoutException {
        openEpubAndShowExport();
        chooser().answer(Path.of("/archive/out.epub"));

        onFx(() -> button("export-browse").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(chooser().initials()).containsExactly(Path.of("Frankenstein.uk.epub"));
        assertThat(((TextField) required("export-save-to")).getText()).isEqualTo("/archive/out.epub");
        assertThat(ThemeTestSupport.onFx(() -> injector.getInstance(ExportViewModel.class)
                        .destination()
                        .get()))
                .isEqualTo("/archive/out.epub");
    }

    // IF a success raised a toast beside the dialog, THEN the same news would be told twice.
    @Test
    void export_success_opensTheDialogAndRaisesNoToast() throws TimeoutException {
        openEpubAndShowExport();
        final int toastsBefore = scene.getRoot().lookupAll(".toast").size();

        exportBook(report(10, 10, 0));

        assertThat(optional("export-complete-card")).isNotNull();
        assertThat(textOf("export-complete-card")).contains("Frankenstein.uk.epub");
        assertThat(scene.getRoot().lookupAll(".toast")).hasSize(toastsBefore);
    }

    // IF a refused export showed a dialog, THEN a failure would read like a success.
    @Test
    void export_refused_showsTheMessageInPlaceAndNoDialogAndNoResultActions() throws TimeoutException {
        openEpubAndShowExport();
        exportService().failWith(AppError.of(ErrorCode.validation, "Refused", "Frankenstein.uk.epub is taken."));

        onFx(() -> button("export-run").fire());
        awaitFx(() ->
                !injector.getInstance(ExportViewModel.class).failure().get().isEmpty());

        assertThat(labelText("export-failure")).isEqualTo("Frankenstein.uk.epub is taken.");
        assertThat(optional("export-complete-card")).isNull();
        assertThat(isShown("export-actions")).isFalse();
    }

    // IF Open folder or Open book were not wired, THEN pressing them would do nothing.
    @Test
    void resultActions_pressed_revealAndOpenTheWrittenBook() throws TimeoutException {
        openEpubAndShowExport();
        exportBook(report(10, 10, 0));

        onFx(() -> button("export-reveal").fire());
        onFx(() -> button("export-open-book").fire());

        assertThat(revealer().revealed()).containsExactly(Path.of("Frankenstein.uk.epub"));
        assertThat(revealer().opened()).containsExactly(Path.of("Frankenstein.uk.epub"));
    }

    // IF the screen still said the run writes the book, THEN it would describe behaviour that no longer exists.
    @Test
    void screen_subtitle_doesNotClaimTheRunWroteTheBook() throws TimeoutException {
        openEpubAndShowExport();

        assertThat(textOf("export-subtitle")).doesNotContain("The run wrote");
    }

    // IF the replace switch did not reach the viewmodel, THEN an occupied path could never be overwritten.
    @Test
    void replaceSwitch_on_allowsReplacing() throws TimeoutException {
        openEpubAndShowExport();

        onFx(() -> ((ToggleSwitch) required("export-replace")).setSelected(true));

        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(ExportViewModel.class).overwrite().get()))
                .isTrue();
    }

    // IF the labels were not from the active catalogue, THEN a Ukrainian session would read English.
    @ParameterizedTest
    @ValueSource(strings = {"uk"})
    void screen_ukrainian_rendersUkrainianLabels(final String language) throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        openEpubAndShowExport();

        assertThat(button("export-run").getText()).isEqualTo("Експортувати книгу");
        assertThat(button("export-browse").getText()).isEqualTo("Огляд…");
    }
}
