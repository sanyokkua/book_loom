package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.text.Text;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ExportViewModel;

/**
 * The Export screen's words after the real-run check: a partial book is "the book so far", the narrow column's long
 * side-file label wraps instead of being cut, and the consistency switch names what it does beside itself.
 */
class ExportPolishScreenTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void openEpubAndShowExport() throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
        awaitFx(() ->
                injector.getInstance(ExportViewModel.class).exportAvailable().get());
    }

    private void exportBook(final int pending) throws TimeoutException {
        ((ScriptedExportService) injector.getInstance(ExportService.class))
                .reportWith(new ExportReport(
                        Path.of("Frankenstein.uk.epub"),
                        10,
                        pending,
                        0,
                        0,
                        10 - pending,
                        0,
                        List.of(),
                        10,
                        ConsistencySummary.NOT_RUN,
                        0));
        onFx(() -> button("export-run").fire());
        awaitFx(() -> injector.getInstance(ExportViewModel.class).outcome().get() != null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a book written with segments still untranslated were announced as ready, THEN the person would think it done.
    @ParameterizedTest
    @CsvSource({"0, Translated book ready", "4, Your book so far"})
    void title_afterExport_saysReadyOnlyWhenNothingIsPending(final int pending, final String expected)
            throws TimeoutException {
        openEpubAndShowExport();

        exportBook(pending);

        assertThat(labelText("export-title")).isEqualTo(expected);
    }

    // IF the bilingual copy's label were cut with an ellipsis, THEN the person could not read what it writes.
    @Test
    void bilingualBox_inTheNarrowColumn_wrapsItsWholeLabel() throws TimeoutException {
        openEpubAndShowExport();

        final CheckBox box = (CheckBox) required("export-aux-bilingual");
        final List<String> shown = ThemeTestSupport.onFx(() -> box.lookupAll(".text").stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .toList());

        assertThat(box.isWrapText()).isTrue();
        assertThat(String.join("", shown).replace("\n", " ").strip())
                .isEqualTo("Bilingual copy (source and translation side by side)");
    }

    // IF the switch stood alone under its card's heading, THEN it would not say what turning it on does.
    @Test
    void consistencySwitch_shown_namesItselfBesideTheSwitch() throws TimeoutException {
        openEpubAndShowExport();

        final Node node = required("export-aux-consistency");

        assertThat(((ToggleSwitch) node).getText()).isEqualTo("Run before writing");
    }
}
