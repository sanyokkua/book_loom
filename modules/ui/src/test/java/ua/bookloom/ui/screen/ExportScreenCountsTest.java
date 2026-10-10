package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ExportViewModel;

/** The figures and lines the Export screen shows after a write say what happened, not a flattering approximation. */
class ExportScreenCountsTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");
    private static final int CONSISTENCY_LINES = 6;

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

    // The log showed 142 gender deferrals left open and the dialog said nothing needed changing; the warning now says
    // who the segments wait on.
    @Test
    void screen_afterConsistencyPassWithOpenGenders_warnsWhichCharactersTheSegmentsWaitOn() throws TimeoutException {
        exportBook(reportWith(new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(DeferralReason.GENDER_UNKNOWN, 142),
                0,
                ConsistencyChecks.NONE,
                List.of(new GenderWait("Chrome", 120), new GenderWait("Finn", 30)))));

        assertThat(labelText("export-check-awaiting-gender"))
                .isEqualTo("142 segments wait on a character's gender: Chrome ×120, Finn ×30. Set it in Names & style;"
                        + " the next export re-renders them.");
    }

    // The warning is only useful if the person can get to the place that fixes it.
    @Test
    void awaitingGenderLink_pressed_opensNamesAndStyle() throws TimeoutException {
        exportBook(reportWith(new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(DeferralReason.GENDER_UNKNOWN, 1),
                0,
                ConsistencyChecks.NONE,
                List.of(new GenderWait("Chrome", 1)))));

        onFx(() -> button("export-awaiting-gender-open").fire());

        assertThat(ua.bookloom.ui.ThemeTestSupport.onFx(
                        () -> navigator.currentView().get()))
                .isEqualTo(ViewNames.NAMES_STYLE);
    }

    @Test
    void screen_afterConsistencyPassWithNoOpenGenders_showsNoWarningAndNoLink() throws TimeoutException {
        exportBook(reportWith(new ConsistencySummary(ConsistencySummary.Status.RAN, 1, 0)));

        assertThat(scene.getRoot().lookup("#export-check-awaiting-gender")).isNull();
        assertThat(scene.getRoot().lookup("#export-awaiting-gender-open")).isNull();
    }

    // IF the pass reported only its fixes, THEN a pass that drafted 6 segments again and read 30 paragraphs would read
    // like one that did nothing.
    @Test
    void screen_afterConsistencyPassWithRetryAndChecks_saysWhatEachStepCameTo() throws TimeoutException {
        exportBook(reportWith(new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(),
                1,
                new ConsistencyChecks(4, 2, 27, Map.of("quotes", 2, "worse", 1), 1))));

        assertThat(consistencyLines())
                .contains(
                        "Consistency pass: 5 segments adjusted",
                        "a fresh draft improved 4 flagged or doubtful segments; 2 were no better and stayed as before",
                        "27 paragraphs checked against their neighbours needed no change",
                        "3 answers were refused because they would make the text worse (quote marks not paired: 2, scored worse: 1)",
                        "1 segment was skipped — the model call failed")
                .doesNotContain("nothing needed changing");
    }

    private String consistencyLines() {
        return IntStream.range(0, CONSISTENCY_LINES)
                .mapToObj(index -> textOf("export-check-consistency" + (index == 0 ? "" : "-" + index)))
                .collect(Collectors.joining("\n"));
    }

    private static ExportReport reportWith(final ConsistencySummary summary) {
        return new ExportReport(Path.of("Frankenstein.uk.epub"), 10, 0, 0, 0, 10, 0, List.of(), 10, summary, 0);
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
