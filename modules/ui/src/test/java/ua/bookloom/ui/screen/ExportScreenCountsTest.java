package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ExportViewModel;

/** The figures and lines the Export screen shows after a write say what happened, not a flattering approximation. */
class ExportScreenCountsTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void exportBook(final ExportReport report) throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
        awaitFx(() ->
                injector.getInstance(ExportViewModel.class).exportAvailable().get());
        ((ScriptedExportService) injector.getInstance(ExportService.class)).reportWith(report);
        onFx(() -> button("export-run").fire());
        awaitFx(() -> injector.getInstance(ExportViewModel.class).outcome().get() != null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The log showed 142 gender deferrals left open and the dialog said nothing needed changing.
    @Test
    void screen_afterConsistencyPassThatChangedNothingWithOpenGenders_namesTheWaitingSegments()
            throws TimeoutException {
        exportBook(new ExportReport(
                Path.of("Frankenstein.uk.epub"),
                10,
                0,
                0,
                0,
                10,
                0,
                List.of(),
                10,
                new ConsistencySummary(ConsistencySummary.Status.RAN, 0, 0, Map.of(DeferralReason.GENDER_UNKNOWN, 142)),
                0));

        assertThat(textOf("export-check-consistency"))
                .contains("142 segments await the gender of a character; set it in Names & style")
                .doesNotContain("nothing needed changing");
    }

    // The run's 84.2 % counted the 72 lines kept as they are, which no model decided, in the denominator.
    @Test
    void screen_afterExportWithKeptVerbatimAndPending_sharesAutoAcceptedOverTheSegmentsThatNeededADecision()
            throws TimeoutException {
        exportBook(new ExportReport(
                Path.of("Frankenstein.uk.epub"), 100, 10, 0, 5, 60, 0, List.of(), 100, ConsistencySummary.NOT_RUN, 20));

        assertThat(labelText("export-auto-value")).isEqualTo("66.7%");
    }
}
